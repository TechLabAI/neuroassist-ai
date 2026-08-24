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

        // E2E-029 — nenhuma autoridade sobre TENANT aqui. Um coordenador administra a própria
        // clínica; criar e reprocessar empresas é operação de plataforma, e vive no cliente de
        // serviço `platform-ops`. Enquanto estas três estiveram neste papel, o coordenador da
        // clínica A podia criar e reprocessar a clínica B.
        assertThat(authorities)
                .doesNotContain(
                        "acs.tenant.provisionar", "acs.tenant.read", "acs.tenant.reprocessar");

        // acs.vinculo.criar/.revogar saíram deste conjunto (2026-08-24): havia duas portas para
        // o mesmo vinculo e a do ACS nao escrevia nada no PAC. A coordenadora vincula pelo PAC.
        assertThat(authorities)
                .contains(
                        "acs.cuidador.criar",
                        "acs.cuidador.read",
                        "acs.cuidador.desativar",
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
                        "pac.paciente.read",
                        "pac.paciente.editar",
                        "pac.comando-authoring.criar",
                        "pac.comando-lifecycle.publicar",
                        // PAC propagates the caller token to GEST for the eligibility decision,
                        // which is taken on the caregiver's behalf
                        "gest.eligibility.read");
    }

    /**
     * A cuidadora edita o paciente, mas nao decide QUEM cuida dele nem cadastra paciente novo
     * (decisao de produto, 2026-08-24).
     *
     * <p>Este teste afirma <b>ausencia</b>, e e por isso que ele existe: as duas capacidades
     * chegavam pelo mesmo {@code PUT}, entao {@code pac.paciente.editar} concedia vinculo de
     * carona — bastava mandar outra lista no corpo. Uma cuidadora podia se vincular a qualquer
     * paciente da empresa. Sem esta assercao, devolver qualquer uma das duas ao papel passaria
     * despercebido.
     *
     * <p>E `pac.paciente.criar` saiu porque a cuidadora conseguia criar um paciente que nascia
     * descoberto e que ela propria nao conseguia assumir — nunca teve permissao de vincular.
     */
    @Test
    void cuidadora_naoCriaPacienteNemGerenciaVinculo() {
        List<String> authorities = policy.toAuthorities(CuidadorPapel.CUIDADORA);

        assertThat(authorities)
                .doesNotContain(
                        "pac.paciente.criar",
                        "pac.paciente.vincular",
                        "acs.vinculo.criar",
                        "acs.vinculo.revogar");
    }

    /**
     * A coordenadora cadastra paciente e decide quem cuida de quem — <b>pelo PAC</b>.
     *
     * <p>Ela NAO tem acs.vinculo.criar/.revogar. Havia duas portas para o mesmo vinculo, e a do
     * ACS nao escrevia nada no PAC: os dois lados divergiam em silencio (17 pacientes no dev, com
     * o inverso em zero). As duas authorities continuam existindo, mas so no cliente de servico
     * `pac-service`, que as usa para propagar. Uma entrada so, e o PAC continua sendo quem inicia.
     */
    @Test
    void coordenadora_vinculaPeloPacENaoDiretoNoAcs() {
        List<String> authorities = policy.toAuthorities(CuidadorPapel.COORDENADOR_CLINICA);

        assertThat(authorities).contains("pac.paciente.criar", "pac.paciente.vincular");
        assertThat(authorities)
                .as("nenhuma pessoa deve vincular direto no ACS — so o cliente de servico do PAC")
                .doesNotContain("acs.vinculo.criar", "acs.vinculo.revogar");
    }

    /** Nenhum papel HUMANO pode escrever vinculo direto no ACS. */
    @Test
    void nenhumPapelHumanoVinculaDiretoNoAcs() {
        for (CuidadorPapel papel : CuidadorPapel.values()) {
            assertThat(policy.toAuthorities(papel))
                    .as("papel %s nao pode ter authority de vinculo direto", papel)
                    .doesNotContain("acs.vinculo.criar", "acs.vinculo.revogar");
        }
    }

    /**
     * A authority que ATRAVESSA a invariante D-204 nao pode pertencer a pessoa nenhuma.
     *
     * <p>{@code acs.vinculo.revogar-apagamento} revoga todos os vinculos de um paciente sem
     * verificar cobertura — legitimo apenas para um paciente apagado, que nao produz alarme. E a
     * unica porta do sistema que passa por cima de uma invariante marcada SAFETY-CRITICAL, e existe
     * so no cliente de servico do pac-service.
     *
     * <p>Este teste afirma ausencia justamente porque conceder a alguem seria facil e silencioso.
     */
    @Test
    void authorityDeApagamentoNaoPertenceANenhumPapelHumano() {
        for (CuidadorPapel papel : CuidadorPapel.values()) {
            assertThat(policy.toAuthorities(papel))
                    .as(
                            "papel %s nao pode atravessar a invariante de cobertura (D-204)",
                            papel)
                    .doesNotContain("acs.vinculo.revogar-apagamento");
        }
    }

    /**
     * No caregiver role may write audit facts.
     *
     * <p>Audit facts are written by services, not humans: pac-service authenticates to hist-service
     * with a service identity ({@code POST /oauth2/token/service}), so the caregiver's token no
     * longer needs — and must not have — {@code hist:write}. Granting it here would let any
     * caregiver append an arbitrary fact to the audit ledger. See standards/service-identity.md.
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
                        "acs.directory.read",
                        "dash.panel-composition.read",
                        "hist:read",
                        // Ultimo degrau da escada (ADR-011): recebe o alarme, entao precisa poder
                        // confirma-lo. ACKNOWLEDGED e o unico caminho de saida de um alarme de
                        // ajuda, que nunca expira.
                        "evnt.event-confirmation.confirmar");
        assertThat(authorities)
                .as("confirmar alarme nao pode arrastar junto poder de escrita sobre nada")
                .doesNotContain(
                        "acs.tenant.read",
                        "acs.cuidador.read",
                        "acs.cuidador.criar",
                        "acs.vinculo.criar",
                        "hist:read:all",
                        "hist:write",
                        "pac.paciente.criar",
                        "pac.paciente.editar",
                        "pac.paciente.vincular",
                        "pac.comando-lifecycle.recalibrar",
                        "edge.manage");
    }
}
