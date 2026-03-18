package com.dris.v2.backend.service;

import com.dris.v2.backend.dao.IdentifierHistoryQueryDao;
import com.dris.v2.backend.dto.IdentifierHistoryPageResponse;
import com.dris.v2.backend.dto.IdentifierHistoryRecordView;
import com.dris.v2.backend.model.BusinessException;
import com.dris.v2.backend.model.IdentifierHistoryRecord;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class IdentifierAuditQueryService {
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 200;

    private final IdentifierHistoryQueryDao identifierHistoryQueryDao;

    public IdentifierAuditQueryService(IdentifierHistoryQueryDao identifierHistoryQueryDao) {
        this.identifierHistoryQueryDao = identifierHistoryQueryDao;
    }

    public IdentifierHistoryPageResponse queryIdentifierHistory(
            String identifier,
            String operation,
            String startTime,
            String endTime,
            Integer page,
            Integer size
    ) {
        String identifierFilter = normalizeFilter(identifier, 30, "identifier");
        String operationFilter = normalizeFilter(operation, 64, "operation");
        Instant start = parseInstant(startTime, "startTime");
        Instant end = parseInstant(endTime, "endTime");
        if (start != null && end != null && end.isBefore(start)) {
            throw new BusinessException("VALIDATION_ERROR", "endTime must be greater than or equal to startTime", HttpStatus.BAD_REQUEST);
        }
        int pageNo = normalizePage(page);
        int pageSize = normalizeSize(size);
        int offset = (pageNo - 1) * pageSize;

        long total = identifierHistoryQueryDao.count(identifierFilter, operationFilter, start, end);
        List<IdentifierHistoryRecord> records = total <= 0L
                ? Collections.<IdentifierHistoryRecord>emptyList()
                : identifierHistoryQueryDao.findPage(identifierFilter, operationFilter, start, end, offset, pageSize);

        List<IdentifierHistoryRecordView> views = new ArrayList<IdentifierHistoryRecordView>();
        for (IdentifierHistoryRecord record : records) {
            if (record == null) {
                continue;
            }
            IdentifierHistoryRecordView view = new IdentifierHistoryRecordView();
            view.setId(record.getId());
            view.setIdentifier(safe(record.getIdentifier()));
            view.setOperation(safe(record.getOperation()));
            view.setActorUserId(safe(record.getActorUserId()));
            view.setActorRole(safe(record.getActorRole()));
            view.setSuccess(record.isSuccess());
            view.setMessage(safe(record.getMessage()));
            view.setCreatedAt(record.getCreatedAt() == null ? "" : record.getCreatedAt().toString());
            views.add(view);
        }

        IdentifierHistoryPageResponse response = new IdentifierHistoryPageResponse();
        response.setRecords(views);
        response.setTotal(total);
        response.setPage(pageNo);
        response.setSize(pageSize);
        response.setHasNext((long) pageNo * (long) pageSize < total);
        return response;
    }

    private static String normalizeFilter(String value, int maxLen, String fieldName) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > maxLen) {
            throw new BusinessException("VALIDATION_ERROR", fieldName + " is too long", HttpStatus.BAD_REQUEST);
        }
        return trimmed;
    }

    private static int normalizePage(Integer page) {
        int pageNo = page == null ? DEFAULT_PAGE : page.intValue();
        if (pageNo < 1) {
            throw new BusinessException("VALIDATION_ERROR", "page must be greater than or equal to 1", HttpStatus.BAD_REQUEST);
        }
        return pageNo;
    }

    private static int normalizeSize(Integer size) {
        int pageSize = size == null ? DEFAULT_SIZE : size.intValue();
        if (pageSize < 1 || pageSize > MAX_SIZE) {
            throw new BusinessException("VALIDATION_ERROR", "size must be between 1 and " + MAX_SIZE, HttpStatus.BAD_REQUEST);
        }
        return pageSize;
    }

    private static Instant parseInstant(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String trimmed = value.trim();
        try {
            if (trimmed.matches("^[0-9]{1,17}$")) {
                return Instant.ofEpochMilli(Long.parseLong(trimmed));
            }
            return Instant.parse(trimmed);
        } catch (DateTimeParseException ex) {
            throw new BusinessException("VALIDATION_ERROR", fieldName + " must be ISO-8601 or epoch milliseconds", HttpStatus.BAD_REQUEST);
        } catch (NumberFormatException ex) {
            throw new BusinessException("VALIDATION_ERROR", fieldName + " is invalid", HttpStatus.BAD_REQUEST);
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
