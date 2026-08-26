package com.neuroassist.dash.infrastructure.clients.adapter;

import com.neuroassist.dash.application.port.out.PatientDirectoryPort;
import com.neuroassist.dash.infrastructure.clients.PacClient;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Adapter outbound: implementa {@link PatientDirectoryPort} delegando ao {@link PacClient}.
 *
 * <p>Sem {@code fallbackMethod} de propósito. A falha precisa <b>subir</b> por duas razões: um
 * breaker cuja exceção é capturada dentro do método nunca abre (o resilience4j registra sucesso), e
 * quem decide o que fazer sem o nome é o serviço de aplicação — que registra a ignorância como
 * {@code name = null} em vez de mostrar um UUID no lugar de uma pessoa.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PatientDirectoryAdapter implements PatientDirectoryPort {

    private final PacClient pacClient;

    @Override
    @CircuitBreaker(name = "pac-service")
    public Map<String, String> getPatientNames(Collection<String> patientIds) {
        if (patientIds == null || patientIds.isEmpty()) {
            return Map.of();
        }
        Set<String> pedidos = new HashSet<>(patientIds);
        log.debug("Consultando PAC: nomes de {} paciente(s)", pedidos.size());

        Map<String, String> nomes = new LinkedHashMap<>();
        for (PacClient.PacienteView p : pacClient.listarPacientes()) {
            if (p.id() != null && pedidos.contains(p.id())) {
                nomes.put(p.id(), p.nome());
            }
        }
        return Map.copyOf(nomes);
    }

    @Override
    @CircuitBreaker(name = "pac-service")
    public String getPatientName(String patientId) {
        log.debug("Consultando PAC: nome do paciente [{}]", patientId);
        return pacClient.buscarPaciente(patientId).nome();
    }
}
