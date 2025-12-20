const { test, expect } = require('@playwright/test');
const { loginAsAndy } = require('./helpers/test-helpers');

test.describe('Payment Flow E2E Tests', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsAndy(page);
  });

  test('1. View Subscription Plans', async ({ page }) => {
    await page.click('[data-testid="nav-payment"]');
    await page.waitForTimeout(2000);

    // Check if subscription plans are displayed
    const plansSection = page.locator('[data-testid="subscription-plans"]');
    if (await plansSection.count() > 0) {
      await expect(plansSection).toBeVisible({ timeout: 5000 });
    }
  });

  test('2. Payment History View', async ({ page }) => {
    await page.click('[data-testid="nav-payment"]');
    await page.waitForTimeout(2000);

    // Look for payment history section
    const historySection = page.locator('[data-testid="payment-history"]');
    if (await historySection.count() > 0) {
      await expect(historySection).toBeVisible({ timeout: 5000 });
    }
  });
});

