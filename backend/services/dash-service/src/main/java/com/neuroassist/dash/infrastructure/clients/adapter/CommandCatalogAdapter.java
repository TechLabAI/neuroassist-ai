package com.neuroassist.dash.infrastructure.clients.adapter;

import com.neuroassist.dash.application.port.out.CommandCatalogPort;
import com.neuroassist.dash.domain.model.CalibrationParams;
import com.neuroassist.dash.domain.model.CalibrationResult;
import com.neuroassist.dash.domain.model.CommandCatalogSnapshot;
import com.neuroassist.dash.infrastructure.clients.PacClient;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Adapter outbound: implementa {@link CommandCatalogPort} delegando ao {@link PacClient}.
 *
 * <p>Sem {@code fallbackMethod}: o PAC é dependência <i>hard</i> do overview, e a falha deve subir
 * para virar 503. Um breaker cuja exceção é capturada dentro do próprio método nunca abriria — o
 * resilience4j registraria sucesso.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CommandCatalogAdapter implements CommandCatalogPort {

    private final PacClient pacClient;

    @Override
    @CircuitBreaker(name = "pac-service")
    public CommandCatalogSnapshot getCommandCatalog(String patientId) {
        log.debug("Consultando PAC: catalogo ativo do paciente [{}]", patientId);
        PacClient.CatalogoAtivoView view = pacClient.getCatalogoAtivo(patientId);
        List<CommandCatalogSnapshot.CommandSummary> comandos =
                view.comandos() == null
                        ? List.of()
                        : view.comandos().stream()
                                .map(
                                        c ->
                                                new CommandCatalogSnapshot.CommandSummary(
                                                        c.id(),
                                                        c.rotulo(),
                                                        c.necessidade(),
                                                        c.sensibilidade(),
                                                        c.urgencia(),
                                                        c.calibracaoVersao()))
                                .toList();
        return new CommandCatalogSnapshot(view.catalogoVersao(), comandos);
    }

    @Override
    @CircuitBreaker(name = "pac-service")
    public CalibrationResult recalibrate(
            String patientId, String commandId, CalibrationParams params) {
        log.debug(
                "Write-through PAC: recalibracao paciente [{}] comando [{}]", patientId, commandId);
        PacClient.RecalibrateBody body =
                new PacClient.RecalibrateBody(
                        params.urgencia(),
                        params.sensibilidade(),
                        params.tempoMinGestoMs(),
                        params.tempoMaxGestoMs());
        PacClient.ComandoLifecycleView view = pacClient.recalibrar(patientId, commandId, body);
        return new CalibrationResult(
                view.id(),
                view.sensibilidade(),
                view.urgencia(),
                view.estado(),
                view.calibracaoVersao(),
                view.warnings() == null ? List.of() : view.warnings());
    }
}
