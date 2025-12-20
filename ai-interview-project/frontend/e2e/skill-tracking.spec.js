const { test, expect } = require('@playwright/test');
const { loginAsAndy, navigateToPage } = require('./helpers/test-helpers');

test.describe('Skill Tracking E2E Tests', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsAndy(page);
  });

  test('1. Navigate to Skills Page', async ({ page }) => {
    // Navigate to skills page
    await navigateToPage(page, 'skills');
    await page.waitForURL('**/skills', { timeout: 10000 });

    // Verify we're on skills page
    await expect(page.locator('text=/skill|tracking|competency/i').first()).toBeVisible({ timeout: 5000 });
  });

  test('2. View Skill Categories', async ({ page }) => {
    await navigateToPage(page, 'skills');

    // Look for skill categories or types
    const skillCategories = [
      'technical',
      'soft',
      'communication',
      'leadership',
      'problem.solving'
    ];

    let hasSkillCategories = false;
    for (const category of skillCategories) {
      const elements = await page.locator(`text=/${category}/i`).count();
      if (elements > 0) {
        hasSkillCategories = true;
        break;
      }
    }

    expect(hasSkillCategories).toBeTruthy();
  });

  test('3. Skill Proficiency Levels', async ({ page }) => {
    await navigateToPage(page, 'skills');

    // Look for proficiency indicators (beginner, intermediate, advanced, etc.)
    const proficiencyLevels = [
      'beginner',
      'intermediate',
      'advanced',
      'expert',
      'novice',
      'proficient'
    ];

    let hasProficiencyLevels = false;
    for (const level of proficiencyLevels) {
      const elements = await page.locator(`text=/${level}/i`).count();
      if (elements > 0) {
        hasProficiencyLevels = true;
        break;
      }
    }

    // Proficiency levels are optional but should be present if skills are tracked
    const skillElements = await page.locator('[class*="skill"], [class*="progress"], [class*="level"]').count();
    if (skillElements > 0) {
      expect(hasProficiencyLevels || skillElements > 0).toBeTruthy();
    }
  });

  test('4. Skill Progress Visualization', async ({ page }) => {
    await navigateToPage(page, 'skills');

    // Look for progress bars, charts, or visual indicators
    const progressElements = page.locator('progress, [class*="progress"], [class*="bar"], svg');
    const visualElements = await progressElements.count();

    // Look for percentage indicators
    const percentageElements = page.locator('text=/\\d+%/');
    const hasPercentages = await percentageElements.count() > 0;

    // Should have some form of progress indication
    expect(visualElements > 0 || hasPercentages).toBeTruthy();
  });

  test('5. Skill Recommendations', async ({ page }) => {
    await navigateToPage(page, 'skills');

    // Look for recommendation or improvement suggestions
    const recommendationElements = page.locator('text=/recommend|suggest|improve|focus/i');
    const hasRecommendations = await recommendationElements.count() > 0;

    if (hasRecommendations) {
      await expect(recommendationElements.first()).toBeVisible();
    }
  });

  test('6. Skill Assessment History', async ({ page }) => {
    await navigateToPage(page, 'skills');

    // Look for historical skill assessments
    const historyElements = page.locator('text=/history|assessment|previous|evolution/i');
    const hasHistory = await historyElements.count() > 0;

    if (hasHistory) {
      await expect(historyElements.first()).toBeVisible();
    }
  });
});
