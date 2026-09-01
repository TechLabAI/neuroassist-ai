import { test, expect } from '@playwright/test';

/**
 * apenas-lado-cuidadora — TREI e2e gate (P6 / FE-TREI-003 / INV-1)
 * Source: docs/.../e2e/apenas-lado-cuidadora.feature
 * Status: stub scaffolded (D-494 promote). Implement in Layer 2.
 *
 * Covers: TREI-FE never writes confirmadoPaciente (INV-1 / ADR-007 FE / ADR-003 BE).
 * Includes: 3 dedicated INV-1 scenarios from specs-validate (handoff.M3.5.inv1_coverage).
 */

test.describe('Apenas lado cuidadora (INV-1)', () => {
  test('não há chamada a /aprovacoes/paciente em nenhuma ação TREI', async ({ page }) => {
    const pacienteRequests: string[] = [];
    page.on('request', (req) => {
      if (req.url().includes('/aprovacoes/paciente')) {
        pacienteRequests.push(req.url());
      }
    });

    await page.goto('/treinamento');
    // No action in TREI should ever call /aprovacoes/paciente (INV-1)
    await page.waitForTimeout(500);
    expect(pacienteRequests).toHaveLength(0);
  });

  test('o lado do paciente no indicador dual é sempre "pendente" em TREI', async ({ page }) => {
    await page.goto('/treinamento/sessao/propostas');
    // If any proposal cards exist, patient side must always show "pending"
    const cards = page.getByTestId('proposal-card');
    const count = await cards.count();
    for (let i = 0; i < count; i++) {
      await expect(cards.nth(i).getByTestId('paciente-pending')).toBeVisible();
    }
  });

  test('não existe botão de aprovação do paciente em nenhuma rota TREI', async ({ page }) => {
    await page.goto('/treinamento');
    await expect(page.getByTestId('approve-patient-btn')).not.toBeVisible();
    await page.goto('/treinamento/sessao/propostas');
    await expect(page.getByTestId('approve-patient-btn')).not.toBeVisible();
  });
});
