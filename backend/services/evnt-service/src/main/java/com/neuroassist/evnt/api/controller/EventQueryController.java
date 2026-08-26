package com.neuroassist.evnt.api.controller;

import com.neuroassist.evnt.application.port.in.EventQueryUseCase;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class EventQueryController {

    private final EventQueryUseCase eventQueryUseCase;

    @GetMapping("/active")
    @PreAuthorize("hasAuthority('evnt.event-query.read')")
    public ResponseEntity<EventQueryUseCase.ActiveEventsResponse> listActive(
            @RequestParam(required = false) String since) {
        // 'since' parameter accepted for polling fallback (EVNT-024); service ignores it for now
        return ResponseEntity.ok(eventQueryUseCase.listActive());
    }

    /**
     * Quais destes pacientes têm pedido de ajuda em aberto.
     *
     * <p><b>Para que o painel da cuidadora possa acender o indicador.</b> {@code GET /active}
     * devolve <b>um</b> evento e o resumo não carrega {@code pacienteId} — com ele, "este paciente
     * tem alarme?" não era respondível. Este endpoint responde pelo conjunto inteiro numa ida só: o
     * painel lista N pacientes, e uma pergunta por paciente seria um N+1 sobre HTTP exatamente no
     * caminho que a cuidadora abre quando o alarme chega.
     *
     * <p><b>Autoridade.</b> {@code SVC_DASH} é a identidade de serviço do dash-service — o mesmo
     * modelo já usado por {@code POST /events/&#123;id&#125;/confirm} e por {@code
     * /notifications/**}: o cuidador viaja explícito na borda do DASH, que já o autorizou em {@code
     * /panel/**}. {@code evnt.event-query.read} continua aceito para que um cuidador com JWT
     * próprio (ou uma ferramenta de suporte) possa fazer a mesma leitura. Ambos os nomes existem
     * literalmente hoje: {@code SVC_DASH} no cliente de serviço {@code dash-service} do ACS, {@code
     * evnt.event-query.read} nos papéis CUIDADORA / COORDENADOR_CLINICA — nenhuma authority nova
     * foi inventada aqui.
     *
     * @param pacienteIds repetível ({@code ?pacienteIds=a&pacienteIds=b}) ou separado por vírgula;
     *     ausente significa conjunto vazio, e a resposta é vazia — nunca "todos"
     */
    @GetMapping("/active/by-patient")
    @PreAuthorize("hasAnyAuthority('SVC_DASH','evnt.event-query.read')")
    public ResponseEntity<EventQueryUseCase.ActiveEventsByPatientResponse> listActiveByPatient(
            @RequestParam(name = "pacienteIds", required = false) List<String> pacienteIds) {
        return ResponseEntity.ok(
                eventQueryUseCase.listActiveByPatient(
                        pacienteIds == null ? List.of() : pacienteIds));
    }
}
