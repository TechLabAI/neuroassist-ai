package com.neuroassist.acs.bdd;

import com.neuroassist.acs.application.port.out.MigProvisioningPort;
import com.neuroassist.acs.application.port.out.PacPatientResolvePort;
import com.neuroassist.acs.integration.BaseIntegrationTest;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;

/**
 * Bootstraps the Spring context for Cucumber step definitions. Extends BaseIntegrationTest to
 * inherit Testcontainers PostgreSQL setup (D-532: singleton container pattern for BDD test
 * context).
 *
 * <p>@SpringBootTest and @ActiveProfiles("test") are inherited from BaseIntegrationTest.
 *
 * <p>@MockBean for external port adapters (P5 / ACS-024): - MigProvisioningPort: enables
 * per-scenario MIG behaviour control (success / timeout). - PacPatientResolvePort: enables
 * per-scenario PAC patient resolution stubs. HistAuditPort is NOT mocked here — HistAuditAdapter is
 * already a no-op fire-and-forget stub.
 */
@CucumberContextConfiguration
public class CucumberSpringContext extends BaseIntegrationTest {

    /** Replaces MigProvisioningAdapter so scenarios can control MIG responses. */
    @MockBean public MigProvisioningPort migProvisioningPort;

    /** Replaces PacPatientResolveAdapter so scenarios can control PAC patient resolution. */
    @MockBean public PacPatientResolvePort pacPatientResolvePort;
}
