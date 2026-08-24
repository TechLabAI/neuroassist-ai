package com.neuroassist.dash.api.controller;

import com.neuroassist.dash.api.dto.CalibrationProxyRequest;
import com.neuroassist.dash.api.dto.CalibrationProxyResponse;
import com.neuroassist.dash.application.port.in.CalibrationProxyUseCase;
import com.neuroassist.dash.domain.model.CalibrationParams;
import com.neuroassist.dash.domain.model.CalibrationResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

/** Write-through de calibracao para o PAC. Superficie /panel/** (JWT do cuidador). */
@Slf4j
@RestController
@RequestMapping("/panel")
@RequiredArgsConstructor
public class CalibrationProxyController {

    private final CalibrationProxyUseCase calibrationProxyUseCase;

    /** POST /panel/calibration Envia calibracao para o PAC e retorna eco do resultado. */
    @PostMapping("/calibration")
    @PreAuthorize("hasAuthority('ROLE_CAREGIVER')")
    public ResponseEntity<CalibrationProxyResponse> calibrate(
            @RequestParam String patientId,
            @RequestParam String commandId,
            @RequestBody @Valid CalibrationProxyRequest request,
            Authentication auth) {

        String caregiverId = auth.getName();
        String tenantId = extractTenantId(auth);

        CalibrationParams params =
                new CalibrationParams(
                        request.urgencia(),
                        request.sensibilidade(),
                        request.tempoMinGestoMs(),
                        request.tempoMaxGestoMs());

        CalibrationResult result =
                calibrationProxyUseCase.calibrate(
                        caregiverId, patientId, commandId, params, tenantId);

        CalibrationProxyResponse response =
                new CalibrationProxyResponse(
                        result.comandoId(),
                        result.sensibilidade(),
                        result.urgencia(),
                        result.estado(),
                        result.calibracaoVersao(),
                        result.warnings());

        return ResponseEntity.ok(response);
    }

    /**
     * Le o tenant do claim canonico {@code tenant_id} (E2E-021).
     *
     * <p>Lia {@code auth.getDetails()} como um {@code Map} e pegava a chave {@code "tenant"}. Mas
     * {@code JwtAuthenticationToken.getDetails()} e um {@code WebAuthenticationDetails}, nunca um
     * {@code Map} — a condicao nunca era verdadeira e o metodo devolvia <b>string vazia,
     * sempre</b>. O comentario original admitia: "Por ora, retorna vazio; substituir quando
     * SecurityConfig estiver completo". Este proxy operava sem tenant desde que existe.
     */
    private String extractTenantId(Authentication auth) {
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            String tenant = jwtAuth.getToken().getClaimAsString("tenant_id");
            if (tenant != null && !tenant.isBlank()) {
                return tenant;
            }
        }
        // Nunca inventa um tenant. Quem decide o que fazer sem ele e o caso de uso.
        return null;
    }
}
