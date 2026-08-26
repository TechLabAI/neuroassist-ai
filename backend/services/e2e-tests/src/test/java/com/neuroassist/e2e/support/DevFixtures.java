package com.neuroassist.e2e.support;

/**
 * Identificadores do seed de desenvolvimento (schema {@code t_emp_dev}).
 *
 * <p>São dados semeados, não criados pela suíte. A escolha é deliberada: criar paciente, comando,
 * dispositivo e pareamento a cada execução exigiria um teardown confiável em 4 serviços diferentes,
 * e um teardown que falha pela metade deixa o ambiente de dev pior a cada rodada. Enquanto E2E-013
 * não tiver uma fixture factory com ciclo de vida próprio, apoiar-se no seed é a opção honesta.
 *
 * <p>Consequência a assumir: se alguém apagar estas linhas do banco de dev, os testes que as usam
 * falham com 404/403 — e a mensagem deve levar a esta classe.
 */
public final class DevFixtures {

    private DevFixtures() {}

    /** Dispositivo edge pareado, vinculado a {@link #PACIENTE}. */
    public static final String DEVICE = "aa4b420e-58b3-472d-9dcc-21cdbab99e94";

    /** Paciente cujo catálogo é projetado no perfil de detecção do {@link #DEVICE}. */
    public static final String PACIENTE = "0cd405c6-49c5-45ff-864e-6cc2157f0c71";

    /** Tenant que não existe — usado só para provar o isolamento, nunca para ler dados. */
    public static final String TENANT_INEXISTENTE = "11111111-1111-1111-1111-111111111111";

    /** Dispositivo que não existe — o gest-service deve responder {@code device-not-paired}. */
    public static final String DEVICE_INEXISTENTE = "00000000-0000-0000-0000-0000000000ff";
}
