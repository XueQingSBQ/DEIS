package com.dris.v2.backend.service.local.upload;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class UploadProfiles {
    private static final Map<String, UploadProfile> PROFILES = init();

    private UploadProfiles() {
    }

    public static UploadProfile byName(String name) {
        if (name == null) {
            return null;
        }
        return PROFILES.get(name.trim());
    }

    private static Map<String, UploadProfile> init() {
        Map<String, UploadProfile> map = new LinkedHashMap<String, UploadProfile>();
        map.put("doc_pdf", new UploadProfile("doc_pdf", "resource", "doc", "pdf", "application/pdf", false, 0));
        map.put("doc_word", new UploadProfile("doc_word", "resource", "doc", "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", false, 0));
        map.put("data_json", new UploadProfile("data_json", "resource", "dataset", "json", "application/json", false, 0));
        map.put("data_jsonl", new UploadProfile("data_jsonl", "resource", "dataset", "jsonl", "application/x-ndjson", false, 0));
        map.put("data_csv", new UploadProfile("data_csv", "resource", "dataset", "csv", "text/csv", false, 0));
        map.put("data_parquet", new UploadProfile("data_parquet", "resource", "dataset", "parquet", "application/octet-stream", false, 0));
        map.put("table_excel", new UploadProfile("table_excel", "resource", "table", "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", false, 0));
        map.put("image_png", new UploadProfile("image_png", "resource", "image", "png", "image/png", false, 0));
        map.put("image_jpeg", new UploadProfile("image_jpeg", "resource", "image", "jpeg", "image/jpeg", false, 0));
        map.put("image_webp", new UploadProfile("image_webp", "resource", "image", "webp", "image/webp", false, 0));
        map.put("generic_file", new UploadProfile("generic_file", "resource", "file", "bin", "application/octet-stream", false, 0));
        map.put("tool_zip_cli", new UploadProfile("tool_zip_cli", "tool", "tool", "zip", "application/zip", true, 0));
        map.put("service_openapi_json", new UploadProfile("service_openapi_json", "service", "service", "openapi_json", "application/json", false, 0));
        map.put("service_openapi_yaml", new UploadProfile("service_openapi_yaml", "service", "service", "openapi_yaml", "application/yaml", false, 0));
        return Collections.unmodifiableMap(map);
    }
}