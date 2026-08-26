package com.neuroassist.pac.infrastructure.security;

import com.neuroassist.pac.application.port.out.CallerAuthoritiesPort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Adapter de {@link CallerAuthoritiesPort} sobre o {@code SecurityContext} do Spring.
 *
 * <p>Compara pelo nome exato, sem prefixo e sem normalização: as authorities deste sistema são
 * emitidas pelo ACS no claim {@code authorities} com prefixo vazio, e "quase igual" aqui seria uma
 * permissão concedida por engano.
 *
 * <p>Sem chamador autenticado devolve {@code false} — falha fechada.
 */
@Component
public class SpringSecurityCallerAuthoritiesAdapter implements CallerAuthoritiesPort {

    @Override
    public boolean hasAuthority(String authority) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority::equals);
    }
}
