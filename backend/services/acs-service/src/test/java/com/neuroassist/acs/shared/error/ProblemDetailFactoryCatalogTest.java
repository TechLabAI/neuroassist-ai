package com.neuroassist.acs.shared.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Trava o catálogo de erros do ACS.
 *
 * <p>Contexto: o catálogo do {@link ProblemDetailFactory} nasceu vazio (o gerador não tinha módulos
 * pinados em {@code error-model.arch.json}), então TODO código caía no fallback {@code
 * internal_error / 500 / "Internal Server Error"}. O status HTTP não denunciava nada porque cada
 * handler chama {@code setStatus} depois — só o corpo mentia. Uma credencial inválida voltava com
 * {@code "status": 401} e {@code "category": "internal_error"} ao mesmo tempo.
 *
 * <p>Por que os testes antigos não pegaram: {@code GlobalExceptionHandlerTest} assertava {@code
 * status == 401} e {@code pd != null} — passava com a categoria errada. É a armadilha catalogada em
 * {@code plan-e2e-integration.md} §11: teste escrito a partir da suposição, não da resposta. Aqui a
 * asserção é sobre CATEGORIA e TÍTULO, que era o que estava errado.
 *
 * <p>Cada expectativa abaixo foi conferida contra a resposta real do acs-service rodando (curl em
 * {@code POST /oauth2/token} e endpoints protegidos), não contra o que se imagina que ele devolve.
 */
class ProblemDetailFactoryCatalogTest {

    /**
     * Catálogo esperado: código → categoria, status, título.
     *
     * <p>Este mapa é o espelho independente do mapa de produção. Duplicação deliberada: se fosse
     * derivado de {@code ProblemDetailFactory}, o teste concordaria com qualquer valor, inclusive
     * com o errado.
     */
    private static Map<String, Object[]> expectedCatalog() {
        Map<String, Object[]> m = new LinkedHashMap<>();
        m.put("acs.auth.invalid-credentials", new Object[] {"auth", 401, "Unauthorized"});
        m.put("acs.auth.unauthenticated", new Object[] {"auth", 401, "Unauthorized"});
        m.put("acs.auth-unauthenticated", new Object[] {"auth", 401, "Unauthorized"});
        m.put("acs.auth.insufficient-authority", new Object[] {"forbidden", 403, "Forbidden"});
        m.put("acs.tenant.mismatch", new Object[] {"forbidden", 403, "Tenant Mismatch"});
        m.put(
                "acs.validation.invalid-request",
                new Object[] {"validation", 400, "Validation Failed"});
        m.put(
                "acs.validation.invalid-value",
                new Object[] {"validation", 400, "Validation Failed"});
        m.put(
                "acs.auth.invalid-token-type",
                new Object[] {"validation", 400, "Invalid Token Type"});
        m.put("acs.tenant.not-found", new Object[] {"not_found", 404, "Tenant Not Found"});
        m.put("acs.cuidador.not-found", new Object[] {"not_found", 404, "Caregiver Not Found"});
        m.put("acs.tenant-suspended", new Object[] {"conflict", 409, "Tenant Suspended"});
        m.put("acs.tenant-not-active", new Object[] {"conflict", 409, "Tenant Not Active"});
        m.put("acs.tenant.already-seeded", new Object[] {"conflict", 409, "Tenant Already Seeded"});
        m.put(
                "acs.cuidador.login-duplicate",
                new Object[] {"conflict", 409, "Login Already Exists"});
        m.put(
                "acs.link.last-caregiver-link",
                new Object[] {"conflict", 409, "Last Caregiver Link"});
        m.put(
                "acs.link.cross-tenant-link",
                new Object[] {"business_rule", 422, "Cross-Tenant Link"});
        m.put("acs.link.unknown-patient", new Object[] {"business_rule", 422, "Unknown Patient"});
        m.put(
                "acs.upstream-unavailable",
                new Object[] {"upstream_error", 502, "Upstream Unavailable"});
        m.put(
                "acs.tenant.provisioning-timeout",
                new Object[] {"upstream_error", 504, "Provisioning Timeout"});
        m.put(
                "acs.signing-key.no-active-key",
                new Object[] {"internal_error", 500, "Internal Server Error"});
        return m;
    }

    static List<String> expectedCodes() {
        return List.copyOf(expectedCatalog().keySet());
    }

    // ------------------------------------------------------------------
    // 1. Cada código catalogado tem categoria e título corretos
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "{0}")
    @MethodSource("expectedCodes")
    @DisplayName("código catalogado resolve para a categoria, status e título esperados")
    void codeResolvesToExpectedMetadata(String code) {
        Object[] expected = expectedCatalog().get(code);

        assertThat(ProblemDetailFactory.categoryOf(code))
                .as(
                        "categoria de %s — internal_error aqui significa que o código caiu no fallback",
                        code)
                .isEqualTo(expected[0]);
        assertThat(ProblemDetailFactory.httpStatusOf(code))
                .as("status catalogado de %s", code)
                .isEqualTo(expected[1]);
        assertThat(ProblemDetailFactory.titleOf(code))
                .as("título de %s", code)
                .isEqualTo(expected[2]);
    }

    // ------------------------------------------------------------------
    // 2. Nenhum código órfão nos dois sentidos
    // ------------------------------------------------------------------

    @Test
    @DisplayName(
            "catálogo de produção contém exatamente os códigos esperados (sem faltas nem entrada morta)")
    void catalogHasNoOrphansInEitherDirection() {
        assertThat(ProblemDetailFactory.catalogedCodes())
                .as(
                        "código a mais = entrada morta; código a menos = cai no fallback"
                                + " internal_error/500 e ninguém percebe até ler a resposta")
                .containsExactlyInAnyOrderElementsOf(expectedCatalog().keySet());
    }

    // ------------------------------------------------------------------
    // 3. Categoria nunca contradiz o status
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "{0}")
    @MethodSource("expectedCodes")
    @DisplayName("status catalogado é válido para a categoria (error-model.arch.json)")
    void categoryAndStatusAreConsistent(String code) {
        String category = ProblemDetailFactory.categoryOf(code);
        int status = ProblemDetailFactory.httpStatusOf(code);

        assertThat(ProblemDetailFactory.knownCategories())
                .as("categoria %s não existe no modelo de erros", category)
                .contains(category);
        assertThat(ProblemDetailFactory.validStatusesForCategory(category))
                .as(
                        "%s: categoria %s não admite status %d — foi exatamente o defeito relatado",
                        code, category, status)
                .contains(status);
    }

    // ------------------------------------------------------------------
    // 4. Toda exceção de produção tem entrada no catálogo
    // ------------------------------------------------------------------

    static List<CodedException> productionExceptions() {
        UUID id = UUID.randomUUID();
        return List.of(
                new InvalidCredentialsException(),
                new InvalidTokenTypeException(),
                new TenantNotFoundException(id),
                new TenantSuspendedException(id),
                new TenantNotActiveException(id, "PROVISIONING"),
                new TenantAlreadySeededException(id),
                new TenantProvisioningTimeoutException(id),
                new UpstreamUnavailableException("mig-service", new RuntimeException("down")),
                new CaregiverNotFoundException(id),
                new LoginDuplicateException("alguem@x.com", id),
                new LastCaregiverLinkException(id),
                new CrossTenantLinkException(id, id),
                new UnknownPatientException(id),
                new TenantMismatchException(id, id),
                new NoActiveSigningKeyException());
    }

    @ParameterizedTest
    @MethodSource("productionExceptions")
    @DisplayName("exceção lançada em produção tem código catalogado (não cai no fallback)")
    void everyProductionExceptionCodeIsCataloged(CodedException ex) {
        assertThat(ProblemDetailFactory.catalogedCodes())
                .as(
                        "%s lança %s, que não está no catálogo — a resposta sairia com"
                                + " category=internal_error",
                        ex.getClass().getSimpleName(), ex.code())
                .contains(ex.code());
    }

    // ------------------------------------------------------------------
    // 5. Segurança: opacidade do 401 preservada
    // ------------------------------------------------------------------

    @Test
    @DisplayName("todo 401 de autenticação sai com o mesmo título opaco")
    void allAuthFailuresShareTheSameOpaqueTitle() {
        // ServiceTokenService responde o MESMO 401 para cliente desconhecido, segredo errado,
        // cliente desabilitado e tenant inativo. Um título específico por motivo reintroduziria
        // por outro campo a distinção que o código evita.
        List<String> authCodes =
                List.of(
                        "acs.auth.invalid-credentials",
                        "acs.auth.unauthenticated",
                        "acs.auth-unauthenticated");
        assertThat(authCodes)
                .allSatisfy(
                        code -> {
                            assertThat(ProblemDetailFactory.titleOf(code))
                                    .isEqualTo("Unauthorized");
                            assertThat(ProblemDetailFactory.categoryOf(code)).isEqualTo("auth");
                        });
    }

    // ------------------------------------------------------------------
    // 6. Fallback continua existindo para código desconhecido
    // ------------------------------------------------------------------

    @Test
    @DisplayName("código desconhecido ainda cai no fallback internal_error/500")
    void unknownCodeStillFallsBackToInternalError() {
        var pd = ProblemDetailFactory.forCode("acs.nao.existe", new RuntimeException("x"), null);

        assertThat(pd.getStatus()).isEqualTo(500);
        assertThat(pd.getTitle()).isEqualTo("Internal Server Error");
        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) pd.getProperties().get("error");
        assertThat(error).containsEntry("category", "internal_error");
        assertThat(error).containsEntry("code", "acs.nao.existe");
    }
}
