package com.rodrilang.librarymanager.config.aspects;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Aspect
@Component
public class ExternalApiLoggingAspect {

    private static final long SLOW_EXTERNAL_API_THRESHOLD_MS = 1000;

    @Around("within(com.rodrilang.librarymanager.metadata..*)")
    public Object logExternalApiExecution(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();

        String requestId = MDC.get("requestId");

        String providerName = joinPoint.getSignature()
                .getDeclaringType()
                .getSimpleName();

        String methodName = joinPoint.getSignature().getName();

        log.info(
                "EXTERNAL API START [{}] {}.{}() args={}",
                requestId,
                providerName,
                methodName,
                joinPoint.getArgs()
        );

        try {
            Object result = joinPoint.proceed();

            long duration = System.currentTimeMillis() - startTime;

            String resultStatus = resolveResultStatus(result);

            if (duration >= SLOW_EXTERNAL_API_THRESHOLD_MS) {
                log.warn(
                        "SLOW EXTERNAL API [{}] {}.{}() result={} time={}ms",
                        requestId,
                        providerName,
                        methodName,
                        resultStatus,
                        duration
                );
            } else {
                log.info(
                        "EXTERNAL API END [{}] {}.{}() result={} time={}ms",
                        requestId,
                        providerName,
                        methodName,
                        resultStatus,
                        duration
                );
            }

            return result;

        } catch (Exception ex) {
            long duration = System.currentTimeMillis() - startTime;

            log.error(
                    "EXTERNAL API ERROR [{}] {}.{}() error={} message={} time={}ms",
                    requestId,
                    providerName,
                    methodName,
                    ex.getClass().getSimpleName(),
                    ex.getMessage(),
                    duration
            );

            throw ex;
        }
    }

    private String resolveResultStatus(Object result) {
        if (result instanceof Optional<?> optional) {
            return optional.isPresent() ? "FOUND" : "NOT_FOUND";
        }

        if (result == null) {
            return "NULL";
        }

        return "OK";
    }
}