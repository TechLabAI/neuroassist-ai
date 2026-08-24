package com.neuroassist.gest.infrastructure.clients;

import com.neuroassist.gest.application.port.out.PatientCatalogPort;
import com.neuroassist.gest.domain.model.Sensibilidade;
import com.neuroassist.gest.infrastructure.security.TenantContextHolder;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Adapter de {@link PatientCatalogPort} — lê o catálogo ativo do paciente no PAC.
 *
 * <p>Nunca lança para fora, pela mesma razão do {@link EdgeDeviceAdapter} — e, como lá, a falha de
 * transporte sobe até o breaker antes de virar estado. O dispositivo está na casa do paciente. Sem
 * o catálogo ele detecta com a faixa padrão e a resposta diz que está degradado; parar de detectar
 * porque o PAC caiu seria trocar uma detecção imprecisa por nenhuma.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PatientCatalogAdapter implements PatientCatalogPort {

    private final PacCatalogClient pacCatalogClient;

    @CircuitBreaker(name = "pacCatalog", fallbackMethod = "fallback")
    @Override
    public PatientBands bandsFor(UUID pacienteId) {
        try {
            // RULE-07: o tenant vem do contexto da requisicao, nunca de um default inventado.
            String empresaId = TenantContextHolder.getTenantIdOrNull();
            if (empresaId == null || empresaId.isBlank()) {
                log.warn(
                        "Sem tenant em contexto — nao da para ler o catalogo do paciente {}",
                        pacienteId);
                return PatientBands.unavailable();
            }
            PacCatalogClient.CatalogoAtivoView view =
                    pacCatalogClient.getCatalogoAtivo(pacienteId, empresaId);
            if (view == null || view.comandos() == null || view.comandos().isEmpty()) {
                log.debug("Paciente {} sem catalogo publicado", pacienteId);
                return PatientBands.empty();
            }
            Map<UUID, Sensibilidade> bands = new HashMap<>();
            for (PacCatalogClient.ComandoView comando : view.comandos()) {
                if (comando.primitivoPrincipalId() == null) {
                    continue;
                }
                Sensibilidade band = parse(comando.sensibilidade(), comando.id());
                if (band != null) {
                    // Dois comandos do mesmo paciente podem apontar para o mesmo primitivo. Vence a
                    // faixa mais sensivel: entre alarmes falsos e um pedido de ajuda que nao sai,
                    // um pedido que nao sai e o pior dos dois.
                    bands.merge(
                            comando.primitivoPrincipalId(),
                            band,
                            PatientCatalogAdapter::maisSensivel);
                }
            }
            log.debug(
                    "Paciente {} — catalogo v{} com {} primitivo(s) calibrado(s)",
                    pacienteId,
                    view.catalogoVersao(),
                    bands.size());
            return PatientBands.of(view.catalogoVersao(), bands);
        } catch (FeignException.NotFound e) {
            // O PAC respondeu: este paciente nao tem catalogo. Estado legitimo.
            log.debug("Paciente {} sem catalogo ativo (404)", pacienteId);
            return PatientBands.empty();
        }
        // Idem EdgeDeviceAdapter: a falha de transporte propaga para o breaker contar, e e o
        // fallbackMethod que a traduz em unavailable().
    }

    /**
     * Faixa desconhecida vira {@code null} e o primitivo cai no padrao — nao uma excecao. O PAC
     * valida o valor com um CHECK, entao um valor fora do vocabulario aqui significa que os dois
     * lados divergiram; e divergencia de vocabulario nao pode derrubar a deteccao do paciente.
     */
    private static Sensibilidade parse(String raw, UUID comandoId) {
        if (raw == null) {
            return null;
        }
        try {
            return Sensibilidade.valueOf(raw);
        } catch (IllegalArgumentException e) {
            log.warn(
                    "Comando {} traz sensibilidade desconhecida '{}' — usando a padrao",
                    comandoId,
                    raw);
            return null;
        }
    }

    private static Sensibilidade maisSensivel(Sensibilidade a, Sensibilidade b) {
        return a.ordinal() > b.ordinal() ? a : b; // baixa < media < alta
    }

    /**
     * Um 4xx que nao seja 404 quase nunca e o PAC caindo — e authority faltando, tenant errado, ou
     * contrato divergente. Continua degradando (o aparelho esta na casa do paciente), mas com um
     * log distinto e em ERROR: sem isso, um 403 de configuracao fica indistinguivel de uma queda,
     * que foi exatamente o defeito C-19.
     */
    @SuppressWarnings("unused") // invocado por nome pelo resilience4j
    private PatientBands fallback(UUID pacienteId, Throwable cause) {
        if (cause instanceof FeignException fe && fe.status() >= 400 && fe.status() < 500) {
            log.error(
                    "PAC recusou o catalogo do paciente {} com HTTP {} — isto NAO e uma queda do"
                            + " PAC. Verifique a authority pac.catalogo-ativo.read na identidade de"
                            + " servico do GEST e o tenant do token. A deteccao segue na faixa"
                            + " padrao.",
                    pacienteId,
                    fe.status());
        } else {
            log.warn(
                    "PAC indisponivel para o paciente {}: {}",
                    pacienteId,
                    cause.getClass().getSimpleName());
        }
        return PatientBands.unavailable();
    }
}
