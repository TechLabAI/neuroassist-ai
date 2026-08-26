package com.neuroassist.evnt.domain.service;

import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Decide se um alarme ainda faz sentido — a parte 3 do ADR-011.
 *
 * <p>POJO puro, zero Spring (RULE-01). Registrado como {@code @Bean} em {@code
 * DomainServiceConfig}.
 *
 * <p><b>Por que isto é uma classe de domínio e não um {@code if} dentro do sweep.</b> A regra mais
 * importante do ADR-011 — <i>um alarme de ajuda não expira</i> — precisa morar num lugar só, óbvio,
 * e testável sem subir Spring. Espalhada pelo serviço de aplicação, ela seria uma condição entre
 * outras, e a próxima pessoa a mexer no sweep teria de adivinhar que aquela condição em particular
 * é clínica e não técnica.
 *
 * <p><b>Três regras, nesta ordem:</b>
 *
 * <ol>
 *   <li>É um pedido de ajuda? Então <b>nunca expira</b>. Ponto. Nenhuma configuração derruba isto.
 *   <li>Existe TTL configurado para a urgência deste alarme? Se não existe, <b>não expira</b> — o
 *       default seguro do ADR-011 é não expirar, o oposto do default anterior, que era desistir.
 *   <li>Existe TTL: expira quando a idade do alarme o ultrapassa. Por <b>idade</b>, nunca por
 *       contagem de tentativas.
 * </ol>
 */
public class AlarmRelevancePolicy {

    private final Map<String, Duration> ttlPorUrgencia;
    private final Set<String> termosDeAjuda;

    /**
     * @param ttlPorUrgencia janela de relevância por {@code comando.urgencia} (ex.: {@code rotina
     *     -> 2h}). Vazio significa "nenhuma necessidade tem política definida ainda", que é o
     *     default de produção — o ADR-011 exige um dono explícito por tipo de necessidade antes de
     *     qualquer alarme poder envelhecer
     * @param termosDeAjuda termos que identificam um pedido de ajuda no {@code comando_ref} ou no
     *     rótulo congelado do comando
     */
    public AlarmRelevancePolicy(Map<String, Duration> ttlPorUrgencia, Set<String> termosDeAjuda) {
        this.ttlPorUrgencia = normalizeKeys(ttlPorUrgencia);
        this.termosDeAjuda = normalizeTerms(termosDeAjuda);
    }

    /**
     * Um pedido de ajuda?
     *
     * <p><b>O casamento é deliberadamente generoso</b> (substring, sem acento, sem caixa). Os dois
     * erros possíveis não custam a mesma coisa: um falso positivo mantém um alarme na fila para
     * sempre — memória; um falso negativo deixa um pedido de ajuda expirar — o oposto exato do que
     * o ADR-011 decidiu. Quando há dúvida, a resposta é "é ajuda".
     */
    public boolean isPedidoDeAjuda(String comandoRef, String rotuloSnapshot) {
        String alvo = normalize(comandoRef) + " " + normalize(rotuloSnapshot);
        for (String termo : termosDeAjuda) {
            if (alvo.contains(termo)) {
                return true;
            }
        }
        return false;
    }

    /**
     * A janela de relevância deste alarme, ou {@link Optional#empty()} quando ele não expira nunca.
     */
    public Optional<Duration> janelaDeRelevancia(
            String urgencia, String comandoRef, String rotuloSnapshot) {
        if (isPedidoDeAjuda(comandoRef, rotuloSnapshot)) {
            return Optional.empty(); // regra 1 — inegociável
        }
        return Optional.ofNullable(ttlPorUrgencia.get(normalize(urgencia)));
    }

    /**
     * @param criadoEm quando o alarme nasceu — a idade é medida a partir daqui, não da última
     *     tentativa: um alarme que continua sendo tentado não fica mais novo por isso
     * @return true somente quando existe política E a idade a ultrapassou
     */
    public boolean expirouPorRelevancia(
            String urgencia,
            String comandoRef,
            String rotuloSnapshot,
            Instant criadoEm,
            Instant agora) {
        if (criadoEm == null || agora == null) {
            return false;
        }
        return janelaDeRelevancia(urgencia, comandoRef, rotuloSnapshot)
                .map(ttl -> Duration.between(criadoEm, agora).compareTo(ttl) > 0)
                .orElse(false);
    }

    private static Map<String, Duration> normalizeKeys(Map<String, Duration> origem) {
        if (origem == null || origem.isEmpty()) {
            return Map.of();
        }
        return origem.entrySet().stream()
                .filter(e -> e.getKey() != null && e.getValue() != null)
                .collect(
                        java.util.stream.Collectors.toUnmodifiableMap(
                                e -> normalize(e.getKey()), Map.Entry::getValue, (a, b) -> a));
    }

    private static Set<String> normalizeTerms(Set<String> origem) {
        if (origem == null || origem.isEmpty()) {
            return Set.of();
        }
        return origem.stream()
                .filter(t -> t != null && !t.isBlank())
                .map(AlarmRelevancePolicy::normalize)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /** Minúsculas e sem acento — "Ajuda", "AJUDA" e "ajúda" são a mesma necessidade. */
    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String semAcento =
                Normalizer.normalize(value, Normalizer.Form.NFD)
                        .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return semAcento.toLowerCase(Locale.ROOT).trim();
    }
}
