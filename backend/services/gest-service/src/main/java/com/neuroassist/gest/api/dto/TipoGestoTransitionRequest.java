package com.neuroassist.gest.api.dto;

public record TipoGestoTransitionRequest(
        String toState,
        String engineFeatureRef,
        String reason) {}
