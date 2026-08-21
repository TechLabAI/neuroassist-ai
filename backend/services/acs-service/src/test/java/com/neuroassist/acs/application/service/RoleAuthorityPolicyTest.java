package com.neuroassist.acs.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.neuroassist.acs.domain.model.CuidadorPapel;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for RoleAuthorityPolicy (ACS-005). Verifies 100% coverage of all CuidadorPapel
 * mappings.
 *
 * <p>Since E2E-003 the map carries the cross-service authorities the downstream resource servers
 * actually enforce (PAC, GEST, EDGE, DASH, HIST), so the assertions below check the invariants of
 * each role — the ACS admin surface, the HIST scope form, the privilege ordering — rather than an
 * exact-size snapshot that has to be rewritten whenever a downstream endpoint is added.
 */
class RoleAuthorityPolicyTest {

    private final RoleAuthorityPolicy policy = new RoleAuthorityPolicy();

    @Test
    void coordenadorClinica_hasFullAcsAdminSurface() {
        List<String> authorities = policy.toAuthorities(CuidadorPapel.COORDENADOR_CLINICA);

        assertThat(authorities)
                .contains(
                        "acs.tenant.provisionar",
                        "acs.tenant.read",
                        "acs.tenant.reprocessar",
                        "acs.cuidador.criar",
                        "acs.cuidador.read",
                        "acs.cuidador.desativar",
                        "acs.vinculo.criar",
                        "acs.vinculo.revogar",
                        "acs.directory.read");
    }

    @Test
    void coordenadorClinica_hasTenantWideHistoryScope() {
        List<String> authorities = policy.toAuthorities(CuidadorPapel.COORDENADOR_CLINICA);

        // HIST enforces the colon form with the SCOPE_ prefix — hist.read would silently 403
        assertThat(authorities).contains("hist:read", "hist:read:all");
    }

    @Test
    void cuidadora_hasPatientAndCommandAuthoring() {
        List<String> authorities = policy.toAuthorities(CuidadorPapel.CUIDADORA);

        assertThat(authorities)
                .contains(
                        "ROLE_CAREGIVER",
                        "pac.paciente.criar",
                        "pac.paciente.read",
                        "pac.paciente.editar",
                        "pac.comando-authoring.criar",
                        "pac.comando-lifecycle.publicar",
                        // PAC propagates the caller token to GEST for the eligibility decision,
                        // which is taken on the caregiver's behalf
                        "gest.eligibility.read");
    }

    /**
     * No caregiver role may write audit facts.
     *
     * <p>Audit facts are written by services, not humans: pac-service authenticates to
     * hist-service with a service identity ({@code POST /oauth2/token/service}), so the
     * caregiver's token no longer needs — and must not have — {@code hist:write}. Granting it
     * here would let any caregiver append an arbitrary fact to the audit ledger. See
     * standards/service-identity.md.
     */
    @Test
    void noRoleCanWriteAuditFacts() {
        for (CuidadorPapel papel : CuidadorPapel.values()) {
            assertThat(policy.toAuthorities(papel))
                    .as("papel=%s must not be able to write audit facts", papel)
                    .doesNotContain("hist:write");
        }
    }

    @Test
    void allPapelMappingsAreDefined() {
        // All CuidadorPapel enum values must have a mapping — no IllegalStateException
        for (CuidadorPapel papel : CuidadorPapel.values()) {
            List<String> authorities = policy.toAuthorities(papel);
            assertThat(authorities)
                    .as("Authorities for papel=%s must not be empty", papel)
                    .isNotEmpty();
        }
    }

    @Test
    void cuidadora_doesNotHaveAdminAuthorities() {
        List<String> authorities = policy.toAuthorities(CuidadorPapel.CUIDADORA);

        assertThat(authorities)
                .doesNotContain(
                        "acs.tenant.provisionar",
                        "acs.tenant.reprocessar",
                        "acs.cuidador.criar",
                        "acs.cuidador.desativar",
                        "acs.vinculo.criar",
                        "acs.vinculo.revogar",
                        // tenant-wide history is the coordinator's privilege only
                        "hist:read:all");
    }

    @Test
    void pacienteApoio_hasOnlyViewAuthorities() {
        List<String> authorities = policy.toAuthorities(CuidadorPapel.PACIENTE_APOIO);

        assertThat(authorities)
                .containsExactlyInAnyOrder(
                        "acs.directory.read", "dash.panel-composition.read", "hist:read");
        assertThat(authorities)
                .doesNotContain(
                        "acs.tenant.read",
                        "acs.cuidador.read",
                        "hist:read:all",
                        "hist:write",
                        "pac.paciente.criar",
                        "pac.paciente.editar");
    }
}
