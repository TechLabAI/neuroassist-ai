package com.neuroassist.gest.application.port.in;

import com.neuroassist.gest.api.dto.TipoGestoRequest;
import com.neuroassist.gest.api.dto.TipoGestoResponse;
import com.neuroassist.gest.api.dto.TransitionRequest;
import java.util.List;
import java.util.UUID;

/** Inbound port — TipoGesto use cases. */
public interface TipoGestoUseCase {

    List<TipoGestoResponse> listAll();

    TipoGestoResponse findById(UUID id);

    TipoGestoResponse register(TipoGestoRequest request);

    TipoGestoResponse transition(UUID id, TransitionRequest request);
}
