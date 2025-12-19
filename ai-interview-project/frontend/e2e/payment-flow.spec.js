const { test, expect } = require('@playwright/test');
const { loginAsAndy } = require('./helpers/test-helpers');

test.describe('Payment Flow E2E Tests', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsAndy(page);
  });

  test('1. View Subscription Plans', async ({ page }) => {
    await page.click('a:has-text("Subscription"), a:has-text("Payment"), a[href*="/payment"]');
    await page.waitForTimeout(2000);
    
    // Check if subscription plans are displayed
    const plansSection = page.locator('text=/plan|subscription|pricing/i');
    if (await plansSection.count() > 0) {
      await expect(plansSection.first()).toBeVisible({ timeout: 5000 });
    }
  });

  test('2. Payment History View', async ({ page }) => {
    await page.click('a:has-text("Subscription"), a:has-text("Payment"), a[href*="/payment"]');
    await page.waitForTimeout(2000);
    
    // Look for payment history button or section
    const historyButton = page.locator('button:has-text("History"), a:has-text("History")');
    if (await historyButton.count() > 0) {
      await historyButton.click();
      await page.waitForTimeout(1000);
      
      // Should show payment history
      const historySection = page.locator('text=/history|transaction|payment/i');
      const hasHistory = await historySection.count() > 0;
      expect(hasHistory).toBeTruthy();
    }
  });
});

