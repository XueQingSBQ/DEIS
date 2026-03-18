package com.dris.v2.backend.dto;

import java.util.ArrayList;
import java.util.List;

public class IdentifierComponentVersionPageResponse {
    private List<IdentifierComponentVersionView> records = new ArrayList<IdentifierComponentVersionView>();
    private long total;
    private int page;
    private int size;
    private boolean hasNext;

    public List<IdentifierComponentVersionView> getRecords() {
        return records;
    }

    public void setRecords(List<IdentifierComponentVersionView> records) {
        this.records = records == null ? new ArrayList<IdentifierComponentVersionView>() : records;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public boolean isHasNext() {
        return hasNext;
    }

    public void setHasNext(boolean hasNext) {
        this.hasNext = hasNext;
    }
}
