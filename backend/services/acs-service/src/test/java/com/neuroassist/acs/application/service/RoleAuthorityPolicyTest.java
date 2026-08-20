package com.neuroassist.acs.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.neuroassist.acs.domain.model.CuidadorPapel;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for RoleAuthorityPolicy (ACS-005). Verifies 100% coverage of all CuidadorPapel
 * mappings.
 */
class RoleAuthorityPolicyTest {

    private final RoleAuthorityPolicy policy = new RoleAuthorityPolicy();

    @Test
    void coordenadorClinica_returns11Authorities() {
        List<String> authorities = policy.toAuthorities(CuidadorPapel.COORDENADOR_CLINICA);

        assertThat(authorities).hasSize(11);
        assertThat(authorities)
                .containsExactlyInAnyOrder(
                        "acs.tenant.provisionar",
                        "acs.tenant.read",
                        "acs.tenant.reprocessar",
                        "acs.cuidador.criar",
                        "acs.cuidador.read",
                        "acs.cuidador.desativar",
                        "acs.vinculo.criar",
                        "acs.vinculo.revogar",
                        "acs.directory.read",
                        "evnt.read",
                        "hist.read");
    }

    @Test
    void cuidadora_returns5Authorities() {
        List<String> authorities = policy.toAuthorities(CuidadorPapel.CUIDADORA);

        assertThat(authorities).hasSize(5);
        assertThat(authorities)
                .containsExactlyInAnyOrder(
                        "acs.cuidador.read",
                        "acs.directory.read",
                        "evnt.read",
                        "evnt.confirm",
                        "dash.read");
    }

    @Test
    void pacienteApoio_returns2Authorities() {
        List<String> authorities = policy.toAuthorities(CuidadorPapel.PACIENTE_APOIO);

        assertThat(authorities).hasSize(2);
        assertThat(authorities).containsExactlyInAnyOrder("acs.directory.read", "dash.read");
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
                        "acs.tenant.create",
                        "acs.cuidador.create",
                        "acs.cuidador.deactivate",
                        "acs.link.create",
                        "acs.link.revoke",
                        "hist.read");
    }

    @Test
    void pacienteApoio_hasOnlyViewAuthorities() {
        List<String> authorities = policy.toAuthorities(CuidadorPapel.PACIENTE_APOIO);

        assertThat(authorities)
                .doesNotContain(
                        "acs.tenant.read",
                        "acs.cuidador.read",
                        "evnt.read",
                        "evnt.confirm",
                        "hist.read");
    }
}
