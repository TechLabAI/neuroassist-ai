package com.neuroassist.evnt.api.controller;

import com.neuroassist.evnt.application.port.in.EventQueryUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class EventQueryController {

    private final EventQueryUseCase eventQueryUseCase;

    @GetMapping("/active")
    public ResponseEntity<EventQueryUseCase.ActiveEventsResponse> listActive() {
        return ResponseEntity.ok(eventQueryUseCase.listActive());
    }
}
