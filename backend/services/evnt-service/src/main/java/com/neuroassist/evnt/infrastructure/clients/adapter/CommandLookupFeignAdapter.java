package com.neuroassist.evnt.infrastructure.clients.adapter;

import com.neuroassist.evnt.application.port.out.CommandLookupPort;
import com.neuroassist.evnt.infrastructure.clients.CommandLookupClient;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Reads the command's label and urgency from pac-service at the moment the report arrives.
 *
 * <p>Replaces {@code CommandLookupHardcodedAdapter}, which returned a fixed {@code "Preciso de
 * ajuda"} / {@code EMERGENCIA} snapshot — so in an integrated system every alarm would have shown
 * the same sentence and been escalated at the same tier, whichever command the patient triggered.
 *
 * <p><b>Fail-closed.</b> The urgency decides whether the caregiver gets a full-screen alarm or a
 * discreet notification. Guessing it — in either direction — is a patient-safety decision this
 * adapter has no standing to make, so a failure to reach pac-service propagates.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommandLookupFeignAdapter implements CommandLookupPort {

    private final CommandLookupClient client;

    @Override
    public CommandSnapshot lookup(String pacienteId, String commandRef) {
        UUID paciente = UUID.fromString(pacienteId);
        UUID comando = UUID.fromString(commandRef);
        CommandLookupClient.ComandoResponse response = client.findCommand(paciente, comando);
        String label = response.rotulo() != null ? response.rotulo() : response.necessidade();
        log.debug(
                "Command snapshot resolved comandoRef={} urgencia={}",
                commandRef,
                response.urgencia());
        return new CommandSnapshot(commandRef, label, response.urgencia());
    }
}
