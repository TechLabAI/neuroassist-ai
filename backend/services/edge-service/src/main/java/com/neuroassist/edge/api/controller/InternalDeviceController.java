package com.neuroassist.edge.api.controller;

import com.neuroassist.edge.api.dto.VerifyCredentialRequest;
import com.neuroassist.edge.api.dto.VerifyCredentialResponse;
import com.neuroassist.edge.application.port.in.DeviceCredentialVerificationUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Superfície interna service-to-service do EDGE (service-identity.md §7).
 *
 * <p><b>Base path {@code /internal}, deliberadamente fora de {@code /api/v1/edge/**}:</b> o ADR-008
 * define a superfície pública do gateway como {@code /api/v1/edge/**}; nada sob {@code /internal} é
 * roteado pelo GWY, então este endpoint só é alcançável dentro da malha de serviços. Mover esta
 * rota para debaixo de {@code /api/v1/edge} a tornaria pública.
 *
 * <p>RULE-04: sem imports de {@code infrastructure.*} aqui.
 */
@Slf4j
@RestController
@RequestMapping("/internal/devices")
@RequiredArgsConstructor
public class InternalDeviceController {

    private final DeviceCredentialVerificationUseCase verificationUseCase;

    /**
     * POST /internal/devices/verify-credential — o ACS pergunta se um par {@code clientId}/{@code
     * clientSecret} de dispositivo é válido, antes de emitir um token com a authority {@code
     * edge.device} (C-18).
     *
     * <p>Toda rejeição é {@code 401 edge.credential-invalid}, indistinguível entre credencial
     * desconhecida, segredo errado e dispositivo aposentado. O segredo nunca é logado.
     */
    @PostMapping("/verify-credential")
    @PreAuthorize("hasAuthority('edge.internal')")
    public ResponseEntity<VerifyCredentialResponse> verifyCredential(
            @Valid @RequestBody VerifyCredentialRequest req) {
        DeviceCredentialVerificationUseCase.VerificationResult result =
                verificationUseCase.verify(req.clientId(), req.clientSecret());
        return ResponseEntity.ok(
                VerifyCredentialResponse.valid(result.deviceId(), result.tenantId()));
    }
}
