package com.neuroassist.evnt.application.port.out;

public interface CommandLookupPort {

    CommandSnapshot lookup(String commandRef);

    record CommandSnapshot(String commandRef, String label, String urgencia) {}
}
