package com.neuroassist.evnt.api.controller;

import com.neuroassist.evnt.application.port.in.ReportIngressUseCase;
import com.neuroassist.evnt.application.port.in.TenantResolverPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Ingress for gesture reports produced by an edge device agent.
 *
 * <p>Guarded by {@code edge.device} — the authority carried by a device token minted at {@code POST
 * /oauth2/token/device} on acs-service, whose credential the device received at enroll. Until this
 * change the endpoint was anonymous, so the tenant could not be established from the caller and
 * {@code TenantContextFilter} hardcoded {@code "poc"} (C-17).
 *
 * <p><b>The tenant comes from the token, never from the body.</b> The request record still carries
 * {@code deviceTenant}, and the service still compares it against {@code patientTenant} to reject a
 * cross-tenant report (EVNT-022) — but that guard only means something if the device cannot assert
 * its own tenant. So the value is overwritten here with the {@code tenant_id} claim.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportIngressController {

    private final ReportIngressUseCase reportIngressUseCase;
    private final TenantResolverPort tenantResolverPort;

    @PostMapping
    @PreAuthorize("hasAuthority('edge.device')")
    public ResponseEntity<ReportIngressUseCase.ReportIngressResponse> ingest(
            @RequestBody ReportIngressUseCase.ReportIngressRequest request) {

        String tokenTenant = tenantResolverPort.getTenantIdRequired();
        ReportIngressUseCase.ReportIngressResponse response =
                reportIngressUseCase.ingest(withTokenTenant(request, tokenTenant));
        HttpStatus status =
                "created".equals(response.status()) ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(response);
    }

    /**
     * Rebuilds the request with the device's tenant taken from the token, and defaults {@code
     * patientTenant} to it when the body omits it — a report that does not name a patient tenant is
     * about a patient of the device's own tenant, which is also the schema the request is already
     * routed to.
     */
    private ReportIngressUseCase.ReportIngressRequest withTokenTenant(
            ReportIngressUseCase.ReportIngressRequest request, String tokenTenant) {
        return new ReportIngressUseCase.ReportIngressRequest(
                request.reportId(),
                request.pacienteId(),
                request.commandRef(),
                request.primitives(),
                request.confidence(),
                request.profileVersion(),
                request.recognizedAt(),
                request.deviceId(),
                request.rawFramePayload(),
                request.patientTenant() == null ? tokenTenant : request.patientTenant(),
                tokenTenant);
    }
}
