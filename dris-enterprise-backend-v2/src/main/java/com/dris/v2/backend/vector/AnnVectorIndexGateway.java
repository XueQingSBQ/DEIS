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
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AnnVectorIndexGateway implements VectorIndexGateway {
    private static final int DEFAULT_DIMENSIONS = 128;
    private static final int DEFAULT_SIGNATURE_BITS = 16;
    private static final int DEFAULT_MIN_CANDIDATES = 64;

    private final DataSource dataSource;
    private final EmbeddingProvider embeddingProvider;
    private final VectorMetadataDocumentBuilder documentBuilder;
    private final int signatureBits;
    private final int minCandidates;
    private final ConcurrentHashMap<String, VectorEntry> vectors = new ConcurrentHashMap<String, VectorEntry>();
    private final ConcurrentHashMap<Integer, Set<String>> buckets = new ConcurrentHashMap<Integer, Set<String>>();
    private final Object loadLock = new Object();
    private volatile boolean loaded = false;

    public AnnVectorIndexGateway(DataSource dataSource) {
        this(dataSource, new HashTextEmbeddingProvider(DEFAULT_DIMENSIONS), DEFAULT_SIGNATURE_BITS, DEFAULT_MIN_CANDIDATES);
    }

    public AnnVectorIndexGateway(DataSource dataSource, EmbeddingProvider embeddingProvider) {
        this(dataSource, embeddingProvider, DEFAULT_SIGNATURE_BITS, DEFAULT_MIN_CANDIDATES);
    }

    public AnnVectorIndexGateway(DataSource dataSource, int dimensions, int signatureBits, int minCandidates) {
        this(dataSource, new HashTextEmbeddingProvider(dimensions), signatureBits, minCandidates);
    }

    public AnnVectorIndexGateway(DataSource dataSource, EmbeddingProvider embeddingProvider, int signatureBits, int minCandidates) {
        this.dataSource = dataSource;
        this.embeddingProvider = embeddingProvider == null
                ? new HashTextEmbeddingProvider(DEFAULT_DIMENSIONS)
                : embeddingProvider;
        this.documentBuilder = new VectorMetadataDocumentBuilder();
        this.signatureBits = Math.max(8, Math.min(24, signatureBits));
        this.minCandidates = Math.max(16, minCandidates);
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
        List<double[]> vectorsBatch = embeddingProvider.embedTexts(extractTexts(records));
        if (vectorsBatch == null || vectorsBatch.size() != records.size()) {
            return false;
        }
        String sql = "INSERT INTO identifier_vector_index (identifier, vector_dim, vector_norm, vector_data, update_time) "
                + "VALUES (?,?,?,?,?) "
                + "ON DUPLICATE KEY UPDATE vector_dim=VALUES(vector_dim), vector_norm=VALUES(vector_norm), "
                + "vector_data=VALUES(vector_data), update_time=VALUES(update_time)";
        List<VectorEntry> updates = new ArrayList<VectorEntry>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int i = 0; i < records.size(); i++) {
                VectorRecord record = records.get(i);
                double[] vector = vectorsBatch.get(i);
                double norm = DenseVectorSupport.norm(vector);
                ps.setString(1, record.identifier);
                ps.setInt(2, vector == null ? 0 : vector.length);
                ps.setDouble(3, norm);
                ps.setString(4, DenseVectorSupport.serialize(vector));
                ps.setString(5, record.updateTime);
                ps.addBatch();
                updates.add(new VectorEntry(record.identifier, vector, norm, signature(vector)));
            }
            ps.executeBatch();
        } catch (SQLException | RuntimeException exception) {
            return false;
        }
        for (VectorEntry entry : updates) {
            putEntry(entry);
        }
        loaded = true;
        return true;
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
            for (String identifier : identifiers) {
                ps.setString(idx++, identifier);
            }
            ps.executeUpdate();
        } catch (SQLException exception) {
            return false;
        }
        for (String identifier : identifiers) {
            removeEntry(identifier);
        }
        return true;
    }

    @Override
    public List<VectorSearchHit> search(String queryText, int topK) {
        int limit = Math.max(0, topK);
        if (limit <= 0 || isBlank(queryText)) {
            return Collections.<VectorSearchHit>emptyList();
        }
        ensureLoaded();
        double[] query = embeddingProvider.embedText(queryText);
        double queryNorm = DenseVectorSupport.norm(query);
        if (queryNorm <= 0.0) {
            return Collections.<VectorSearchHit>emptyList();
        }
        Set<String> candidates = collectCandidates(query);
        if (candidates.isEmpty()) {
            candidates = new HashSet<String>(vectors.keySet());
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
        for (String identifier : candidates) {
            VectorEntry entry = vectors.get(identifier);
            if (entry == null) {
                continue;
            }
            double score = DenseVectorSupport.cosine(query, entry.vector, entry.norm);
            if (score <= 0.0) {
                continue;
            }
            VectorSearchHit hit = new VectorSearchHit(entry.identifier, score);
            if (top.size() < limit) {
                top.offer(hit);
            } else if (top.peek() != null && hit.getScore() > top.peek().getScore()) {
                top.poll();
                top.offer(hit);
            }
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

    private void ensureLoaded() {
        if (loaded) {
            return;
        }
        synchronized (loadLock) {
            if (loaded) {
                return;
            }
            loadAllFromDb();
            loaded = true;
        }
    }

    private void loadAllFromDb() {
        vectors.clear();
        buckets.clear();
        String sql = "SELECT identifier, vector_norm, vector_data FROM identifier_vector_index WHERE vector_dim=?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, Math.max(0, embeddingProvider.dimensions()));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String identifier = rs.getString("identifier");
                    if (isBlank(identifier)) {
                        continue;
                    }
                    double norm = rs.getDouble("vector_norm");
                    double[] vector = DenseVectorSupport.deserialize(rs.getString("vector_data"), embeddingProvider.dimensions());
                    putEntry(new VectorEntry(identifier, vector, norm, signature(vector)));
                }
            }
        } catch (SQLException ignore) {
            // best effort preload; falls back to empty index
        }
    }

    private Set<String> collectCandidates(double[] query) {
        HashSet<String> out = new HashSet<String>();
        int currentSignature = signature(query);
        addBucket(currentSignature, out);
        if (out.size() < minCandidates) {
            for (int i = 0; i < signatureBits; i++) {
                addBucket(currentSignature ^ (1 << i), out);
                if (out.size() >= minCandidates) {
                    return out;
                }
            }
        }
        if (out.size() < minCandidates) {
            for (int i = 0; i < signatureBits; i++) {
                for (int j = i + 1; j < signatureBits; j++) {
                    addBucket(currentSignature ^ (1 << i) ^ (1 << j), out);
                    if (out.size() >= minCandidates) {
                        return out;
                    }
                }
            }
        }
        return out;
    }

    private void addBucket(int signature, Set<String> out) {
        Set<String> ids = buckets.get(Integer.valueOf(signature));
        if (ids != null && !ids.isEmpty()) {
            out.addAll(ids);
        }
    }

    private void putEntry(VectorEntry entry) {
        if (entry == null || isBlank(entry.identifier)) {
            return;
        }
        VectorEntry previous = vectors.put(entry.identifier, entry);
        if (previous != null) {
            removeFromBucket(previous.signature, previous.identifier);
        }
        buckets.computeIfAbsent(Integer.valueOf(entry.signature), new java.util.function.Function<Integer, Set<String>>() {
            @Override
            public Set<String> apply(Integer key) {
                return ConcurrentHashMap.newKeySet();
            }
        }).add(entry.identifier);
    }

    private void removeEntry(String identifier) {
        if (isBlank(identifier)) {
            return;
        }
        VectorEntry previous = vectors.remove(identifier);
        if (previous != null) {
            removeFromBucket(previous.signature, previous.identifier);
        }
    }

    private void removeFromBucket(int signature, String identifier) {
        Set<String> ids = buckets.get(Integer.valueOf(signature));
        if (ids == null) {
            return;
        }
        ids.remove(identifier);
        if (ids.isEmpty()) {
            buckets.remove(Integer.valueOf(signature), ids);
        }
    }

    private int signature(double[] vector) {
        int currentSignature = 0;
        if (vector == null || vector.length == 0) {
            return currentSignature;
        }
        int dimensions = vector.length;
        for (int i = 0; i < signatureBits; i++) {
            int idx = Math.abs((i * 7919 + 17) % dimensions);
            if (vector[idx] >= 0.0) {
                currentSignature |= (1 << i);
            }
        }
        return currentSignature;
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

    private static boolean isBlank(String value) {
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

    private static final class VectorEntry {
        private final String identifier;
        private final double[] vector;
        private final double norm;
        private final int signature;

        private VectorEntry(String identifier, double[] vector, double norm, int signature) {
            this.identifier = identifier == null ? "" : identifier.trim();
            this.vector = vector == null ? new double[0] : vector;
            this.norm = norm;
            this.signature = signature;
        }
    }
}