package com.dris.v2.backend.contract.system;

public class KeywordIndexSyncResult {
    private String engine = "";
    private String indexName = "";
    private int processed;
    private int indexed;
    private int deleted;
    private int failed;
    private boolean skipped;
    private String message = "";

    public String getEngine() {
        return engine;
    }

    public void setEngine(String engine) {
        this.engine = engine == null ? "" : engine;
    }

    public String getIndexName() {
        return indexName;
    }

    public void setIndexName(String indexName) {
        this.indexName = indexName == null ? "" : indexName;
    }

    public int getProcessed() {
        return processed;
    }

    public void setProcessed(int processed) {
        this.processed = processed;
    }

    public int getIndexed() {
        return indexed;
    }

    public void setIndexed(int indexed) {
        this.indexed = indexed;
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

    public boolean isSkipped() {
        return skipped;
    }

    public void setSkipped(boolean skipped) {
        this.skipped = skipped;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message == null ? "" : message;
    }
}
