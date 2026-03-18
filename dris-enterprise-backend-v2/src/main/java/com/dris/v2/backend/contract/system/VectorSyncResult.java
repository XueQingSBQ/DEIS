package com.dris.v2.backend.contract.system;

public class VectorSyncResult {
    private int processed;
    private int updated;
    private int deleted;
    private int failed;

    public int getProcessed() {
        return processed;
    }

    public void setProcessed(int processed) {
        this.processed = processed;
    }

    public int getUpdated() {
        return updated;
    }

    public void setUpdated(int updated) {
        this.updated = updated;
    }

    public int getDeleted() {
        return deleted;
    }

    public void setDeleted(int deleted) {
        this.deleted = deleted;
    }

    public int getFailed() {
        return failed;
    }

    public void setFailed(int failed) {
        this.failed = failed;
    }
}
