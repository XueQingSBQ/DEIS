package com.dris.v2.backend.service.local.system;

import com.dris.sdk.next.domain.IdentifierMetadata;
import com.dris.v2.backend.contract.DrisResponse;
import com.dris.v2.backend.contract.system.VectorSyncResult;
import com.dris.v2.backend.vector.VectorIndexGateway;
import com.dris.v2.backend.dao.IdentifierVectorMaintenanceDao;
import com.dris.v2.backend.metrics.BackendOperationMetrics;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DefaultLocalSystemOpsService implements LocalSystemOpsService {
    private final IdentifierVectorMaintenanceDao identifierVectorMaintenanceDao;
    private final VectorIndexGateway vectorIndexGateway;
    private final BackendOperationMetrics metrics;

    public DefaultLocalSystemOpsService(
            IdentifierVectorMaintenanceDao identifierVectorMaintenanceDao,
            VectorIndexGateway vectorIndexGateway,
            BackendOperationMetrics metrics
    ) {
        this.identifierVectorMaintenanceDao = identifierVectorMaintenanceDao;
        this.vectorIndexGateway = vectorIndexGateway;
        this.metrics = metrics;
    }

    @Override
    public DrisResponse<VectorSyncResult> syncVectorIndex() {
        long start = System.nanoTime();
        boolean ok = false;
        try {
            List<IdentifierMetadata> dirty = identifierVectorMaintenanceDao.findVectorDirty();
            VectorSyncResult result = new VectorSyncResult();
            if (dirty == null || dirty.isEmpty()) {
                ok = true;
                return DrisResponse.success(result);
            }

            List<IdentifierMetadata> upserts = new ArrayList<IdentifierMetadata>();
            List<String> deletes = new ArrayList<String>();
            for (IdentifierMetadata metadata : dirty) {
                if (metadata == null || metadata.getIdentifier() == null) {
                    continue;
                }
                if ("deleted".equalsIgnoreCase(metadata.getStatus())) {
                    deletes.add(metadata.getIdentifier());
                } else {
                    upserts.add(metadata);
                }
            }

            List<String> okIds = new ArrayList<String>();
            if (!upserts.isEmpty()) {
                boolean batchOk = vectorIndexGateway.upsertBatch(upserts);
                if (batchOk) {
                    result.setUpdated(upserts.size());
                    result.setProcessed(result.getProcessed() + upserts.size());
                    for (IdentifierMetadata metadata : upserts) {
                        okIds.add(metadata.getIdentifier());
                    }
                } else {
                    result.setFailed(result.getFailed() + upserts.size());
                }
            }
            if (!deletes.isEmpty()) {
                boolean batchOk = vectorIndexGateway.deleteBatch(deletes);
                if (batchOk) {
                    result.setDeleted(deletes.size());
                    result.setProcessed(result.getProcessed() + deletes.size());
                    okIds.addAll(deletes);
                } else {
                    result.setFailed(result.getFailed() + deletes.size());
                }
            }
            if (!okIds.isEmpty()) {
                identifierVectorMaintenanceDao.clearVectorDirtyBatch(okIds);
            }
            ok = true;
            return DrisResponse.success(result);
        } catch (RuntimeException exception) {
            return DrisResponse.failure("INTERNAL_ERROR", exception.getMessage(), null);
        } finally {
            metrics.record("syncVectorIndex", System.nanoTime() - start, ok);
        }
    }

    @Override
    public Map<String, Map<String, Long>> metricsSnapshot() {
        Map<String, Map<String, Long>> snapshot = new LinkedHashMap<String, Map<String, Long>>(metrics.snapshot());
        Map<String, Long> repository = new LinkedHashMap<String, Long>();
        repository.put("activeIdentifiers", Long.valueOf(identifierVectorMaintenanceDao.countActiveIdentifiers()));
        repository.put("deletedIdentifiers", Long.valueOf(identifierVectorMaintenanceDao.countDeletedIdentifiers()));
        repository.put("vectorDirty", Long.valueOf(identifierVectorMaintenanceDao.countVectorDirty()));
        repository.put("vectorIndexed", Long.valueOf(identifierVectorMaintenanceDao.countVectorIndexed()));
        snapshot.put("repository", repository);
        return snapshot;
    }
}