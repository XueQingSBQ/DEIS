package com.dris.sdk.next.result;

public class BatchItemResult<T> {
    private String key;
    private BatchItemStatus status;
    private String code;
    private String message;
    private T data;

    public static <T> BatchItemResult<T> success(String key, T data) {
        BatchItemResult<T> item = new BatchItemResult<T>();
        item.key = key;
        item.status = BatchItemStatus.SUCCESS;
        item.code = "OK";
        item.data = data;
        return item;
    }

    public static <T> BatchItemResult<T> failure(String key, BatchItemStatus status, String code, String message) {
        BatchItemResult<T> item = new BatchItemResult<T>();
        item.key = key;
        item.status = status;
        item.code = code;
        item.message = message;
        return item;
    }

    public String getKey() {
        return key;
    }

    public BatchItemStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }
}