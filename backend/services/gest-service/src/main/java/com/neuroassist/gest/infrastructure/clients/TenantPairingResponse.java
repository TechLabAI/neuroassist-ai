package com.neuroassist.gest.infrastructure.clients;

import java.util.UUID;

/**
 * Resposta de {@code GET /api/v1/edge/devices/&#123;id&#125;/tenant} — espelha {@code
 * DeviceRegistrationController.TenantRefView} do edge-service, campo por campo.
 *
 * <p><b>Este record dizia outra coisa (E2E-034).</b> Declarava {@code (String tenantId, boolean
 * paired)}, e o EDGE nunca enviou nenhum dos dois: ele envia {@code deviceId} e {@code tenantRef}.
 * O Jackson preenchia {@code tenantId=null} e {@code paired=false}, e o adapter lia isso como
 * "dispositivo não pareado" — <b>sempre</b>. O guarda de pareamento do GEST nunca funcionou, para
 * dispositivo nenhum, desde que existe.
 *
 * <p>É a 18ª ocorrência do padrão catalogado em plan-e2e-integration.md §11: o consumidor escreveu
 * o DTO a partir da própria suposição, e o stub do BDD foi escrito com a mesma suposição — então o
 * teste passava exatamente porque a suposição estava errada.
 *
 * <p>Não há campo de pareamento nesta resposta, e não é omissão: o endpoint é device→tenant, e o
 * contrato congelado (GEST FROZEN §4.2) diz "no patient, liveness, or credential data". Quem
 * conhece o estado de pareamento é {@code GET /devices/&#123;id&#125;} — usado por {@link
 * EdgeDeviceClient}. Aqui, 200 com {@code tenantRef} significa dispositivo conhecido; 404 significa
 * desconhecido.
 */
public record TenantPairingResponse(UUID deviceId, String tenantRef) {

    /** Conhecido pelo EDGE e atribuído a uma empresa. */
    public boolean known() {
        return tenantRef != null && !tenantRef.isBlank();
    }
}
