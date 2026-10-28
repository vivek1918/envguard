package com.envguard.controller;

import com.envguard.dto.request.AssignRoleRequest;
import com.envguard.dto.request.RegisterRequest;
import com.envguard.dto.response.ApiResponse;
import com.envguard.security.UserPrincipal;
import com.envguard.service.AuthService;
import com.envguard.service.UserAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserAdminService userAdminService;
    private final AuthService authService;

    @GetMapping
    public ApiResponse<List<AuthService.UserView>> list() {
        return ApiResponse.ok(userAdminService.listUsers());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AuthService.AuthResult> create(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(authService.register(request));
    }

    @PostMapping("/{userId}/roles")
    public ApiResponse<AuthService.UserView> assignRole(
            @PathVariable UUID userId,
            @Valid @RequestBody AssignRoleRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(userAdminService.assignRole(userId, request, principal.getId()));
    }
}
