package com.neuroassist.dash.domain.model;

import java.util.List;

/**
 * Snapshot do catálogo ativo de comandos de um paciente, vindo do PAC. Zero JPA/Spring.
 *
 * <p><b>A forma anterior — {@code (List<String> commands, String calibrationState)} — não existia
 * do lado do PAC.</b> O catálogo do PAC devolve {@code catalogoVersao} mais uma lista de comandos
 * com campos próprios, e <b>não existe endpoint de "estado de calibração"</b>: o que o PAC tem é um
 * {@code calibracaoVersao} <i>por comando</i>. O campo {@code calibrationState} do DASH nunca teve
 * origem — era preenchido a partir de um {@code GET .../calibration} que responde 404.
 *
 * @param catalogoVersao versão do catálogo do paciente; é a semente do ETag no PAC
 */
public record CommandCatalogSnapshot(int catalogoVersao, List<CommandSummary> commands) {

    /**
     * Espelha {@code CatalogoAtivoResponse.ComandoSummary} nos campos que o painel mostra.
     *
     * @param calibracaoVersao o mais próximo que existe de um "estado de calibração": quantas vezes
     *     este comando foi recalibrado
     */
    public record CommandSummary(
            String id,
            String rotulo,
            String necessidade,
            String sensibilidade,
            String urgencia,
            int calibracaoVersao) {}
}
