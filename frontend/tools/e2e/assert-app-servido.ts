/**
 * Confere que quem está na baseURL é O NOSSO aplicativo.
 *
 * POR QUE ISTO EXISTE
 *
 * O `webServer` do Playwright, com `reuseExistingServer: true`, considera pronto
 * qualquer coisa que responda — inclusive 404. Nesta máquina havia um processo
 * Java efêmero que ocupava :4200 e devolvia 404 para toda rota; as suítes
 * rodavam contra ele e reprovavam por "selector não encontrado", sem defeito
 * algum no aplicativo. O gate ficava vermelho pelo motivo errado, e ficaria
 * VERDE pelo motivo errado se um dia o intruso servisse os seletores certos.
 *
 * A checagem é de conteúdo, e é isso que a torna útil: status não distingue o
 * nosso app de um estranho, `<app-root>` distingue.
 */
async function globalSetup(config: { projects: { use: { baseURL?: string } }[] }): Promise<void> {
  const baseURL =
    process.env['APP_URL'] ?? config.projects[0]?.use?.baseURL ?? 'http://localhost:4200';

  let corpo: string;
  try {
    corpo = await (await fetch(baseURL)).text();
  } catch (erro) {
    throw new Error(
      `Nada respondeu em ${baseURL}. Suba o aplicativo (npx nx serve neuroassist-ai-web) ` +
        `ou aponte APP_URL para uma instância servida.\nCausa: ${String(erro)}`,
    );
  }

  if (!corpo.includes('<app-root')) {
    throw new Error(
      `Há algo escutando em ${baseURL}, mas NÃO é o neuroassist-ai-web: a resposta não ` +
        `contém <app-root>. Provavelmente outro processo tomou a porta. Rodar a suíte ` +
        `contra ele produziria falhas que não são do aplicativo.\n` +
        `Início da resposta: ${corpo.slice(0, 200).replace(/\s+/g, ' ')}`,
    );
  }
}

export default globalSetup;
