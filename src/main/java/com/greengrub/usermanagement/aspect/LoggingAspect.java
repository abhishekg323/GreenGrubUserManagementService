package com.greengrub.usermanagement.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Aspect for logging method calls, execution time, and exceptions
 * Provides centralized logging for service and controller layers
 */
@Aspect
@Component
@Slf4j
public class LoggingAspect {

    /**
     * Pointcut for all methods in controller package
     */
    @Pointcut("execution(* com.greengrub.customer.controller..*(..))")
    public void controllerMethods() {}

    /**
     * Pointcut for all methods in service package
     */
    @Pointcut("execution(* com.greengrub.customer.service..*(..))")
    public void serviceMethods() {}

    /**
     * Pointcut for all methods in repository package
     */
    @Pointcut("execution(* com.greengrub.customer.repository..*(..))")
    public void repositoryMethods() {}

    /**
     * Log method entry with parameters
     */
    @Before("controllerMethods() || serviceMethods()")
    public void logMethodEntry(JoinPoint joinPoint) {
        String className = joinPoint.getSignature().getDeclaringTypeName();
        String methodName = joinPoint.getSignature().getName();
        Object[] args = joinPoint.getArgs();

        log.debug(">>> Entering: {}.{}() with arguments: {}",
                className, methodName, Arrays.toString(args));
    }

    /**
     * Log method exit with return value
     */
    @AfterReturning(pointcut = "controllerMethods() || serviceMethods()", returning = "result")
    public void logMethodExit(JoinPoint joinPoint, Object result) {
        String className = joinPoint.getSignature().getDeclaringTypeName();
        String methodName = joinPoint.getSignature().getName();

        log.debug("<<< Exiting: {}.{}() with result: {}",
                className, methodName, result);
    }

    /**
     * Log exceptions thrown by methods
     */
    @AfterThrowing(pointcut = "controllerMethods() || serviceMethods() || repositoryMethods()",
            throwing = "exception")
    public void logException(JoinPoint joinPoint, Throwable exception) {
        String className = joinPoint.getSignature().getDeclaringTypeName();
        String methodName = joinPoint.getSignature().getName();

        log.error("!!! Exception in: {}.{}() - Exception: {} - Message: {}",
                className, methodName, exception.getClass().getSimpleName(), exception.getMessage());
    }

    /**
     * Log method execution time
     */
    @Around("controllerMethods() || serviceMethods()")
    public Object logExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        String className = joinPoint.getSignature().getDeclaringTypeName();
        String methodName = joinPoint.getSignature().getName();

        long startTime = System.currentTimeMillis();

        Object result = joinPoint.proceed();

        long executionTime = System.currentTimeMillis() - startTime;

        if (executionTime > 1000) {
            log.warn("SLOW: {}.{}() took {}ms", className, methodName, executionTime);
        } else {
            log.debug("PERF: {}.{}() took {}ms", className, methodName, executionTime);
        }

        return result;
    }
}
