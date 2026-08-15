package com.neuroassist.evnt.application.port.in;

import java.util.UUID;

public interface EventConfirmationUseCase {

    void confirm(UUID eventoId, String verdict, String confirmedBy);
}
