package com.ledgerlens.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class ApiRequestProtectionFilter extends OncePerRequestFilter {

    private static final String API_PREFIX = "/api/v1/";
    private static final String WORKSPACE_HEADER = "X-Workspace-Id";
    private static final long WINDOW_MILLIS = 60_000;
    private static final int GENERAL_LIMIT = 60;
    private static final int UPLOAD_LIMIT = 10;
    private static final Pattern UUID_FORMAT = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private final ObjectMapper objectMapper;
    private final Map<RateLimitKey, Window> windows = new HashMap<>();
    private long requestsSinceCleanup;

    public ApiRequestProtectionFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestUri = request.getRequestURI();
        if (!requestUri.equals("/api/v1") && !requestUri.startsWith(API_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())
                && request.getHeader("Access-Control-Request-Method") != null) {
            filterChain.doFilter(request, response);
            return;
        }

        UUID workspaceId;
        try {
            String workspaceHeader = request.getHeader(WORKSPACE_HEADER);
            if (workspaceHeader == null || !UUID_FORMAT.matcher(workspaceHeader).matches()) {
                throw new IllegalArgumentException();
            }
            workspaceId = UUID.fromString(workspaceHeader);
        } catch (IllegalArgumentException exception) {
            writeProblem(response, HttpStatus.BAD_REQUEST, "O cabeçalho X-Workspace-Id deve conter um UUID válido.");
            return;
        }

        WorkspaceContext.setWorkspaceId(workspaceId);
        try {
            String clientIp = clientIp(request);
            if (!acquire(clientIp, workspaceId, false)) {
                response.setHeader("Retry-After", retryAfterSeconds(clientIp, workspaceId, false));
                writeProblem(response, HttpStatus.TOO_MANY_REQUESTS,
                        "Limite de requisições excedido. Tente novamente em instantes.");
                return;
            }
            if (requestUri.equals("/api/v1/batches/upload")
                    && !acquire(clientIp, workspaceId, true)) {
                response.setHeader("Retry-After", retryAfterSeconds(clientIp, workspaceId, true));
                writeProblem(response, HttpStatus.TOO_MANY_REQUESTS,
                        "Limite de envios excedido. Tente novamente em instantes.");
                return;
            }
            filterChain.doFilter(request, response);
        } finally {
            WorkspaceContext.clear();
        }
    }

    private synchronized boolean acquire(String ip, UUID workspaceId, boolean upload) {
        long now = System.currentTimeMillis();
        long windowStart = now - now % WINDOW_MILLIS;
        int limit = upload ? UPLOAD_LIMIT : GENERAL_LIMIT;
        RateLimitKey key = new RateLimitKey(ip, workspaceId, upload);
        Window window = windows.get(key);
        if (window == null || window.startMillis != windowStart) {
            window = new Window(windowStart);
            windows.put(key, window);
        }
        boolean allowed = window.count < limit;
        if (allowed) window.count++;
        if (++requestsSinceCleanup >= 100) {
            windows.values().removeIf(value -> value.startMillis < windowStart);
            requestsSinceCleanup = 0;
        }
        return allowed;
    }

    private static String clientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

    private synchronized String retryAfterSeconds(String ip, UUID workspaceId, boolean upload) {
        Window window = windows.get(new RateLimitKey(ip, workspaceId, upload));
        if (window == null) {
            return "1";
        }
        long remainingMillis = window.startMillis + WINDOW_MILLIS - System.currentTimeMillis();
        return Long.toString(Math.max(1, (remainingMillis + 999) / 1000));
    }

    private void writeProblem(HttpServletResponse response, HttpStatus status, String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status == HttpStatus.BAD_REQUEST ? "Requisição inválida" : "Limite excedido");
        objectMapper.writeValue(response.getOutputStream(), problem);
    }

    private record RateLimitKey(String ip, UUID workspaceId, boolean upload) {
    }

    private static final class Window {
        private final long startMillis;
        private int count;

        private Window(long startMillis) {
            this.startMillis = startMillis;
        }
    }
}
