package com.neuroassist.e2e.support;

/**
 * Falha ao montar uma fixture — nunca uma falha de asserção.
 *
 * <p>A distinção importa: quando um cenário fica vermelho, a primeira pergunta é "o produto quebrou
 * ou o cenário não conseguiu nem começar?". Um {@code AssertionError} responde a primeira; esta
 * exceção responde a segunda, e por isso a mensagem carrega <b>qual passo</b> falhou, contra
 * <b>qual serviço</b>, em <b>qual URL</b> e com <b>qual corpo</b> — as quatro coisas que faltam num
 * "expected 200 but was 500" solto.
 */
public class FixtureException extends RuntimeException {

    public FixtureException(String mensagem) {
        super(mensagem);
    }

    public FixtureException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
