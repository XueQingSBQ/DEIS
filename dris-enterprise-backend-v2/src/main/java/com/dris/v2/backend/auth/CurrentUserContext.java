package com.dris.v2.backend.auth;

import com.dris.v2.backend.model.BusinessException;
import org.springframework.http.HttpStatus;

public final class CurrentUserContext {
    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<CurrentUser>();

    private CurrentUserContext() {
    }

    public static void set(CurrentUser currentUser) {
        HOLDER.set(currentUser);
    }

    public static CurrentUser get() {
        return HOLDER.get();
    }

    public static String getUserIdOrNull() {
        CurrentUser currentUser = HOLDER.get();
        return currentUser == null ? null : currentUser.getUserId();
    }

    public static CurrentUser require() {
        CurrentUser currentUser = HOLDER.get();
        if (currentUser == null || isBlank(currentUser.getUserId())) {
            throw new BusinessException("UNAUTHORIZED", "authentication is required", HttpStatus.UNAUTHORIZED);
        }
        return currentUser;
    }

    public static void clear() {
        HOLDER.remove();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
