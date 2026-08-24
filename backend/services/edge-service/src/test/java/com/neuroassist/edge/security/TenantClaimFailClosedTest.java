package com.neuroassist.edge.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.neuroassist.edge.infrastructure.security.TenantContextFilter;
import com.neuroassist.edge.infrastructure.security.TenantContextHolder;
import jakarta.servlet.FilterChain;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * E2E-021 — o filtro passa a recusar um token autenticado que não diz a que empresa pertence.
 *
 * <p>Antes ele seguia em frente sem ligar tenant nenhum, e a requisição morria adiante com {@code
 * relation ... does not exist} — porque o resolver cai em {@code neuroassist_global}, onde as
 * tabelas de negócio não existem. Não vazava dado, mas a segurança dependia dessa coincidência, e
 * era o que tornava perigoso o ACS parar de emitir os aliases de claim: quatro serviços liam
 * apenas um alias.
 */
class TenantClaimFailClosedTest {

    private final TenantContextFilter filter = new TenantContextFilter();

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        TenantContextHolder.clear();
    }

    private static void authenticate(Map<String, Object> claims) {
        Jwt jwt =
                Jwt.withTokenValue("t")
                        .header("alg", "RS256")
                        .claims(c -> c.putAll(claims))
                        .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    private FilterChainProbe run(Map<String, Object> claims) throws Exception {
        authenticate(claims);
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();
        AtomicBoolean chamou = new AtomicBoolean(false);
        FilterChain chain = (request, response) -> chamou.set(true);
        filter.doFilter(req, res, chain);
        return new FilterChainProbe(res, chamou.get());
    }

    private record FilterChainProbe(MockHttpServletResponse response, boolean chainInvocado) {}

    // ── o teste negativo que a issue pede ────────────────────────────────────

    @Test
    @DisplayName("token só com o alias antigo `tenant` é recusado com 403")
    void aliasAntigoTenant_recusado() throws Exception {
        FilterChainProbe probe = run(Map.of("sub", "u1", "tenant", "empresa-abc"));

        assertThat(probe.response().getStatus()).isEqualTo(403);
        assertThat(probe.response().getContentAsString()).contains("tenant_claim_missing");
        assertThat(probe.chainInvocado())
                .describedAs("a requisicao nao pode seguir sem tenant ligado")
                .isFalse();
    }

    @Test
    @DisplayName("token só com o alias antigo `empresaId` é recusado com 403")
    void aliasAntigoEmpresaId_recusado() throws Exception {
        FilterChainProbe probe = run(Map.of("sub", "u1", "empresaId", "empresa-abc"));

        assertThat(probe.response().getStatus()).isEqualTo(403);
        assertThat(probe.chainInvocado()).isFalse();
    }

    @Test
    @DisplayName("token de usuário sem claim de tenant nenhum é recusado com 403")
    void semClaimNenhum_recusado() throws Exception {
        FilterChainProbe probe = run(Map.of("sub", "u1", "token_type", "access"));

        assertThat(probe.response().getStatus()).isEqualTo(403);
        assertThat(probe.chainInvocado()).isFalse();
    }

    @Test
    @DisplayName("token de dispositivo sem tenant é recusado — o EDGE resolve o tenant no enroll")
    void tokenDeDispositivoSemTenant_recusado() throws Exception {
        FilterChainProbe probe = run(Map.of("sub", "edge-abc", "token_type", "device"));

        assertThat(probe.response().getStatus()).isEqualTo(403);
        assertThat(probe.chainInvocado()).isFalse();
    }

    // ── e o que NÃO pode ser recusado ────────────────────────────────────────

    @Test
    @DisplayName("claim canônico liga o tenant e segue")
    void claimCanonico_passa() throws Exception {
        FilterChainProbe probe =
                run(Map.of("sub", "u1", "token_type", "access", "tenant_id", "empresa-abc"));

        assertThat(probe.response().getStatus()).isEqualTo(200);
        assertThat(probe.chainInvocado()).isTrue();
    }

    @Test
    @DisplayName("token de SERVIÇO sem tenant passa — é uma capacidade concedida, não um descuido")
    void tokenDeServicoSemTenant_passa() throws Exception {
        // E2E-029: o cliente que cria empresas nao tem empresa a que se escopar, e o ACS so
        // concede token tenant-less a clientes explicitamente marcados. Fechar isto aqui quebraria
        // a criacao de empresa inteira.
        FilterChainProbe probe = run(Map.of("sub", "platform-ops", "token_type", "service"));

        assertThat(probe.response().getStatus()).isEqualTo(200);
        assertThat(probe.chainInvocado()).isTrue();
        assertThat(TenantContextHolder.getTenantIdOrNull()).isNull();
    }

    @Test
    @DisplayName("sem JWT nenhum o filtro não opina — quem decide é o SecurityConfig")
    void semJwt_passa() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();
        AtomicBoolean chamou = new AtomicBoolean(false);

        filter.doFilter(req, res, (request, response) -> chamou.set(true));

        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(chamou.get()).isTrue();
    }
}
