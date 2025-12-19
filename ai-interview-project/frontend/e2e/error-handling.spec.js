const { test, expect } = require('@playwright/test');
const { loginAsAndy } = require('./helpers/test-helpers');

test.describe('Error Handling E2E Tests', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsAndy(page);
  });

  test('1. Network Error Handling', async ({ page }) => {
    // Simulate network failure
    await page.route('**/api/**', route => route.abort());
    
    // Try to navigate to a page that requires API call
    await page.goto('http://localhost:3000/');
    await page.waitForTimeout(2000);
    
    // Check for error message or retry button
    const errorMessage = page.locator('text=/error|failed|retry/i');
    const hasError = await errorMessage.count() > 0;
    expect(hasError).toBeTruthy();
  });

  test('2. Invalid Form Submission', async ({ page }) => {
    await page.goto('http://localhost:3000/');
    
    // Try to create interview with empty form
    const newInterviewBtn = page.locator('button:has-text("New Interview")');
    if (await newInterviewBtn.count() > 0) {
      await newInterviewBtn.click();
      await page.waitForTimeout(1000);
      
      // Try to submit without filling required fields
      const createButton = page.locator('button:has-text("Create")');
      if (await createButton.count() > 0) {
        await createButton.click();
        await page.waitForTimeout(1000);
        
        // Should show validation error
        const validationError = page.locator('text=/required|please|invalid/i');
        const hasValidationError = await validationError.count() > 0;
        expect(hasValidationError).toBeTruthy();
      }
    }
  });

  test('3. 404 Page Not Found', async ({ page }) => {
    await page.goto('http://localhost:3000/non-existent-page');
    await page.waitForTimeout(2000);
    
    // Should show 404 or not found message
    const notFoundMessage = page.locator('text=/404|not found|page not found/i');
    const hasNotFound = await notFoundMessage.count() > 0;
    expect(hasNotFound).toBeTruthy();
  });
});

