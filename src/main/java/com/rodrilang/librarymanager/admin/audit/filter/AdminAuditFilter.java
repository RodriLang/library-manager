package com.rodrilang.librarymanager.admin.audit.filter;

import com.rodrilang.librarymanager.admin.audit.model.AdminAuditLog;
import com.rodrilang.librarymanager.admin.audit.repository.AdminAuditLogRepository;
import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class AdminAuditFilter extends OncePerRequestFilter {
    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    private final AdminAuditLogRepository repository;
    private final BookstoreContext context;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/admin/") || READ_METHODS.contains(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        filterChain.doFilter(request, response);
        if (response.getStatus() >= 400) return;
        Long actor = null;
        try { actor = context.getCurrentUserId(); } catch (RuntimeException ignored) { }
        try {
            repository.save(AdminAuditLog.builder()
                    .actorUserId(actor)
                    .action(request.getMethod())
                    .entityType(resolveEntityType(request.getRequestURI()))
                    .entityId(resolveEntityId(request.getRequestURI()))
                    .summary(request.getMethod() + " " + request.getRequestURI())
                    .ip(request.getRemoteAddr())
                    .build());
        } catch (RuntimeException ignored) {
            // La auditoría nunca debe romper la operación administrativa ya completada.
        }
    }

    private String resolveEntityType(String uri) {
        String[] parts = uri.split("/");
        return parts.length > 3 ? parts[3].toUpperCase() : "ADMIN";
    }

    private String resolveEntityId(String uri) {
        String[] parts = uri.split("/");
        return parts.length > 4 && parts[4].matches("\\d+") ? parts[4] : null;
    }
}
