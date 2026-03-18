package com.dris.auth.center.auth;

import com.dris.auth.center.auth.dto.AuthPublicKeyResponse;
import com.dris.auth.center.auth.dto.AuthTokenResponse;
import com.dris.auth.center.auth.dto.LoginRequest;
import com.dris.auth.center.auth.dto.RegisterUserRequest;
import com.dris.auth.center.auth.dto.UserInfoResponse;
import com.dris.auth.center.dto.ApiResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/auth/register")
    public ResponseEntity<ApiResponse<AuthTokenResponse>> register(@RequestBody RegisterUserRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(authService.register(request)));
    }

    @PostMapping("/auth/login")
    public ResponseEntity<ApiResponse<AuthTokenResponse>> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(authService.login(request)));
    }

    @PostMapping(value = "/oauth2/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<ApiResponse<AuthTokenResponse>> token(@RequestParam MultiValueMap<String, String> form) {
        String grantType = first(form, "grant_type");
        if (!"password".equalsIgnoreCase(grantType)) {
            throw new AuthException("UNSUPPORTED_GRANT_TYPE", "only password grant_type is supported", org.springframework.http.HttpStatus.BAD_REQUEST);
        }
        LoginRequest request = new LoginRequest();
        request.setUsername(first(form, "username"));
        request.setPassword(first(form, "password"));
        return ResponseEntity.ok(ApiResponse.ok(authService.login(request)));
    }

    @GetMapping({"/auth/userinfo", "/oauth2/userinfo"})
    public ResponseEntity<ApiResponse<UserInfoResponse>> userinfo(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return ResponseEntity.ok(ApiResponse.ok(authService.currentUserInfo(authorization)));
    }

    @GetMapping("/auth/public-key")
    public ResponseEntity<ApiResponse<AuthPublicKeyResponse>> publicKey() {
        return ResponseEntity.ok(ApiResponse.ok(authService.publicKey()));
    }

    private static String first(MultiValueMap<String, String> form, String key) {
        return form == null ? null : form.getFirst(key);
    }
}
