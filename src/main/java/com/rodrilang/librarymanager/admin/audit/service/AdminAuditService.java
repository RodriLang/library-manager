package com.rodrilang.librarymanager.admin.audit.service;

import com.rodrilang.librarymanager.admin.audit.model.AdminAuditLog;
import com.rodrilang.librarymanager.admin.audit.repository.AdminAuditLogRepository;
import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
@RequiredArgsConstructor
public class AdminAuditService {
    private final AdminAuditLogRepository repository;
    private final BookstoreContext context;

    public void record(String action, String entityType, Object entityId, String summary) {
        Long actor = null;
        try { actor = context.getCurrentUserId(); } catch (RuntimeException ignored) {}
        String ip = null;
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest req = attrs.getRequest(); ip = req.getRemoteAddr();
        }
        repository.save(AdminAuditLog.builder().actorUserId(actor).action(action).entityType(entityType)
                .entityId(entityId == null ? null : String.valueOf(entityId)).summary(summary).ip(ip).build());
    }
}
