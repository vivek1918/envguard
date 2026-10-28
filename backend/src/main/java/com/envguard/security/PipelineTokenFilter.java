package com.envguard.security;

import com.envguard.config.EnvGuardProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PipelineTokenFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-EnvGuard-Pipeline-Token";
    public static final String ROLE_PIPELINE = "ROLE_PIPELINE";

    private final EnvGuardProperties properties;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !path.startsWith("/api/pipeline");
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String configured = trimToNull(properties.getPipeline().getToken());
        String provided = trimToNull(request.getHeader(HEADER));

        if (!StringUtils.hasText(configured) || !StringUtils.hasText(provided)
                || !constantTimeEquals(configured, provided)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"success\":false,\"message\":\"Invalid or missing pipeline token\"}");
            return;
        }

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "pipeline", null, List.of(new SimpleGrantedAuthority(ROLE_PIPELINE)));
        SecurityContextHolder.getContext().setAuthentication(auth);
        filterChain.doFilter(request, response);
    }

    private static boolean constantTimeEquals(String a, String b) {
        byte[] left = a.getBytes(StandardCharsets.UTF_8);
        byte[] right = b.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(left, right);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
