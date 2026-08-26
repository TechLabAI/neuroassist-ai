package com.neuroassist.acs.application.service;

import com.neuroassist.acs.domain.model.CuidadorPapel;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Defines the authority set for each CuidadorPapel. Used by TokenIssuer to populate the
 * "authorities" claim in the issued JWT.
 *
 * <p>Authority naming: {@code <service>.<resource>.<action>} for PAC/GEST/EDGE/DASH/EVNT/ACS, and
 * the colon form {@code hist:<action>} for HIST (which enforces it with the {@code SCOPE_} prefix).
 * Every value below matches a literal {@code @PreAuthorize} / SecurityConfig rule in the owning
 * service — a mismatch means a silent 403 at runtime (E2E-003).
 *
 * <p>{@code hist:write} is deliberately absent: audit facts are written by services, not by humans.
 * The producers (PAC today, EVNT/ACS once their clients exist) authenticate to hist-service with a
 * service identity — see standards/service-identity.md. Granting it here would let any caregiver
 * append an arbitrary audit fact.
 */
@Component
public class RoleAuthorityPolicy {

    private static final Map<CuidadorPapel, List<String>> AUTHORITY_MAP =
            Map.of(
                    CuidadorPapel.COORDENADOR_CLINICA,
                    List.of(
                            "ROLE_CAREGIVER",
                            // acs.tenant.provisionar / .read / .reprocessar saíram daqui
                            // (E2E-029). Um coordenador de clínica podia criar e reprocessar
                            // OUTRAS empresas — não porque o negócio quisesse, mas porque era o
                            // único caminho existente. Onboarding é operação de plataforma:
                            // as autoridades vivem agora no cliente de serviço `platform-ops`,
                            // que obtém um token sem tenant (não há tenant antes de criá-lo).
                            "acs.cuidador.criar",
                            "acs.cuidador.read",
                            "acs.cuidador.editar",
                            "acs.cuidador.desativar",
                            // acs.vinculo.criar / .revogar SAIRAM daqui.
                            //
                            // Havia DUAS portas para criar um vinculo cuidadora-paciente: pelo PAC
                            // (que grava a projecao local e propaga ao ACS) e direto neste
                            // endpoint. A segunda nao escreve nada no PAC, entao os dois lados
                            // divergiam em silencio — no ambiente de desenvolvimento isso produziu
                            // 17 pacientes com vinculo ATIVO no ACS e nenhuma linha no PAC, com o
                            // inverso em zero, o que fixa a direcao do vazamento.
                            //
                            // O sintoma pratico: a lista de pacientes sem cuidadora le a projecao
                            // do PAC e mostrava como descoberto quem entrou pela porta do ACS.
                            //
                            // Agora a unica pessoa que vincula e a coordenadora, e ela vincula
                            // PELO PAC (pac.paciente.vincular). Estas duas authorities continuam
                            // existindo, mas so no cliente de servico `pac-service`, que as usa
                            // para propagar — ver acs.service-clients em application.yml. Uma
                            // entrada so, e o PAC continua sendo quem inicia.
                            "acs.directory.read",
                            // Mudar QUEM cuida de quem e da coordenadora. Distinta de
                            // pac.paciente.editar de proposito: editar e vincular chegam pelo
                            // mesmo PUT, e sem separa-las dar "editar" a cuidadora daria junto o
                            // poder de se vincular a qualquer paciente da empresa.
                            "pac.paciente.vincular",
                            "pac.paciente.criar",
                            "pac.paciente.read",
                            "pac.paciente.editar",
                            // Apagar dados pessoais (LGPD) é irreversível e só o coordenador
                            // responde por ele — deliberadamente ausente do papel CUIDADORA.
                            "pac.paciente.deletar",
                            "pac.catalogo-ativo.read",
                            "pac.comando-authoring.criar",
                            "pac.comando-authoring.editar",
                            "pac.comando-lifecycle.read",
                            "pac.comando-lifecycle.publicar",
                            "pac.comando-lifecycle.aposentar",
                            "pac.comando-lifecycle.recalibrar",
                            "gest.tipo-gesto.read",
                            "gest.tipo-gesto.criar",
                            "gest.tipo-gesto.editar",
                            "gest.detection-profile.read",
                            "gest.detection-profile.publicar",
                            "gest.detection-contract.read",
                            "gest.detection-contract.validar",
                            "gest.eligibility.read",
                            "edge.read",
                            "edge.manage",
                            "evnt.event-query.read",
                            "evnt.event-confirmation.confirmar",
                            "dash.panel-composition.read",
                            "hist:read",
                            "hist:read:all"),
                    CuidadorPapel.CUIDADORA,
                    List.of(
                            "ROLE_CAREGIVER",
                            "acs.cuidador.read",
                            "acs.directory.read",
                            // pac.paciente.criar saiu daqui: cadastrar paciente e da coordenadora.
                            // A cuidadora conseguia criar um paciente que nascia DESCOBERTO e que
                            // ela propria nao conseguia assumir, porque nunca teve permissao de
                            // vincular. Editar ela mantem — mas editar deixou de conceder vinculo
                            // (ver pac.paciente.vincular).
                            "pac.paciente.read",
                            "pac.paciente.editar",
                            "pac.catalogo-ativo.read",
                            "pac.comando-authoring.criar",
                            "pac.comando-authoring.editar",
                            "pac.comando-lifecycle.read",
                            "pac.comando-lifecycle.publicar",
                            "pac.comando-lifecycle.aposentar",
                            "pac.comando-lifecycle.recalibrar",
                            "gest.tipo-gesto.read",
                            "gest.detection-profile.read",
                            "gest.detection-contract.read",
                            "gest.detection-contract.validar",
                            "gest.eligibility.read",
                            "edge.read",
                            "evnt.event-query.read",
                            "evnt.event-confirmation.confirmar",
                            "dash.panel-composition.read",
                            "hist:read"),
                    CuidadorPapel.PACIENTE_APOIO,
                    List.of(
                            "acs.directory.read",
                            "dash.panel-composition.read",
                            "hist:read",
                            // O apoio e o ULTIMO degrau da escada de escalonamento (ADR-011):
                            // quando nenhuma cuidadora e nem a coordenadora atendem, o alarme
                            // chega a ele. Sem esta authority ele receberia o pedido e nao
                            // conseguiria responder — e ACKNOWLEDGED e o unico caminho de saida
                            // de um alarme de ajuda, que por decisao clinica nunca expira. O
                            // ultimo degrau seria um beco sem saida, e o alarme ficaria na fila
                            // para sempre com alguem olhando para ele sem poder fecha-lo.
                            //
                            // Note o que ele NAO ganhou: continua sem qualquer autoridade de
                            // escrita sobre paciente, comando, cuidadora ou dispositivo. Poder
                            // dizer "eu vi e vou atender" nao e poder configurar nada.
                            "evnt.event-confirmation.confirmar",
                            // Receber o alarme e nao conseguir LISTA-LO e o mesmo beco sem saida
                            // de outra forma: confirmar exige ver. Verificado ao vivo — sem esta
                            // authority o apoio levava erro em GET /api/v1/events/active enquanto
                            // a cuidadora recebia 200.
                            "evnt.event-query.read"));

    /**
     * Returns the list of fine-grained authorities for the given role.
     *
     * @param papel the caregiver role
     * @return immutable list of authority strings
     * @throws IllegalStateException if the papel is not mapped (misconfiguration)
     */
    public List<String> toAuthorities(CuidadorPapel papel) {
        List<String> authorities = AUTHORITY_MAP.get(papel);
        if (authorities == null) {
            throw new IllegalStateException(
                    "No authority mapping found for papel: "
                            + papel
                            + " — update RoleAuthorityPolicy.AUTHORITY_MAP");
        }
        return authorities;
    }
}
