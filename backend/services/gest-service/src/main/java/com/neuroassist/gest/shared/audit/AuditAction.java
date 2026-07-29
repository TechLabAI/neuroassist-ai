package com.neuroassist.gest.shared.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller method as audit-sensitive. The string is the action token recorded in the
 * audit log (e.g. "CREATE_ORCAMENTO", "DELETE_CLIENTE").
 *
 * <p>Applied automatically by the module generator for methods whose resolved CRUD verb appears in
 * rbac.arch.json {@code enforcement.audit_required}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AuditAction {
    String value();
}
