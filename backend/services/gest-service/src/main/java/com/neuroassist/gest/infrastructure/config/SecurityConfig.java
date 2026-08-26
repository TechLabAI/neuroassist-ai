package com.neuroassist.gest.infrastructure.config;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * OAuth2 JWT Resource Server security configuration.
 *
 * <p>gest-service is a real resource server: {@code @EnableMethodSecurity} activates the
 * {@code @PreAuthorize} annotations on the controllers and the chain ends in {@code
 * anyRequest().authenticated()} — only the actuator probes stay public. Before this, the chain
 * ended in {@code anyRequest().permitAll()} and method security was off, so every
 * {@code @PreAuthorize} was inert and PAC → GEST / EDGE → GEST calls succeeded through the
 * *absence* of authorization rather than through correct authorization.
 *
 * <p><b>Bearer JWT e a unica autenticacao.</b> Os tokens sao emitidos pelo acs-service e as
 * authorities saem da uniao dos claims {@code scope} e {@code authorities}, com prefixo vazio, de
 * modo que {@code hasAuthority('gest.tipo-gesto.read')} case com o valor do claim ao pe da letra
 * (E2E-003 / C-1). A decodificacao tambem alimenta o {@link
 * com.neuroassist.gest.infrastructure.security.TenantContextFilter}, que le {@code tenant_id}
 * (E2E-009 / C-14).
 *
 * <p><b>Por que o HTTP Basic saiu.</b> Existia aqui um {@code httpBasic()} com dois usuarios em
 * memoria ({@code gest-manager} / {@code gest-reader}) e senha em texto plano, sem {@code @Profile}
 * nem {@code @ConditionalOnProperty} — valia em todo ambiente. O proprio javadoc anterior admitia a
 * razao: os cenarios de RBAC do {@code tipo-gesto-lifecycle.feature} autenticavam por ali. Ou seja,
 * uma superficie de autenticacao de producao existia para servir a suite de teste, e {@code
 * gest-manager} carregava {@code gest.detection-profile.publicar} — publicar o perfil de deteccao
 * da empresa inteira com uma senha versionada no repositorio.
 *
 * <p>O GEST era o unico dos sete servicos com Basic. Com ele fora, o {@code TenantContextFilter}
 * volta a cobrir 100% dos chamadores autenticados: todo caller ou traz um JWT com {@code
 * tenant_id}, ou e um token de servico declarado, ou e anonimo e para nas regras acima. Nenhum
 * chamador autenticado chega ao JDBC sem tenant ligado.
 *
 * <p>A suite BDD passou a assinar JWTs de teste ({@code TestJwtFactory}), preservando a distincao
 * de authorities que os dois usuarios em memoria representavam.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers("/actuator/**")
                                        .permitAll()
                                        // Filter-level enforcement mirrors the @PreAuthorize on the
                                        // controller (defense in depth; reliable in every test
                                        // context, including @WebMvcTest slices).
                                        .requestMatchers(
                                                HttpMethod.POST, "/gest-service/tipo-gesto")
                                        .hasAuthority("gest.tipo-gesto.criar")
                                        .requestMatchers(
                                                HttpMethod.POST,
                                                "/gest-service/tipo-gesto/*/transitions")
                                        .hasAuthority("gest.tipo-gesto.editar")
                                        .anyRequest()
                                        .authenticated())
                .oauth2ResourceServer(
                        oauth2 ->
                                oauth2.jwt(
                                        jwt ->
                                                jwt.jwtAuthenticationConverter(
                                                        jwtAuthenticationConverter())))
                .exceptionHandling(
                        ex ->
                                ex.accessDeniedHandler(
                                        (req, res, e) -> {
                                            res.setStatus(403);
                                            res.setContentType("application/problem+json");
                                            res.getWriter()
                                                    .write(
                                                            "{\"type\":\"/errors/insufficient-scope\","
                                                                    + "\"title\":\"Forbidden\","
                                                                    + "\"status\":403,"
                                                                    + "\"detail\":\"Insufficient scope for this operation\"}");
                                        }))
                .csrf(AbstractHttpConfigurer::disable);
        return http.build();
    }

    /**
     * Maps JWT claims to authorities, reading the union of the standard {@code scope} claim and the
     * {@code authorities} claim that acs-service actually populates (E2E-003 / C-1).
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter scopeConverter = new JwtGrantedAuthoritiesConverter();
        scopeConverter.setAuthorityPrefix("");

        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthorityPrefix("");
        authoritiesConverter.setAuthoritiesClaimName("authorities");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(
                jwt -> {
                    Collection<GrantedAuthority> merged = new LinkedHashSet<>();
                    merged.addAll(scopeConverter.convert(jwt));
                    merged.addAll(authoritiesConverter.convert(jwt));
                    return merged;
                });
        return converter;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
