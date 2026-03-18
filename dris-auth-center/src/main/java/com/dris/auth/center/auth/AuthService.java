package com.dris.auth.center.auth;

import com.dris.auth.center.auth.dto.AuthPublicKeyResponse;
import com.dris.auth.center.auth.dto.AuthTokenResponse;
import com.dris.auth.center.auth.dto.LoginRequest;
import com.dris.auth.center.auth.dto.RegisterUserRequest;
import com.dris.auth.center.auth.dto.UserInfoResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AuthService {
    private final AuthUserDao authUserDao;
    private final JwtTokenService jwtTokenService;
    private final AuthProperties properties;
    private final PasswordEncoder passwordEncoder;

    public AuthService(
            AuthUserDao authUserDao,
            JwtTokenService jwtTokenService,
            AuthProperties properties,
            PasswordEncoder passwordEncoder
    ) {
        this.authUserDao = authUserDao;
        this.jwtTokenService = jwtTokenService;
        this.properties = properties;
        this.passwordEncoder = passwordEncoder;
    }

    public AuthTokenResponse register(RegisterUserRequest request) {
        if (request == null) {
            throw new AuthException("VALIDATION_ERROR", "request is required", HttpStatus.BAD_REQUEST);
        }
        String username = require(request.getUsername(), "username is required");
        String password = require(request.getPassword(), "password is required");
        String displayName = require(request.getDisplayName(), "displayName is required");
        String orgCode = require(request.getOrgCode(), "orgCode is required");
        if (password.length() < 6) {
            throw new AuthException("VALIDATION_ERROR", "password length must be at least 6", HttpStatus.BAD_REQUEST);
        }
        if (authUserDao.findByUsername(username) != null) {
            throw new AuthException("USERNAME_EXISTS", "username already exists", HttpStatus.CONFLICT);
        }

        AuthUser user = new AuthUser();
        user.setUserId("u_" + UUID.randomUUID().toString().replace("-", ""));
        user.setUsername(username);
        user.setDisplayName(displayName);
        user.setOrgCode(orgCode);
        user.setEmail(trim(request.getEmail()));
        user.setPhone(trim(request.getPhone()));
        user.setStatus("active");
        user.setPasswordHash(passwordEncoder.encode(password));
        authUserDao.insert(user);
        return toTokenResponse(user);
    }

    public AuthTokenResponse login(LoginRequest request) {
        if (request == null) {
            throw new AuthException("VALIDATION_ERROR", "request is required", HttpStatus.BAD_REQUEST);
        }
        String username = require(request.getUsername(), "username is required");
        String password = require(request.getPassword(), "password is required");
        AuthUser user = authUserDao.findByUsername(username);
        if (user == null || isBlank(user.getPasswordHash()) || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new AuthException("INVALID_CREDENTIALS", "username or password is invalid", HttpStatus.UNAUTHORIZED);
        }
        if (!"active".equalsIgnoreCase(trim(user.getStatus()))) {
            throw new AuthException("USER_DISABLED", "user is disabled", HttpStatus.FORBIDDEN);
        }
        return toTokenResponse(user);
    }

    public UserInfoResponse currentUserInfo(String bearerToken) {
        String token = extractToken(bearerToken);
        CurrentUser currentUser = jwtTokenService.parse(token);
        UserInfoResponse out = new UserInfoResponse();
        out.setUserId(currentUser.getUserId());
        out.setUsername(currentUser.getUsername());
        out.setDisplayName(currentUser.getDisplayName());
        out.setOrgCode(currentUser.getOrgCode());
        out.setTokenId(currentUser.getTokenId());
        return out;
    }

    public AuthPublicKeyResponse publicKey() {
        AuthPublicKeyResponse out = new AuthPublicKeyResponse();
        out.setIssuer(jwtTokenService.getIssuer());
        out.setAudience(jwtTokenService.getAudience());
        out.setKeyId(jwtTokenService.getKeyId());
        out.setAlgorithm("RS256");
        out.setPublicKeyPem(jwtTokenService.getPublicKeyPem());
        return out;
    }

    private AuthTokenResponse toTokenResponse(AuthUser user) {
        AuthTokenResponse out = new AuthTokenResponse();
        out.setAccessToken(jwtTokenService.issueToken(user));
        out.setExpiresIn(Math.max(300L, properties.getAccessTokenTtlSeconds()));
        out.setUserId(user.getUserId());
        out.setUsername(user.getUsername());
        out.setDisplayName(user.getDisplayName());
        out.setOrgCode(user.getOrgCode());
        return out;
    }

    private static String extractToken(String header) {
        if (header == null || !header.startsWith("Bearer ")) {
            throw new AuthException("UNAUTHORIZED", "Authorization Bearer token is required", HttpStatus.UNAUTHORIZED);
        }
        String token = header.substring("Bearer ".length()).trim();
        if (token.isEmpty()) {
            throw new AuthException("UNAUTHORIZED", "access token is empty", HttpStatus.UNAUTHORIZED);
        }
        return token;
    }

    private static String require(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new AuthException("VALIDATION_ERROR", message, HttpStatus.BAD_REQUEST);
        }
        return value.trim();
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
