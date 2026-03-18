package com.dris.v2.backend.vector;

import com.dris.sdk.next.domain.IdentifierMetadata;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

public class JdbcVectorIndexGateway implements VectorIndexGateway {
    private static final int DEFAULT_DIMENSIONS = 128;

    private final DataSource dataSource;
    private final EmbeddingProvider embeddingProvider;
    private final VectorMetadataDocumentBuilder documentBuilder;

    public JdbcVectorIndexGateway(DataSource dataSource) {
        this(dataSource, new HashTextEmbeddingProvider(DEFAULT_DIMENSIONS));
    }

    public JdbcVectorIndexGateway(DataSource dataSource, int dimensions) {
        this(dataSource, new HashTextEmbeddingProvider(dimensions));
    }

    public JdbcVectorIndexGateway(DataSource dataSource, EmbeddingProvider embeddingProvider) {
        this.dataSource = dataSource;
        this.embeddingProvider = embeddingProvider == null
                ? new HashTextEmbeddingProvider(DEFAULT_DIMENSIONS)
                : embeddingProvider;
        this.documentBuilder = new VectorMetadataDocumentBuilder();
    }

    @Override
    public boolean upsertBatch(List<IdentifierMetadata> metadataList) {
        if (metadataList == null || metadataList.isEmpty()) {
            return true;
        }
        List<VectorRecord> records = prepareRecords(metadataList);
        if (records.isEmpty()) {
            return true;
        }
        List<double[]> vectors = embeddingProvider.embedTexts(extractTexts(records));
        if (vectors == null || vectors.size() != records.size()) {
            return false;
        }
        String sql = "INSERT INTO identifier_vector_index (identifier, vector_dim, vector_norm, vector_data, update_time) "
                + "VALUES (?,?,?,?,?) "
                + "ON DUPLICATE KEY UPDATE vector_dim=VALUES(vector_dim), vector_norm=VALUES(vector_norm), "
                + "vector_data=VALUES(vector_data), update_time=VALUES(update_time)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int i = 0; i < records.size(); i++) {
                VectorRecord record = records.get(i);
                double[] vector = vectors.get(i);
                ps.setString(1, record.identifier);
                ps.setInt(2, vector == null ? 0 : vector.length);
                ps.setDouble(3, DenseVectorSupport.norm(vector));
                ps.setString(4, DenseVectorSupport.serialize(vector));
                ps.setString(5, record.updateTime);
                ps.addBatch();
            }
            ps.executeBatch();
            return true;
        } catch (SQLException | RuntimeException exception) {
            return false;
        }
    }

    @Override
    public boolean deleteBatch(List<String> identifiers) {
        if (identifiers == null || identifiers.isEmpty()) {
            return true;
        }
        StringBuilder sql = new StringBuilder("DELETE FROM identifier_vector_index WHERE identifier IN (");
        for (int i = 0; i < identifiers.size(); i++) {
            if (i > 0) {
                sql.append(',');
            }
            sql.append('?');
        }
        sql.append(')');
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql.toString())) {
            int idx = 1;
            for (String id : identifiers) {
                ps.setString(idx++, id);
            }
            ps.executeUpdate();
            return true;
        } catch (SQLException exception) {
            return false;
        }
    }

    @Override
    public List<VectorSearchHit> search(String queryText, int topK) {
        int limit = Math.max(0, topK);
        if (limit <= 0 || queryText == null || queryText.trim().isEmpty()) {
            return Collections.<VectorSearchHit>emptyList();
        }
        double[] query = embeddingProvider.embedText(queryText);
        double queryNorm = DenseVectorSupport.norm(query);
        if (queryNorm <= 0.0) {
            return Collections.<VectorSearchHit>emptyList();
        }

        PriorityQueue<VectorSearchHit> top = new PriorityQueue<VectorSearchHit>(Math.max(8, limit),
                new Comparator<VectorSearchHit>() {
                    @Override
                    public int compare(VectorSearchHit left, VectorSearchHit right) {
                        int compare = Double.compare(left.getScore(), right.getScore());
                        if (compare != 0) {
                            return compare;
                        }
                        return String.valueOf(left.getIdentifier()).compareTo(String.valueOf(right.getIdentifier()));
                    }
                });

        String sql = "SELECT identifier, vector_norm, vector_data FROM identifier_vector_index WHERE vector_dim=?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, Math.max(0, embeddingProvider.dimensions()));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String identifier = rs.getString("identifier");
                    String data = rs.getString("vector_data");
                    double norm = rs.getDouble("vector_norm");
                    double[] candidate = DenseVectorSupport.deserialize(data, embeddingProvider.dimensions());
                    double score = DenseVectorSupport.cosine(query, candidate, norm);
                    if (score <= 0.0) {
                        continue;
                    }
                    VectorSearchHit hit = new VectorSearchHit(identifier, score);
                    if (top.size() < limit) {
                        top.offer(hit);
                    } else if (top.peek() != null && hit.getScore() > top.peek().getScore()) {
                        top.poll();
                        top.offer(hit);
                    }
                }
            }
        } catch (SQLException exception) {
            return Collections.<VectorSearchHit>emptyList();
        }

        List<VectorSearchHit> out = new ArrayList<VectorSearchHit>(top);
        out.sort(new Comparator<VectorSearchHit>() {
            @Override
            public int compare(VectorSearchHit left, VectorSearchHit right) {
                return Double.compare(right.getScore(), left.getScore());
            }
        });
        return out;
    }

    private List<VectorRecord> prepareRecords(List<IdentifierMetadata> metadataList) {
        List<VectorRecord> records = new ArrayList<VectorRecord>();
        for (IdentifierMetadata metadata : metadataList) {
            if (metadata == null || isBlank(metadata.getIdentifier())) {
                continue;
            }
            records.add(new VectorRecord(
                    metadata.getIdentifier().trim(),
                    documentBuilder.buildText(metadata),
                    metadata.getUpdateTime() == null ? "" : metadata.getUpdateTime()
            ));
        }
        return records;
    }

    private List<String> extractTexts(List<VectorRecord> records) {
        List<String> texts = new ArrayList<String>(records.size());
        for (VectorRecord record : records) {
            texts.add(record.text);
        }
        return texts;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static final class VectorRecord {
        private final String identifier;
        private final String text;
        private final String updateTime;

        private VectorRecord(String identifier, String text, String updateTime) {
            this.identifier = identifier == null ? "" : identifier;
            this.text = text == null ? "" : text;
            this.updateTime = updateTime == null ? "" : updateTime;
        }
    }
}