package com.neuroassist.acs.application.service;

import com.neuroassist.acs.domain.model.CuidadorPapel;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Defines the authority set for each CuidadorPapel. Used by TokenIssuer to populate the
 * "authorities" claim in the issued JWT.
 *
 * <p>Authority naming: {@code <service>.<resource>.<action>} Authorities are validated
 * by @PreAuthorize on each controller endpoint.
 */
@Component
public class RoleAuthorityPolicy {

    private static final Map<CuidadorPapel, List<String>> AUTHORITY_MAP =
            Map.of(
                    CuidadorPapel.COORDENADOR_CLINICA,
                    List.of(
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
                            "hist.read"),
                    CuidadorPapel.CUIDADORA,
                    List.of(
                            "acs.cuidador.read",
                            "acs.directory.read",
                            "evnt.read",
                            "evnt.confirm",
                            "dash.read"),
                    CuidadorPapel.PACIENTE_APOIO,
                    List.of("acs.directory.read", "dash.read"));

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
