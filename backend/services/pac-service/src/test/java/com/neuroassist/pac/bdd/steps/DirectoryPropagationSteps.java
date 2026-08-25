package com.neuroassist.pac.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;

import com.neuroassist.pac.application.port.in.DirectoryOutboxDrainUseCase;
import com.neuroassist.pac.bdd.ScenarioContext;
import com.neuroassist.pac.infrastructure.persistence.entity.DirectoryOutboxJpaEntity;
import com.neuroassist.pac.infrastructure.persistence.entity.PacienteCuidadorId;
import com.neuroassist.pac.infrastructure.persistence.entity.PacienteCuidadorJpaEntity;
import com.neuroassist.pac.infrastructure.persistence.repository.DirectoryOutboxJpaRepository;
import com.neuroassist.pac.infrastructure.persistence.repository.PacienteCuidadorJpaRepository;
import com.neuroassist.pac.infrastructure.security.TenantContextHolder;
import com.neuroassist.pac.integration.ExternalServicesWireMock;
import com.neuroassist.pac.integration.PacWireMockStubs;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Step definitions — Directory propagation (E2E-015 / defect C-30).
 *
 * <p>Not annotated with @Component — registered exclusively via {@link
 * com.neuroassist.pac.bdd.BddTestConfiguration}.
 *
 * <p><b>The drain is invoked explicitly, never waited for.</b> {@code DirectoryOutboxScheduler}
 * exists and is wired, but a test that sleeps until a scheduled tick fires is a test that fails on
 * a slow machine and passes on a fast one. The scheduler's own responsibility — binding a tenant
 * before the transaction opens — is asserted by the unit test; what these scenarios assert is what
 * happens to an entry once the drain runs.
 */
@RequiredArgsConstructor
public class DirectoryPropagationSteps {

    private static final String TENANT = "empresa-abc";

    private final ScenarioContext ctx;
    private final DirectoryOutboxJpaRepository outboxRepository;
    private final PacienteCuidadorJpaRepository cuidadorRepository;
    private final DirectoryOutboxDrainUseCase drainUseCase;
    private final TransactionTemplate transactionTemplate;

    // -------------------------------------------------------------------------
    // Given
    // -------------------------------------------------------------------------

    @And("the directory outbox is empty")
    public void andTheDirectoryOutboxIsEmpty() {
        inTenant(
                () -> {
                    outboxRepository.deleteAll();
                    return null;
                });
    }

    @Given("ACS resolves any caregiver as belonging to \"empresa-abc\"")
    public void givenAcsResolvesAnyCaregiver() {
        PacWireMockStubs.stubAcsResolveCuidador_AnyBelongsTo(
                ExternalServicesWireMock.acs(), TENANT);
    }

    @And("ACS accepts caregiver-patient links")
    public void andAcsAcceptsLinks() {
        PacWireMockStubs.stubAcsCreateLink_Created(ExternalServicesWireMock.acs());
    }

    @And("ACS is unavailable for caregiver-patient links")
    public void andAcsIsUnavailableForLinks() {
        PacWireMockStubs.stubAcsCreateLink_Unavailable(ExternalServicesWireMock.acs());
    }

    @And("ACS refuses the revocation as the last active link")
    public void andAcsRefusesRevocation() {
        PacWireMockStubs.stubAcsRevokeLink_LastActiveLink(ExternalServicesWireMock.acs());
    }

    @Given("a pending \"UNLINK\" entry exists in the directory outbox")
    public void givenAPendingUnlinkEntryExists() {
        inTenant(
                () ->
                        outboxRepository.save(
                                DirectoryOutboxJpaEntity.builder()
                                        .operation("UNLINK")
                                        .pacienteId(UUID.randomUUID())
                                        .cuidadorId(UUID.randomUUID())
                                        .build()));
    }

    // -------------------------------------------------------------------------
    // When
    // -------------------------------------------------------------------------

    @When("the directory outbox is drained")
    public void whenTheDirectoryOutboxIsDrained() {
        TenantContextHolder.set(TENANT);
        try {
            drainUseCase.drainCurrentTenant();
        } finally {
            TenantContextHolder.clear();
        }
    }

    // -------------------------------------------------------------------------
    // Then
    // -------------------------------------------------------------------------

    @And("the directory outbox has {int} pending {string} entry")
    public void andTheOutboxHasPendingEntriesOfOperation(int expected, String operation) {
        List<DirectoryOutboxJpaEntity> entries = entriesWithStatus("pending");
        assertThat(entries)
                .as("entradas '%s' pendentes no directory_outbox", operation)
                .hasSize(expected)
                .allSatisfy(e -> assertThat(e.getOperation()).isEqualTo(operation));
    }

    @And("the directory outbox has {int} pending entries")
    public void andTheOutboxHasPendingEntries(int expected) {
        assertThat(entriesWithStatus("pending"))
                .as("entradas pendentes no directory_outbox")
                .hasSize(expected);
    }

    @And("the directory outbox has {int} {string} entry")
    public void andTheOutboxHasEntriesWithStatus(int expected, String status) {
        assertThat(entriesWithStatus(status))
                .as("entradas '%s' no directory_outbox", status)
                .hasSize(expected);
    }

    @And("the directory outbox has {int} {string} entry with a recorded reason")
    public void andTheOutboxHasFailedEntryWithReason(int expected, String status) {
        List<DirectoryOutboxJpaEntity> entries = entriesWithStatus(status);
        assertThat(entries).as("entradas '%s'", status).hasSize(expected);
        // Um estado terminal sem causa registrada é indistinguível de fila ocupada — foi assim
        // que o C-44 passou despercebido.
        assertThat(entries).allSatisfy(e -> assertThat(e.getLastError()).isNotBlank());
    }

    @And("the pending entry has {int} delivery attempt")
    public void andThePendingEntryHasAttempts(int expected) {
        List<DirectoryOutboxJpaEntity> entries = entriesWithStatus("pending");
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).getRetryCount())
                .as("tentativas registradas — a entrada continua retentável, não perdida")
                .isEqualTo(expected);
    }

    @Then("ACS received a link request for the created patient")
    public void thenAcsReceivedALinkRequest() {
        String location = ctx.getLastResponse().getHeaders().getFirst("Location");
        assertThat(location).as("Location do paciente criado").isNotNull();
        String pacienteId = location.substring(location.lastIndexOf('/') + 1);

        var requests =
                ExternalServicesWireMock.acs()
                        .findAll(
                                com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor(
                                        com.github.tomakehurst.wiremock.client.WireMock
                                                .urlPathMatching(
                                                        "/api/v1/acs/caregivers/.*/patients/"
                                                                + pacienteId
                                                                + "/link")));
        assertThat(requests)
                .as("o ACS precisa ter recebido o vínculo do paciente %s", pacienteId)
                .isNotEmpty();

        // A chamada nasce de um sweep agendado: não há chamador de quem herdar token, e ela
        // precisa viajar com a identidade de serviço do pac-service.
        String authorization = requests.get(0).getHeader("Authorization");
        assertThat(authorization)
                .as("a propagação do vínculo usa identidade de serviço, não o token do cuidador")
                .isEqualTo("Bearer " + PacWireMockStubs.SERVICE_TOKEN);

        // O tenant vai como query param porque o ACS o confronta com o claim do token.
        assertThat(requests.get(0).getUrl()).contains("tenantId=" + TENANT);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private List<DirectoryOutboxJpaEntity> entriesWithStatus(String status) {
        return inTenant(
                () ->
                        outboxRepository.findAll().stream()
                                .filter(e -> status.equals(e.getStatus()))
                                .toList());
    }

    /**
     * {@code directory_outbox} lives in a per-tenant schema, and a step method has no request to
     * inherit a tenant from. Binding it outside the transaction is not optional: Hibernate fixes
     * the {@code search_path} when it acquires the connection.
     */
    private <T> T inTenant(java.util.function.Supplier<T> work) {
        TenantContextHolder.set(TENANT);
        try {
            return transactionTemplate.execute(status -> work.get());
        } finally {
            TenantContextHolder.clear();
        }
    }

    // ── E2E-028 / ADR-010 — guarda de cobertura no PAC ──────────────────────

    /**
     * Vincula cuidadoras ao paciente com UUIDs <b>explicitos</b>, separados por virgula.
     *
     * <p>Explicitos de proposito: o cenario que prova "remover UMA de duas continua permitido"
     * precisa nomear no corpo do PUT qual das duas fica, e um UUID sorteado aqui nao teria como
     * aparecer la.
     */
    @And("patient {string} has caregivers {string}")
    public void patientHasCaregivers(String alias, String uuidsSeparadosPorVirgula) {
        UUID pacienteId = ctx.getAlias(alias);
        assertThat(pacienteId).as("o paciente %s precisa existir antes", alias).isNotNull();
        TenantContextHolder.set(ctx.getCurrentEmpresaId());
        try {
            transactionTemplate.executeWithoutResult(
                    status -> {
                        for (String raw : uuidsSeparadosPorVirgula.split(",")) {
                            cuidadorRepository.save(
                                    PacienteCuidadorJpaEntity.builder()
                                            .id(
                                                    new PacienteCuidadorId(
                                                            pacienteId,
                                                            UUID.fromString(raw.trim())))
                                            .createdAt(java.time.Instant.now())
                                            .build());
                        }
                    });
        } finally {
            TenantContextHolder.clear();
        }
    }

    @And("patient {string} still has {int} active caregivers")
    public void patientStillHasActiveCaregivers(String alias, int esperado) {
        UUID pacienteId = ctx.getAlias(alias);
        TenantContextHolder.set(ctx.getCurrentEmpresaId());
        try {
            long atuais =
                    transactionTemplate.execute(
                            status ->
                                    (long)
                                            cuidadorRepository
                                                    .findByIdPacienteId(pacienteId)
                                                    .size());
            // O ponto do cenario: a recusa acontece ANTES de qualquer escrita. Antes deste guarda o
            // PAC gravava a lista vazia e so depois o ACS discordava.
            assertThat(atuais).as("a recusa nao pode ter escrito nada").isEqualTo(esperado);
        } finally {
            TenantContextHolder.clear();
        }
    }

    @And("the response error code is {string}")
    public void responseErrorCodeIs(String esperado) {
        String body = ctx.getLastResponse().getBody();
        assertThat(body).as("corpo da resposta").isNotNull();
        assertThat(com.jayway.jsonpath.JsonPath.parse(body).read("$.error.code", String.class))
                .as("codigo de erro do ProblemDetail")
                .isEqualTo(esperado);
    }

    /**
     * Troca o token do cenario pelo de uma CUIDADORA — que edita o paciente, mas nao gerencia
     * vinculo. O token padrao do BDD representa a coordenadora.
     */
    @And("the caller is a caregiver without link management")
    public void callerIsCaregiverWithoutLinkManagement() {
        ctx.setCurrentJwt(
                com.neuroassist.pac.bdd.TestJwtHelper.createToken(
                        ctx.getCurrentEmpresaId(),
                        com.neuroassist.pac.bdd.TestJwtHelper.CUIDADORA_SCOPES));
    }
}
