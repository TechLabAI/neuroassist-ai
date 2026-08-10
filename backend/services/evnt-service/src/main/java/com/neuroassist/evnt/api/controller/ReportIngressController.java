package com.neuroassist.evnt.api.controller;

import com.neuroassist.evnt.application.port.in.ReportIngressUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportIngressController {

    private final ReportIngressUseCase reportIngressUseCase;

    @PostMapping
    public ResponseEntity<ReportIngressUseCase.ReportIngressResponse> ingest(
            @RequestBody ReportIngressUseCase.ReportIngressRequest request) {
        ReportIngressUseCase.ReportIngressResponse response = reportIngressUseCase.ingest(request);
        HttpStatus status =
                "created".equals(response.status()) ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(response);
    }
}
