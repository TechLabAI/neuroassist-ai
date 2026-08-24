package com.neuroassist.pac.application.port.out;

/**
 * Porta de saída — o que quem está chamando tem permissão de fazer.
 *
 * <p>Existe porque há uma autorização que {@code @PreAuthorize} não consegue expressar: editar um
 * paciente e <b>vincular cuidadoras a ele</b> chegam pelo mesmo {@code PUT}, e são capacidades
 * diferentes. Uma cuidadora pode editar o paciente; só a coordenadora pode mudar quem cuida dele.
 * A anotação decide antes de saber o que o corpo pede — a diferença só aparece comparando o que
 * chegou com o que já estava.
 *
 * <p>Isso é autorização dependente de dado, e por isso mora no caso de uso. Mas <b>quem</b> chama
 * é detalhe de infraestrutura (Spring Security), então a pergunta atravessa esta porta em vez de o
 * serviço importar o {@code SecurityContextHolder} — RULE-04/05.
 */
public interface CallerAuthoritiesPort {

    /**
     * @param authority nome exato da authority, como o ACS a emite
     * @return {@code true} se o chamador atual a possui
     */
    boolean hasAuthority(String authority);
}
