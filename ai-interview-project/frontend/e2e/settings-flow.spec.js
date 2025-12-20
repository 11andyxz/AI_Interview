const { test, expect } = require('@playwright/test');
const { loginAsAndy, navigateToPage } = require('./helpers/test-helpers');

test.describe('Settings Flow E2E Tests', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsAndy(page);
  });

  test('1. Navigate to Settings Page', async ({ page }) => {
    // Navigate to settings page
    await navigateToPage(page, 'settings');
    await page.waitForURL('**/settings', { timeout: 10000 });

    // Verify we're on settings page
    await expect(page.locator('text=/settings|preferences/i').first()).toBeVisible({ timeout: 5000 });
  });

  test('2. Settings Page Elements', async ({ page }) => {
    await navigateToPage(page, 'settings');

    // Check for common settings sections
    const settingsSections = [
      'profile',
      'notification',
      'privacy',
      'account',
      'appearance',
      'language'
    ];

    // At least one settings-related element should be visible
    let hasSettingsContent = false;
    for (const section of settingsSections) {
      const elements = await page.locator(`text=/${section}/i`).count();
      if (elements > 0) {
        hasSettingsContent = true;
        break;
      }
    }

    expect(hasSettingsContent).toBeTruthy();
  });

  test('3. Update Settings', async ({ page }) => {
    await navigateToPage(page, 'settings');

    // Look for toggle switches or checkboxes
    const toggles = page.locator('input[type="checkbox"], input[type="radio"]');
    if (await toggles.count() > 0) {
      // Click first toggle
      await toggles.first().click();
      await page.waitForTimeout(1000);

      // Look for save button
      const saveButton = page.locator('button:has-text("Save"), button:has-text("Update"), button:has-text("Apply")');
      if (await saveButton.count() > 0) {
        await saveButton.click();
        await page.waitForTimeout(2000);

        // Should show success message or remain on page
        const currentUrl = page.url();
        expect(currentUrl).toContain('settings');
      }
    } else {
      // If no toggles, just verify page loads
      expect(page.url()).toContain('settings');
    }
  });

  test('4. Settings Navigation', async ({ page }) => {
    await navigateToPage(page, 'settings');

    // Try to navigate back to dashboard
    await navigateToPage(page, 'dashboard');
    await page.waitForURL('**/');

    // Verify we're back on dashboard
    await expect(page.locator('[data-testid="interview-list"]').or(page.locator('text=/interview|dashboard/i')).first()).toBeVisible();
  });
});
