package com.neuroassist.gest.application.port.in;

import com.neuroassist.gest.domain.model.TipoGesto;
import java.util.List;

public interface TipoGestoUseCase {

    List<TipoGesto> listAll();

    TipoGesto findById(String id);

    TipoGesto register(String nome);

    TipoGesto transition(String id, String toState, String engineFeatureRef, String reason);
}
