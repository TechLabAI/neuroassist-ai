package com.neuroassist.pac.infrastructure.clients;

import com.neuroassist.pac.application.port.out.AcsDirectoryPort;
import com.neuroassist.pac.domain.model.CuidadorRef;
import com.neuroassist.pac.domain.model.EmpresaRef;
import com.neuroassist.pac.shared.error.CodedException;
import feign.FeignException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * INT-PAC-002 — Adapter implementing {@link AcsDirectoryPort} via {@link AcsDirectoryClient}.
 *
 * <p>Hexagonal rules:
 *
 * <ul>
 *   <li>RULE-03: no {@code @Transactional} here — boundary is in application.service.
 *   <li>RULE-06: @FeignClient is on the client interface, not this adapter.
 *   <li>RULE-04/05: no api.* cross-dependency.
 * </ul>
 *
 * <p>Failure semantics per SPEC-10 §5.3:
 *
 * <ul>
 *   <li>404 from ACS → {@code CodedException("pac.cuidador.not-found")} → HTTP 422.
 *   <li>Any other Feign exception → {@code CodedException("acs.unavailable")} → HTTP 503.
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AcsDirectoryAdapter implements AcsDirectoryPort {

    private final AcsDirectoryClient acsDirectoryClient;

    @Override
    public EmpresaRef resolveTenant(UUID empresaId) {
        try {
            AcsEmpresaResponse resp = acsDirectoryClient.getEmpresa(empresaId);
            return new EmpresaRef(resp.tenantId(), resp.valid());
        } catch (FeignException.NotFound e) {
            log.warn("Empresa not found in ACS empresaId={}", empresaId);
            throw new CodedException(
                    "pac.cuidador.not-found", "Empresa not found in ACS: " + empresaId);
        } catch (FeignException e) {
            log.error("ACS unavailable while resolving empresa={}", empresaId, e);
            throw new CodedException(
                    "acs.unavailable", "ACS service unavailable — cannot resolve empresa");
        }
    }

    @Override
    public CuidadorRef resolveCuidador(UUID cuidadorId) {
        try {
            AcsCuidadorResponse resp = acsDirectoryClient.getCuidador(cuidadorId);
            return new CuidadorRef(resp.id(), resp.tenant());
        } catch (FeignException.NotFound e) {
            log.warn("Cuidador not found in ACS cuidadorId={}", cuidadorId);
            throw new CodedException(
                    "pac.cuidador.not-found", "Caregiver not found in ACS: " + cuidadorId);
        } catch (FeignException e) {
            log.error("ACS unavailable while resolving cuidador={}", cuidadorId, e);
            throw new CodedException(
                    "acs.unavailable", "ACS service unavailable — cannot resolve cuidador");
        }
    }
}
