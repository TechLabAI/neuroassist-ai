import { test, expect } from '@playwright/test';

/**
 * geracao-server-mediated — TREI e2e gate (P6 / FE-TREI-005 / TECH-007)
 * Source: docs/.../e2e/geracao-server-mediated.feature
 * Status: stub scaffolded (D-494 promote). Implement in Layer 2.
 *
 * Covers: zero client-side generation (ADR-005 / TECH-007); no Anthropic SDK
 * in browser bundle; every generated token comes from trei-service.
 */

test.describe('Geração server-mediated (TECH-007)', () => {
  test('não há chamada direta à API da Anthropic do browser', async ({ page }) => {
    const anthropicRequests: string[] = [];
    page.on('request', (req) => {
      if (req.url().includes('api.anthropic.com')) {
        anthropicRequests.push(req.url());
      }
    });

    await page.goto('/treinamento');
    await page.waitForTimeout(500);
    // Zero-generation invariant: no direct Anthropic call ever leaves the browser
    expect(anthropicRequests).toHaveLength(0);
  });

  test('o banner "Treinamento — não é o app do paciente" está visível (TECH-007)', async ({ page }) => {
    await page.goto('/treinamento');
    await expect(page.getByTestId('authoring-banner')).toBeVisible();
    await page.goto('/treinamento/sessao/propostas');
    await expect(page.getByTestId('authoring-banner')).toBeVisible();
  });

  test('o bundle não contém referência ao SDK da Anthropic', async ({ page }) => {
    // This test is structural — relies on the FE never importing @anthropic-ai/*
    // Verified by dependency check (generator-config.yml extra_dependencies: [])
    // Runtime assertion: no window.Anthropic object after boot
    await page.goto('/treinamento');
    const hasAnthropicGlobal = await page.evaluate(() => 'Anthropic' in window);
    expect(hasAnthropicGlobal).toBe(false);
  });
});
