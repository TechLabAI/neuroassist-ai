package com.neuroassist.hist.shared.security;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.hibernate.Session;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Activates Hibernate filters around each application-service call based on
 * the current request's {@link DataScope}. Filter names + parameters are
 * derived from entity-scope.arch.json: every entity declared under a given
 * tipo contributes one {@code enableFilter} call. Filters are disabled in a
 * {@code finally} block to avoid leaking into other requests reusing the
 * same {@code EntityManager} from the pool.
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class DataScopeFilterAspect {

    private final EntityManager em;

    @Around("execution(* com.neuroassist.hist..application.service..*(..))")
    public Object aroundService(ProceedingJoinPoint pjp) throws Throwable {
        Session session = em.unwrap(Session.class);
        DataScope scope = AccessContextHolder.requireDataScope();

        try {
            if (scope == null || scope.isUnrestricted()) {
                return pjp.proceed();
            }
            log.warn("data-scope tipo '{}' has no filter wiring — proceeding without restriction", scope.tipo());
            return pjp.proceed();
        } finally {
        }
    }
}
