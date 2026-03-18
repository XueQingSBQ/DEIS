package com.dris.v2.backend.vector;

import com.dris.sdk.next.domain.IdentifierMetadata;
import com.dris.v2.backend.vector.VectorSearchHit;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnnVectorIndexGatewayTest {

    @Test
    void upsertSearchDelete_shouldWorkWithInMemoryAnnIndex() throws Exception {
        DataSource dataSource = mockDataSource(true);
        AnnVectorIndexGateway gateway = new AnnVectorIndexGateway(dataSource);

        IdentifierMetadata traffic = metadata(
                "1100ABCD0211202603071000000001",
                "city traffic monitoring platform",
                "urban traffic signal control and scheduling"
        );
        IdentifierMetadata energy = metadata(
                "1100ABCD0111202603071000000002",
                "county wind energy generation",
                "renewable wind energy output and prediction"
        );

        assertTrue(gateway.upsertBatch(Arrays.asList(traffic, energy)));

        List<VectorSearchHit> hits = gateway.search("wind energy prediction", 3);
        assertTrue(hits.stream().anyMatch(hit -> energy.getIdentifier().equals(hit.getIdentifier())));

        assertTrue(gateway.deleteBatch(Arrays.asList(energy.getIdentifier())));
        List<VectorSearchHit> afterDelete = gateway.search("wind energy prediction", 3);
        assertFalse(afterDelete.stream().anyMatch(hit -> energy.getIdentifier().equals(hit.getIdentifier())));
    }

    @Test
    void upsert_sqlError_shouldReturnFalse() throws Exception {
        DataSource dataSource = mockDataSource(false);
        AnnVectorIndexGateway gateway = new AnnVectorIndexGateway(dataSource);

        IdentifierMetadata sample = metadata(
                "1100ABCD0111202603071000000099",
                "sample",
                "sample"
        );
        assertFalse(gateway.upsertBatch(Arrays.asList(sample)));
    }

    private DataSource mockDataSource(boolean success) throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement upsertStatement = mock(PreparedStatement.class);
        PreparedStatement deleteStatement = mock(PreparedStatement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(startsWith("INSERT INTO identifier_vector_index"))).thenReturn(upsertStatement);
        when(connection.prepareStatement(startsWith("DELETE FROM identifier_vector_index"))).thenReturn(deleteStatement);
        if (success) {
            when(upsertStatement.executeBatch()).thenReturn(new int[]{1});
            when(deleteStatement.executeUpdate()).thenReturn(1);
        } else {
            when(upsertStatement.executeBatch()).thenThrow(new SQLException("mock failure"));
        }
        return dataSource;
    }

    private IdentifierMetadata metadata(String identifier, String name, String description) {
        IdentifierMetadata metadata = new IdentifierMetadata();
        metadata.setIdentifier(identifier);
        metadata.setName(name);
        metadata.setDescription(description);
        metadata.setOwner("owner");
        metadata.setIndustry("01");
        metadata.setDataCategory("01");
        metadata.setRegistrationType("01");
        metadata.setRegionCode("1100");
        metadata.setOrganizationCode("ABCD");
        return metadata;
    }
}
