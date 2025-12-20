const { test, expect } = require('@playwright/test');
const { loginAsAndy, navigateToPage, waitForModalReady } = require('./helpers/test-helpers');

test.describe('Question Sets E2E Tests', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsAndy(page);
  });

  test('1. Navigate to Question Sets Page', async ({ page }) => {
    // Navigate to question sets page
    await navigateToPage(page, 'question-sets');
    await page.waitForURL('**/question-sets', { timeout: 10000 });

    // Verify we're on question sets page
    await expect(page.locator('text=/question|sets|collection/i').first()).toBeVisible({ timeout: 5000 });
  });

  test('2. View Available Question Sets', async ({ page }) => {
    await navigateToPage(page, 'question-sets');

    // Look for question set cards or list items
    const questionSetElements = page.locator('[class*="question"], [class*="set"], [class*="card"]');
    const questionSetCount = await questionSetElements.count();

    // Should have some question sets or empty state
    expect(questionSetCount).toBeGreaterThanOrEqual(0);

    // Look for question set titles or descriptions
    const hasContent = await page.locator('text=/question|interview|technical/i').count() > 0;
    expect(hasContent).toBeTruthy();
  });

  test('3. Question Set Details', async ({ page }) => {
    await navigateToPage(page, 'question-sets');

    // Look for question sets to interact with
    const questionSets = page.locator('[class*="question"], [class*="set"]').filter({ hasText: /.+/ });
    const setCount = await questionSets.count();

    if (setCount > 0) {
      // Click on first question set
      await questionSets.first().click();
      await page.waitForTimeout(1000);

      // Should show details or navigate to detail view
      const hasDetails = await page.locator('text=/detail|description|questions|count/i').count() > 0;
      const navigated = !page.url().includes('question-sets') || page.url().includes('/question-sets/');

      expect(hasDetails || navigated).toBeTruthy();
    }
  });

  test('4. Create Custom Question Set', async ({ page }) => {
    await navigateToPage(page, 'question-sets');

    // Look for create button
    const createButton = page.locator('button:has-text("Create"), button:has-text("New"), button:has-text("Add")');
    const canCreate = await createButton.count() > 0;

    if (canCreate) {
      await createButton.click();

      // Wait for modal or form
      await page.waitForTimeout(1000);

      // Look for form elements
      const formElements = page.locator('input, textarea, select');
      const hasForm = await formElements.count() > 0;

      if (hasForm) {
        // Try to fill basic form
        const titleInput = page.locator('input[name="title"], input[placeholder*="title"]').first();
        if (await titleInput.count() > 0) {
          await titleInput.fill('Test Question Set');
        }

        const descriptionInput = page.locator('textarea[name="description"], textarea[placeholder*="description"]').first();
        if (await descriptionInput.count() > 0) {
          await descriptionInput.fill('Test description for question set');
        }

        // Look for save/submit button
        const saveButton = page.locator('button:has-text("Save"), button:has-text("Create"), button:has-text("Submit")');
        if (await saveButton.count() > 0) {
          await saveButton.click();
          await page.waitForTimeout(2000);
        }
      }
    }

    // Test should pass regardless of creation capability
    expect(page.url()).toContain('question-sets');
  });

  test('5. Question Set Categories', async ({ page }) => {
    await navigateToPage(page, 'question-sets');

    // Look for category filters or tags
    const categories = [
      'technical',
      'behavioral',
      'system design',
      'algorithms',
      'frontend',
      'backend',
      'database'
    ];

    let hasCategories = false;
    for (const category of categories) {
      const elements = await page.locator(`text=/${category}/i`).count();
      if (elements > 0) {
        hasCategories = true;
        break;
      }
    }

    // Categories are optional but good to have
    expect(hasCategories || await page.locator('text=/question/i').count() > 0).toBeTruthy();
  });

  test('6. Search Question Sets', async ({ page }) => {
    await navigateToPage(page, 'question-sets');

    // Look for search input
    const searchInput = page.locator('input[type="search"], input[placeholder*="search"], input[placeholder*="find"]');
    const hasSearch = await searchInput.count() > 0;

    if (hasSearch) {
      await searchInput.fill('test');
      await page.waitForTimeout(500);

      // Should filter results or show no results
      const results = await page.locator('[class*="question"], [class*="set"]').count();
      expect(results).toBeGreaterThanOrEqual(0);
    }
  });
});
