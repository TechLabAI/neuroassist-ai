package com.neuroassist.evnt.infrastructure.persistence.adapter;

import com.neuroassist.evnt.application.port.out.CommandLookupPort;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component
public class CommandLookupHardcodedAdapter implements CommandLookupPort {

    @Override
    public CommandLookupPort.CommandSnapshot lookup(String commandRef) {
        return new CommandLookupPort.CommandSnapshot(commandRef, "Preciso de ajuda", "EMERGENCIA");
    }
}
