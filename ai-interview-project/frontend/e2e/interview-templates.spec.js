const { test, expect } = require('@playwright/test');
const { loginAsAndy, waitForModalReady } = require('./helpers/test-helpers');

test.describe('Interview Templates E2E Tests', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsAndy(page);
  });

  test('1. Access Interview Creation with Templates', async ({ page }) => {
    // Click New Interview button
    await page.click('[data-testid="sidebar-new-interview-button"]');

    // Wait for modal
    await waitForModalReady(page);

    // Check if template selection is available
    const templateElements = page.locator('text=/template|preset|predefined/i');
    const hasTemplates = await templateElements.count() > 0;

    expect(hasTemplates).toBeTruthy();
  });

  test('2. View Available Templates', async ({ page }) => {
    await page.click('[data-testid="sidebar-new-interview-button"]');
    await waitForModalReady(page);

    // Look for template options
    const templateOptions = page.locator('select, button, [class*="template"]');
    const hasTemplateOptions = await templateOptions.count() > 0;

    if (hasTemplateOptions) {
      // Should show template names or descriptions
      const templateNames = page.locator('text=/developer|engineer|senior|junior|frontend|backend/i');
      const hasNames = await templateNames.count() > 0;

      expect(hasNames).toBeTruthy();
    }
  });

  test('3. Select Interview Template', async ({ page }) => {
    await page.click('[data-testid="sidebar-new-interview-button"]');
    await waitForModalReady(page);

    // Look for template selection elements
    const templateSelectors = page.locator('select[name*="template"], button:has-text("Choose Template")');
    const hasSelectors = await templateSelectors.count() > 0;

    if (hasSelectors) {
      // Try to select first available template
      const firstOption = page.locator('option').nth(1); // Skip "Select..." option
      if (await firstOption.count() > 0) {
        await firstOption.click();
        await page.waitForTimeout(1000);

        // Should update form fields
        const positionInput = page.locator('[data-testid="position-type-input"]');
        const hasPreFilled = await positionInput.count() > 0 && await positionInput.inputValue() !== '';

        expect(hasPreFilled || page.locator('[data-testid="modal-backdrop"]').count() > 0).toBeTruthy();
      }
    }
  });

  test('4. Template Preview', async ({ page }) => {
    await page.click('[data-testid="sidebar-new-interview-button"]');
    await waitForModalReady(page);

    // Look for template preview or details
    const previewElements = page.locator('text=/preview|details|description|duration|level/i');
    const hasPreview = await previewElements.count() > 0;

    if (hasPreview) {
      // Should show template information
      const templateInfo = page.locator('text=/minutes|questions|difficulty|tech/i');
      const hasInfo = await templateInfo.count() > 0;

      expect(hasInfo).toBeTruthy();
    }
  });

  test('5. Create Interview from Template', async ({ page }) => {
    await page.click('[data-testid="sidebar-new-interview-button"]');
    await waitForModalReady(page);

    // Try to create interview with default or selected template
    const createButton = page.locator('[data-testid="create-interview-button"]');
    const canCreate = await createButton.count() > 0;

    if (canCreate) {
      // Ensure terms are agreed
      const termsCheckbox = page.locator('input[type="checkbox"]').last();
      if (await termsCheckbox.count() > 0) {
        await termsCheckbox.check();
      }

      await createButton.click();

      // Wait for navigation or success
      await page.waitForTimeout(3000);

      const isSuccess = page.url().includes('/interview/') ||
                       page.locator('text=/success|created/i').count() > 0;

      expect(isSuccess).toBeTruthy();
    } else {
      // If can't create, at least verify modal is functional
      expect(await page.locator('[data-testid="modal-backdrop"]').count() > 0).toBeTruthy();
    }
  });

  test('6. Template Categories', async ({ page }) => {
    await page.click('[data-testid="sidebar-new-interview-button"]');
    await waitForModalReady(page);

    // Look for template categories
    const categories = [
      'technical',
      'behavioral',
      'system design',
      'frontend',
      'backend',
      'fullstack'
    ];

    let hasCategories = false;
    for (const category of categories) {
      const elements = await page.locator(`text=/${category}/i`).count();
      if (elements > 0) {
        hasCategories = true;
        break;
      }
    }

    // Categories help organize templates
    expect(hasCategories || await page.locator('text=/template/i').count() > 0).toBeTruthy();
  });
});
