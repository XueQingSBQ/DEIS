package com.dris.v2.backend.service;

import com.dris.v2.backend.dao.IdentifierComponentVersionQueryDao;
import com.dris.v2.backend.dto.IdentifierComponentVersionPageResponse;
import com.dris.v2.backend.dto.IdentifierComponentVersionView;
import com.dris.v2.backend.model.BusinessException;
import com.dris.v2.backend.model.IdentifierComponentVersionRecord;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class IdentifierComponentVersionQueryService {
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 200;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final IdentifierComponentVersionQueryDao queryDao;

    public IdentifierComponentVersionQueryService(IdentifierComponentVersionQueryDao queryDao) {
        this.queryDao = queryDao;
    }

    public IdentifierComponentVersionPageResponse queryPage(String identifier, Integer page, Integer size) {
        String normalizedIdentifier = normalizeIdentifier(identifier);
        int pageNo = normalizePage(page);
        int pageSize = normalizeSize(size);
        int offset = (pageNo - 1) * pageSize;

        long total = queryDao.countByIdentifier(normalizedIdentifier);
        List<IdentifierComponentVersionRecord> rows = total <= 0L
                ? java.util.Collections.<IdentifierComponentVersionRecord>emptyList()
                : queryDao.findPage(normalizedIdentifier, offset, pageSize);

        List<IdentifierComponentVersionView> views = new ArrayList<IdentifierComponentVersionView>();
        for (IdentifierComponentVersionRecord row : rows) {
            if (row != null) {
                views.add(toView(row));
            }
        }

        IdentifierComponentVersionPageResponse response = new IdentifierComponentVersionPageResponse();
        response.setRecords(views);
        response.setTotal(total);
        response.setPage(pageNo);
        response.setSize(pageSize);
        response.setHasNext((long) pageNo * (long) pageSize < total);
        return response;
    }

    public IdentifierComponentVersionView getOne(String identifier, Long versionNo) {
        String normalizedIdentifier = normalizeIdentifier(identifier);
        long normalizedVersionNo = normalizeVersionNo(versionNo);
        IdentifierComponentVersionRecord row = queryDao.findOne(normalizedIdentifier, normalizedVersionNo);
        if (row == null) {
            throw new BusinessException("NOT_FOUND", "component version not found", HttpStatus.NOT_FOUND);
        }
        return toView(row);
    }

    private IdentifierComponentVersionView toView(IdentifierComponentVersionRecord row) {
        IdentifierComponentVersionView view = new IdentifierComponentVersionView();
        view.setIdentifier(safe(row.getIdentifier()));
        view.setVersionNo(row.getVersionNo());
        view.setComponentType(safe(row.getComponentType()));
        view.setStorageType(safe(row.getStorageType()));
        view.setAccessMethod(safe(row.getAccessMethod()));
        view.setContentType(safe(row.getContentType()));
        view.setFormat(safe(row.getFormat()));
        view.setLocator(safe(row.getLocator()));
        view.setSizeBytes(row.getSizeBytes());
        view.setHashValue(safe(row.getHashValue()));
        view.setEtag(safe(row.getEtag()));
        view.setProviderVersionId(safe(row.getProviderVersionId()));
        view.setSourceUploadId(safe(row.getSourceUploadId()));
        view.setBindMode(safe(row.getBindMode()));
        view.setVerifyStatus(safe(row.getVerifyStatus()));
        view.setCreatedAt(safe(row.getCreatedAt()));
        view.setExtra(parseExtra(row.getExtraJson()));
        return view;
    }

    private Map<String, Object> parseExtra(String extraJson) {
        if (extraJson == null || extraJson.trim().isEmpty()) {
            return new LinkedHashMap<String, Object>();
        }
        try {
            Map<String, Object> parsed = OBJECT_MAPPER.readValue(extraJson, new TypeReference<Map<String, Object>>() {
            });
            return parsed == null ? new LinkedHashMap<String, Object>() : parsed;
        } catch (Exception ex) {
            return new LinkedHashMap<String, Object>();
        }
    }

    private String normalizeIdentifier(String identifier) {
        if (identifier == null || identifier.trim().isEmpty()) {
            throw new BusinessException("VALIDATION_ERROR", "identifier is required", HttpStatus.BAD_REQUEST);
        }
        String trimmed = identifier.trim();
        if (trimmed.length() > 30) {
            throw new BusinessException("VALIDATION_ERROR", "identifier is too long", HttpStatus.BAD_REQUEST);
        }
        return trimmed;
    }

    private long normalizeVersionNo(Long versionNo) {
        long value = versionNo == null ? 0L : versionNo.longValue();
        if (value < 1L) {
            throw new BusinessException("VALIDATION_ERROR", "versionNo must be greater than or equal to 1", HttpStatus.BAD_REQUEST);
        }
        return value;
    }

    private int normalizePage(Integer page) {
        int value = page == null ? DEFAULT_PAGE : page.intValue();
        if (value < 1) {
            throw new BusinessException("VALIDATION_ERROR", "page must be greater than or equal to 1", HttpStatus.BAD_REQUEST);
        }
        return value;
    }

    private int normalizeSize(Integer size) {
        int value = size == null ? DEFAULT_SIZE : size.intValue();
        if (value < 1 || value > MAX_SIZE) {
            throw new BusinessException("VALIDATION_ERROR", "size must be between 1 and " + MAX_SIZE, HttpStatus.BAD_REQUEST);
        }
        return value;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
