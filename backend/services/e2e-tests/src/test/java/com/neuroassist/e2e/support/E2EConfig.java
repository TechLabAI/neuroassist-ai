package com.neuroassist.e2e.support;

/**
 * Endereços e credenciais da suíte, resolvidos em três níveis: propriedade de sistema (-D...),
 * variável de ambiente, e por último o default de desenvolvimento.
 *
 * <p>Por que o default de dev existe: a suíte precisa rodar sem cerimônia contra o
 * docker-compose.dev.yml, que é o único ambiente onde estas credenciais valem. Nenhum segredo real
 * mora aqui — {@code coord@neuroassist.dev} é um usuário de seed, criado pela migration de dev e
 * inexistente em staging/produção. Contra qualquer outro ambiente, passe {@code -De2e.senha=...} ou
 * {@code E2E_SENHA}.
 *
 * <p>As portas são as do host mapeadas pelo compose, não as internas do container — a suíte fala
 * com o stack de fora, como um cliente externo.
 */
public final class E2EConfig {

    private E2EConfig() {}

    public static final String ACS = url("e2e.acs.url", "E2E_ACS_URL", "http://localhost:8088");
    public static final String PAC = url("e2e.pac.url", "E2E_PAC_URL", "http://localhost:8082");
    public static final String GEST = url("e2e.gest.url", "E2E_GEST_URL", "http://localhost:8084");
    public static final String EDGE = url("e2e.edge.url", "E2E_EDGE_URL", "http://localhost:8087");
    public static final String HIST = url("e2e.hist.url", "E2E_HIST_URL", "http://localhost:8089");
    public static final String EVNT = url("e2e.evnt.url", "E2E_EVNT_URL", "http://localhost:18085");
    public static final String DASH = url("e2e.dash.url", "E2E_DASH_URL", "http://localhost:18090");

    public static final String LOGIN = url("e2e.login", "E2E_LOGIN", "coord@neuroassist.dev");
    public static final String SENHA = url("e2e.senha", "E2E_SENHA", "Test@1234!");

    /** Tenant de desenvolvimento (schema {@code t_emp_dev}). */
    public static final String TENANT =
            url("e2e.tenant", "E2E_TENANT", "e2e00000-0000-0000-0000-000000000001");

    /**
     * Quando {@code true}, stack fora do ar vira falha em vez de skip. É o modo que o CI deve usar:
     * lá, "pulei porque o ambiente não subiu" é exatamente o resultado que não se pode aceitar em
     * silêncio.
     */
    public static final boolean REQUIRE_STACK =
            Boolean.parseBoolean(url("e2e.requireStack", "E2E_REQUIRE_STACK", "false"));

    /** Header canônico de tenant em todos os serviços (E2E-021 removeu os aliases). */
    public static final String TENANT_HEADER = "X-Tenant-Id";

    private static String url(String sysProp, String envVar, String fallback) {
        String fromProp = System.getProperty(sysProp);
        if (fromProp != null && !fromProp.isBlank()) {
            return fromProp;
        }
        String fromEnv = System.getenv(envVar);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv;
        }
        return fallback;
    }
}
