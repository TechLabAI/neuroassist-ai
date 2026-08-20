package com.neuroassist.hist.bdd;

import com.github.tomakehurst.wiremock.WireMockServer;

/**
 * Exposes the singleton WireMock server to step definitions. Step definitions access {@link
 * #ACS_WIREMOCK} to set up stubs and verify interactions.
 *
 * <p>The WireMock instance is created in {@link CucumberSpringContext} static initializer. This
 * holder provides a package-level reference so step definition classes (different Spring beans) can
 * reference it without circular dependency.
 */
public final class BddWireMockHolder {

    private BddWireMockHolder() {}

    /** Shared WireMock server for ACS actor directory. Started before Spring context. */
    public static WireMockServer ACS_WIREMOCK = CucumberSpringContext.ACS_WIREMOCK;
}
