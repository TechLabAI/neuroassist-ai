package com.neuroassist.conv.api.controller;

import com.neuroassist.conv.application.port.in.NodeSelectionUseCase;
import com.neuroassist.conv.shared.audit.AuditAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

// Security: @PreAuthorize derived from rbac.arch.json via the module generator.
// To change which permission gates a method, edit rbac.arch.json and re-run
// be-module-generator.py; do not patch the annotation by hand.
@Slf4j
@RestController
@RequestMapping("/conv-service/node-selection")
@RequiredArgsConstructor
public class NodeSelectionController {

    private final NodeSelectionUseCase nodeSelectionUseCase;

    @PutMapping("/{id}/selectNode")
    @PreAuthorize("hasAuthority('conv.node-selection.emitir')")
    @AuditAction("CREATE_NODE_SELECTION")
    public ResponseEntity<Void> selectNode() {
        // TODO[SCAFFOLD]: delegate to NodeSelectionUseCase
        throw new UnsupportedOperationException("selectNode not implemented");
    }
}
