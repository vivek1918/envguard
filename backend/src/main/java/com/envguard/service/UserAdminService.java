package com.envguard.service;

import com.envguard.domain.entity.Role;
import com.envguard.domain.entity.User;
import com.envguard.domain.enums.RoleName;
import com.envguard.dto.request.AssignRoleRequest;
import com.envguard.exception.ApiException;
import com.envguard.repository.RoleRepository;
import com.envguard.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserAdminService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<AuthService.UserView> listUsers() {
        return userRepository.findAll().stream().map(AuthService::toView).toList();
    }

    @Transactional
    public AuthService.UserView assignRole(UUID userId, AssignRoleRequest request, UUID actorId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        Role role = roleRepository.findByName(request.getRole())
                .orElseThrow(() -> ApiException.badRequest("Unknown role"));
        user.getRoles().add(role);
        userRepository.save(user);
        auditService.record(null, actorId, null, "ROLE_ASSIGNED", "User", userId.toString(),
                Map.of("role", request.getRole().name()));
        return AuthService.toView(user);
    }
}
