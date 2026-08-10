package com.neuroassist.gest.api.controller;

import com.neuroassist.gest.api.dto.DetectionContractRequest;
import com.neuroassist.gest.api.dto.DetectionContractResponse;
import com.neuroassist.gest.application.port.in.DetectionContractUseCase;
import com.neuroassist.gest.application.port.in.DetectionContractUseCase.ValidateReportCommand;
import com.neuroassist.gest.application.port.in.DetectionContractUseCase.ValidationResult;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/recognized-commands")
@RequiredArgsConstructor
public class DetectionContractController {

    private final DetectionContractUseCase detectionContractUseCase;

    @PostMapping
    public ResponseEntity<DetectionContractResponse> validateReport(
            @RequestBody DetectionContractRequest request) {
        List<String> tipoGestoIds = request.primitives().stream()
                .map(DetectionContractRequest.PrimitiveRef::tipoGestoId)
                .collect(Collectors.toList());
        ValidateReportCommand command = new ValidateReportCommand(
                request.profileVersion(), tipoGestoIds);
        ValidationResult result = detectionContractUseCase.validateReport(command);
        return ResponseEntity.ok(new DetectionContractResponse(result.valid(), result.contractVersion()));
    }
}
