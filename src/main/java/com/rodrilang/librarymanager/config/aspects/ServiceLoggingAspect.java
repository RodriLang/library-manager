package com.rodrilang.librarymanager.config.aspects;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.MDC;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;

@Slf4j
@Aspect
@Component
public class ServiceLoggingAspect {

    private static final long SLOW_SERVICE_THRESHOLD_MS = 500;

    @Around("@within(org.springframework.stereotype.Service)")
    public Object logServiceExecution(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();

        String requestId = MDC.get("requestId");

        String serviceName = joinPoint.getSignature()
                .getDeclaringType()
                .getSimpleName();

        String methodName = joinPoint.getSignature().getName();

        log.debug(
                "SERVICE START [{}] {}.{}() args={}",
                requestId,
                serviceName,
                methodName,
                sanitizeArgs(joinPoint.getArgs())
        );

        try {
            Object result = joinPoint.proceed();

            long duration = System.currentTimeMillis() - startTime;

            if (duration >= SLOW_SERVICE_THRESHOLD_MS) {
                log.warn(
                        "SLOW SERVICE [{}] {}.{}() time={}ms",
                        requestId,
                        serviceName,
                        methodName,
                        duration
                );
            } else {
                log.debug(
                        "SERVICE END [{}] {}.{}() time={}ms",
                        requestId,
                        serviceName,
                        methodName,
                        duration
                );
            }

            return result;

        } catch (Exception ex) {
            long duration = System.currentTimeMillis() - startTime;

            log.error(
                    "SERVICE ERROR [{}] {}.{}() error={} message={} time={}ms",
                    requestId,
                    serviceName,
                    methodName,
                    ex.getClass().getSimpleName(),
                    ex.getMessage(),
                    duration
            );

            throw ex;
        }
    }

    private String sanitizeArgs(Object[] args) {
        return Arrays.stream(args)
                .map(this::sanitizeArg)
                .toList()
                .toString();
    }

    private Object sanitizeArg(Object arg) {
        return switch (arg) {
            case null -> null;
            case MultipartFile file -> "MultipartFile{name=%s, originalFilename=%s, contentType=%s, size=%d}"
                    .formatted(
                            file.getName(),
                            file.getOriginalFilename(),
                            file.getContentType(),
                            file.getSize()
                    );
            case Pageable pageable -> "Pageable{page=%d, size=%d, sort=%s}"
                    .formatted(
                            pageable.getPageNumber(),
                            pageable.getPageSize(),
                            pageable.getSort()
                    );
            default -> arg;
        };

    }
}