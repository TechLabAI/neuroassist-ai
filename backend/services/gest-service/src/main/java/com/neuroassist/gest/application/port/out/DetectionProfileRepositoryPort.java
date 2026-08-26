package com.neuroassist.gest.application.port.out;

import com.neuroassist.gest.domain.model.DetectionProfile;
import java.util.Optional;

/**
 * Outbound port — DetectionProfile persistence. Profiles are immutable after publish; only the
 * {@code active} flag can be flipped (via {@link #deactivateCurrent()}). Multi-tenancy is implicit
 * via schema routing. Implemented by DetectionProfileRepositoryAdapter (infrastructure layer, P3).
 */
public interface DetectionProfileRepositoryPort {

    DetectionProfile save(DetectionProfile profile);

    Optional<DetectionProfile> findActive();

    /**
     * Devolve o perfil de uma versão específica desta empresa — ativo ou já desativado.
     *
     * <p>Existe para auditoria: um relatório de gesto carrega a {@code profileVersion} com que foi
     * detectado, e sem esta consulta não há como recuperar quais limiares estavam valendo naquele
     * momento. Enquanto ela faltou, o serviço devolvia {@code Optional.empty()} de dentro de si
     * mesmo e {@code GET /detection-profile/&#123;version&#125;} era 404 eterno — um endpoint que
     * existia e não respondia.
     *
     * <p>A versão é única por schema de empresa (o roteamento por {@code search_path} já isola o
     * tenant), então não há parâmetro de empresa aqui.
     */
    Optional<DetectionProfile> findByVersion(long version);

    /**
     * Returns the next monotonic version number for this tenant's profile sequence. The first
     * version is 1 ({@code COALESCE(MAX(version), 0) + 1}). Must be called inside an active
     * transaction (RULE-03 — @Transactional only in application.service).
     */
    long nextVersion();

    /**
     * Marks all currently-active profiles for this tenant as inactive. Called before persisting a
     * new profile so that at most one profile is active at a time (publish invariant, GEST-009).
     * Must be called inside an active transaction.
     */
    void deactivateCurrent();
}
