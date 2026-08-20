package com.neuroassist.dash.infrastructure.sse;

import com.neuroassist.dash.domain.model.PanelSession;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Associates a live SSE emitter with its domain session metadata. */
public record SseRegistration(SseEmitter emitter, PanelSession session) {}
