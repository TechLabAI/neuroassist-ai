package com.neuroassist.e2e.auth;

import com.neuroassist.e2e.support.DevFixtures;
import com.neuroassist.e2e.support.E2EConfig;
import com.neuroassist.e2e.support.E2ETestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Isolamento multi-empresa na fronteira HTTP.
 *
 * <p>Estes são os cenários que valem mais que os positivos: um 200 prova que o caminho feliz
 * existe, mas só o 403 prova que existe uma parede. E o header é o vetor certo para testar — o
 * claim é assinado, o header não, então quem quiser ler o tenant do vizinho vai tentar por aqui.
 */
class TenantIsolationE2ETest extends E2ETestBase {

    /**
     * ISO-01..07 — header divergente do claim é recusado, nos SETE serviços.
     *
     * <p>O evnt-service não estava nesta lista: ele era o único que ignorava o header em vez de
     * conferi-lo. Não havia vazamento — o tenant sempre veio do claim assinado —, mas a política
     * divergia. Esta suíte encontrou a divergência e ela foi corrigida (E2E-021); a lista agora
     * cobre os sete, e é a lista que impede o buraco de voltar.
     */
    @ParameterizedTest(name = "ISO-01..07 — {0} recusa header de tenant divergente do claim")
    @CsvSource({
        "acs-service,  /api/v1/acs/directory/tenants/TENANT",
        "pac-service,  /api/pac/v1/pacientes",
        "gest-service, /gest-service/detection-profile/devices/DEVICE",
        "edge-service, /api/v1/edge/devices",
        "hist-service, /api/v1/hist/fact-types",
        "dash-service, /panel/summary",
        "evnt-service, /api/v1/events/active"
    })
    void headerDivergenteDoClaimE403(String service, String path) {
        authedWithTenant(AuthBackboneE2ETest.baseUriOf(service), DevFixtures.TENANT_INEXISTENTE)
                .get(resolve(path))
                .then()
                .statusCode(403);
    }

    @Test
    @DisplayName("ISO-07b — evnt-service com o header CORRETO segue respondendo normalmente")
    void evntComHeaderCorretoResponde() {
        // Contrapeso do parametrizado acima: recusar o header divergente nao pode ter recusado
        // tambem o caso legitimo. Um guarda que recusa tudo passaria no teste de cima.
        authed(E2EConfig.EVNT).get("/api/v1/events/active").then().statusCode(200);
    }

    @Test
    @DisplayName("ISO-08 — pac-service exige o header de tenant (400 missing-tenant)")
    void pacSemHeaderDeTenantE400() {
        authedWithTenant(E2EConfig.PAC, null)
                .get("/api/pac/v1/pacientes")
                .then()
                .statusCode(400)
                .body("error.code", org.hamcrest.Matchers.equalTo("missing-tenant"))
                .body("error.category", org.hamcrest.Matchers.equalTo("validation"));
    }

    @Test
    @DisplayName("ISO-09 — o 403 de tenant divergente traz o codigo tenant-mismatch, nao um 500")
    void tenantMismatchTemCodigoProprio() {
        authedWithTenant(E2EConfig.PAC, DevFixtures.TENANT_INEXISTENTE)
                .get("/api/pac/v1/pacientes")
                .then()
                .statusCode(403)
                .body("error.code", org.hamcrest.Matchers.equalTo("tenant-mismatch"))
                .body("error.category", org.hamcrest.Matchers.equalTo("forbidden"));
    }

    @Test
    @DisplayName("ISO-10 — dispositivo nao pareado nao rende perfil (403 device-not-paired)")
    void dispositivoNaoPareadoNaoRendePerfil() {
        authed(E2EConfig.GEST)
                .get("/gest-service/detection-profile/devices/" + DevFixtures.DEVICE_INEXISTENTE)
                .then()
                .statusCode(403)
                .body("error.code", org.hamcrest.Matchers.equalTo("device-not-paired"));
    }

    private static String resolve(String path) {
        return path.replace("TENANT", E2EConfig.TENANT).replace("DEVICE", DevFixtures.DEVICE);
    }
}
