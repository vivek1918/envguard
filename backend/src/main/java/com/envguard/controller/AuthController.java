package com.envguard.controller;

import com.envguard.dto.request.LoginRequest;
import com.envguard.dto.request.RegisterRequest;
import com.envguard.dto.response.ApiResponse;
import com.envguard.security.UserPrincipal;
import com.envguard.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ApiResponse<AuthService.AuthResult> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AuthService.AuthResult> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(authService.register(request));
    }

    @PostMapping("/logout")
    public ApiResponse<Map<String, String>> logout() {
        // Stateless JWT — client discards token
        return ApiResponse.ok(Map.of("status", "logged_out"));
    }

    @GetMapping("/me")
    public ApiResponse<AuthService.UserView> me(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(authService.me(principal));
    }
}
