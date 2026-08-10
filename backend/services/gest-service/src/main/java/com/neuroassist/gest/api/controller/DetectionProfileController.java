package com.neuroassist.gest.api.controller;

import com.neuroassist.gest.api.dto.DetectionProfileResponse;
import com.neuroassist.gest.application.port.in.DetectionProfileUseCase;
import com.neuroassist.gest.domain.model.DetectionProfile;
import com.neuroassist.gest.domain.model.DetectionProfilePrimitive;
import com.neuroassist.gest.shared.audit.AuditAction;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/gest-service/detection-profile")
@RequiredArgsConstructor
public class DetectionProfileController {

    private final DetectionProfileUseCase detectionProfileUseCase;

    @PostMapping("/publish")
    @PreAuthorize("hasAuthority('gest.detection-profile.publicar')")
    @AuditAction("UPDATE_DETECTION_PROFILE")
    public ResponseEntity<DetectionProfileResponse> publish() {
        DetectionProfile profile = detectionProfileUseCase.publish();
        return ResponseEntity.ok(toResponse(profile, List.of()));
    }

    @GetMapping("/active")
    public ResponseEntity<DetectionProfileResponse> getActive() {
        return detectionProfileUseCase.getActiveWithPrimitives()
                .map(r -> ResponseEntity.ok(toResponse(r.profile(), r.primitives())))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/version/{version}")
    public ResponseEntity<DetectionProfileResponse> findByVersion(@PathVariable int version) {
        return detectionProfileUseCase.findByVersion(version)
                .map(p -> ResponseEntity.ok(toResponse(p, List.of())))
                .orElse(ResponseEntity.notFound().build());
    }

    private DetectionProfileResponse toResponse(DetectionProfile p, List<DetectionProfilePrimitive> primitives) {
        List<DetectionProfileResponse.PrimitiveInfo> prims = primitives.stream()
                .map(prim -> new DetectionProfileResponse.PrimitiveInfo(
                        prim.id(), prim.tipoGestoId(), prim.nome(), prim.engineFeatureRef()))
                .toList();
        return new DetectionProfileResponse(p.id(), p.version(), p.contractVersion(), prims);
    }
}
