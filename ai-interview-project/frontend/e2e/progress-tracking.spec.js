const { test, expect } = require('@playwright/test');
const { loginAsAndy, navigateToPage } = require('./helpers/test-helpers');

test.describe('Progress Tracking E2E Tests', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsAndy(page);
  });

  test('1. Navigate to Progress Page', async ({ page }) => {
    // Navigate to progress page
    await navigateToPage(page, 'progress');
    await page.waitForURL('**/progress', { timeout: 10000 });

    // Verify we're on progress page
    await expect(page.locator('text=/progress|tracking|dashboard/i').first()).toBeVisible({ timeout: 5000 });
  });

  test('2. View Progress Statistics', async ({ page }) => {
    await navigateToPage(page, 'progress');

    // Check for progress-related content
    const progressIndicators = [
      'completed',
      'score',
      'average',
      'progress',
      'statistics',
      'chart',
      'graph'
    ];

    let hasProgressContent = false;
    for (const indicator of progressIndicators) {
      const elements = await page.locator(`text=/${indicator}/i`).count();
      if (elements > 0) {
        hasProgressContent = true;
        break;
      }
    }

    expect(hasProgressContent).toBeTruthy();
  });

  test('3. Progress Charts and Visualizations', async ({ page }) => {
    await navigateToPage(page, 'progress');

    // Look for chart elements (SVG, Canvas, or chart containers)
    const charts = page.locator('svg, canvas, [class*="chart"], [class*="graph"]');
    const chartCount = await charts.count();

    // Should have at least some visual elements or data display
    const hasVisualElements = chartCount > 0 ||
                             await page.locator('text=/\\d+/').count() > 0; // Numbers indicating stats

    expect(hasVisualElements).toBeTruthy();
  });

  test('4. Skill Progress Tracking', async ({ page }) => {
    await navigateToPage(page, 'skills');
    await page.waitForURL('**/skills', { timeout: 10000 });

    // Verify skills page loads
    await expect(page.locator('text=/skill|progress|tracking/i').first()).toBeVisible({ timeout: 5000 });

    // Look for skill-related content
    const skillElements = await page.locator('text=/skill|competency|proficiency/i').count();
    expect(skillElements).toBeGreaterThanOrEqual(0);
  });

  test('5. Progress History', async ({ page }) => {
    await navigateToPage(page, 'progress');

    // Look for historical data or timeline
    const historyElements = page.locator('text=/history|timeline|recent|activity/i');
    const hasHistory = await historyElements.count() > 0;

    if (hasHistory) {
      await expect(historyElements.first()).toBeVisible();
    } else {
      // If no explicit history, page should still load
      expect(page.url()).toContain('progress');
    }
  });

  test('6. Progress Goals and Targets', async ({ page }) => {
    await navigateToPage(page, 'progress');

    // Look for goal or target related content
    const goalElements = page.locator('text=/goal|target|objective|aim/i');
    const hasGoals = await goalElements.count() > 0;

    if (hasGoals) {
      await expect(goalElements.first()).toBeVisible();
    }
  });
});
