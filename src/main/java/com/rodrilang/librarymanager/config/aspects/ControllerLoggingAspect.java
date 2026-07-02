package com.rodrilang.librarymanager.config.aspects;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.MDC;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;

@Slf4j
@Aspect
@Component
public class ControllerLoggingAspect {

    private final HttpServletRequest request;

    public ControllerLoggingAspect(HttpServletRequest request) {
        this.request = request;
    }

    @Around("@within(org.springframework.web.bind.annotation.RestController)")
    public Object logControllerRequest(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();

        String controllerName = joinPoint.getSignature()
                .getDeclaringType()
                .getSimpleName();

        String requestId = MDC.get("requestId");
        String methodName = joinPoint.getSignature().getName();
        String httpMethod = request.getMethod();
        String uri = request.getRequestURI();
        String queryString = request.getQueryString();
        String fullPath = queryString == null ? uri : uri + "?" + queryString;

        String clientIp = getClientIp();

        log.info(
                "REQUEST [{}] {} {} -> {}.{}() ip={} args={}",
                requestId,
                httpMethod,
                fullPath,
                controllerName,
                methodName,
                clientIp,
                sanitizeArgs(joinPoint.getArgs())
        );

        try {
            Object response = joinPoint.proceed();

            long duration = System.currentTimeMillis() - startTime;

            Integer status = getStatus(response);

            log.info(
                    "RESPONSE [{}] {} {} -> {}.{}() status={} time={}ms",
                    requestId,
                    httpMethod,
                    fullPath,
                    controllerName,
                    methodName,
                    status,
                    duration
            );

            return response;

        } catch (Exception ex) {
            long duration = System.currentTimeMillis() - startTime;

            log.error(
                    "ERROR [{}] {} {} -> {}.{}() error={} message={} time={}ms",
                    requestId,
                    httpMethod,
                    fullPath,
                    controllerName,
                    methodName,
                    ex.getClass().getSimpleName(),
                    ex.getMessage(),
                    duration
            );

            throw ex;
        }
    }

    private Integer getStatus(Object response) {
        if (response instanceof ResponseEntity<?> responseEntity) {
            return responseEntity.getStatusCode().value();
        }

        return null;
    }

    private String getClientIp() {
        String forwardedFor = request.getHeader("X-Forwarded-For");

        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }

        return request.getRemoteAddr();
    }

    private String sanitizeArgs(Object[] args) {
        return Arrays.stream(args)
                .map(this::sanitizeArg)
                .toList()
                .toString();
    }

    private Object sanitizeArg(Object arg) {
        if (arg == null) {
            return null;
        }

        if (arg instanceof MultipartFile file) {
            return "MultipartFile{name=%s, originalFilename=%s, contentType=%s, size=%d}"
                    .formatted(
                            file.getName(),
                            file.getOriginalFilename(),
                            file.getContentType(),
                            file.getSize()
                    );
        }

        if (arg instanceof Pageable pageable) {
            return "Pageable{page=%d, size=%d, sort=%s}"
                    .formatted(
                            pageable.getPageNumber(),
                            pageable.getPageSize(),
                            pageable.getSort()
                    );
        }

        return arg;
    }
}