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
                            "acs.tenant.provisionar",
                            "acs.tenant.read",
                            "acs.tenant.reprocessar",
                            "acs.cuidador.criar",
                            "acs.cuidador.read",
                            "acs.cuidador.desativar",
                            "acs.vinculo.criar",
                            "acs.vinculo.revogar",
                            "acs.directory.read",
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
                            "pac.paciente.criar",
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
                            "gest.eligibility.read",
                            "edge.read",
                            "evnt.event-query.read",
                            "evnt.event-confirmation.confirmar",
                            "dash.panel-composition.read",
                            "hist:read"),
                    CuidadorPapel.PACIENTE_APOIO,
                    List.of("acs.directory.read", "dash.panel-composition.read", "hist:read"));

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
