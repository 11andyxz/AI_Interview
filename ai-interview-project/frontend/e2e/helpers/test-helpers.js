/**
 * E2E Test Helper Functions
 */

const BASE_URL = 'http://localhost:3000';
const API_BASE_URL = 'http://localhost:8080';

/**
 * Login as andy user
 * @param {import('@playwright/test').Page} page
 * @returns {Promise<void>}
 */
async function loginAsAndy(page) {
  await page.goto('http://localhost:3000/login');
  await page.fill('input[name="username"], input[type="text"]', 'andy');
  await page.fill('input[name="password"], input[type="password"]', '123456789');
  await page.click('button:has-text("Login"), button[type="submit"]');
  // Wait for navigation to home page (which is the dashboard)
  await page.waitForURL('http://localhost:3000/', { timeout: 10000 });
  // Wait a bit for the page to fully load
  await page.waitForTimeout(1000);
}

/**
 * Wait for API response
 * @param {import('@playwright/test').Page} page
 * @param {string} urlPattern
 * @param {number} timeout
 * @returns {Promise<any>}
 */
async function waitForApiResponse(page, urlPattern, timeout = 10000) {
  const response = await page.waitForResponse(
    (response) => response.url().includes(urlPattern) && response.status() === 200,
    { timeout }
  );
  return response.json();
}

/**
 * Create an interview
 * @param {import('@playwright/test').Page} page
 * @param {Object} data
 * @returns {Promise<string>} interviewId
 */
async function createInterview(page, data = {}) {
  // Click New Interview button
  await page.click('button:has-text("New Interview"), button:has-text("Create Interview")');
  
  // Wait for modal
  await page.waitForSelector('input, select', { timeout: 5000 });
  
  // Fill form if provided
  if (data.positionType) {
    await page.fill('input[name="positionType"], input[placeholder*="Position"]', data.positionType);
  }
  if (data.language) {
    await page.selectOption('select[name="language"], select', { label: data.language });
  }
  
  // Click create button
  await page.click('button:has-text("Create"), button:has-text("Submit")');
  
  // Wait for navigation or success message
  await page.waitForTimeout(2000);
  
  // Extract interview ID from URL if navigated
  const url = page.url();
  const match = url.match(/\/interview\/([^\/]+)/);
  return match ? match[1] : null;
}

/**
 * Create a note
 * @param {import('@playwright/test').Page} page
 * @param {Object} data
 * @returns {Promise<void>}
 */
async function createNote(page, data = {}) {
  await page.click('button:has-text("New Note"), button:has-text("Create Note")');
  await page.waitForSelector('input[name="title"]', { timeout: 5000 });
  
  await page.fill('input[name="title"]', data.title || 'Test Note');
  if (data.content) {
    await page.fill('textarea[name="content"], textarea', data.content);
  }
  
  await page.click('button:has-text("Create"), button:has-text("Save")');
  await page.waitForTimeout(1000);
}

/**
 * Upload resume
 * @param {import('@playwright/test').Page} page
 * @param {string} resumeText
 * @returns {Promise<void>}
 */
async function uploadResume(page, resumeText) {
  await page.click('button:has-text("Upload Resume"), button:has-text("Add Resume")');
  await page.waitForSelector('textarea, input[type="file"]', { timeout: 5000 });
  
  // If textarea for resume text
  const textarea = page.locator('textarea[name="resumeText"], textarea');
  if (await textarea.count() > 0) {
    await textarea.fill(resumeText);
    await page.click('button:has-text("Upload"), button:has-text("Submit")');
  }
  
  await page.waitForTimeout(1000);
}

/**
 * Wait for toast message
 * @param {import('@playwright/test').Page} page
 * @param {string} message
 * @param {number} timeout
 * @returns {Promise<void>}
 */
async function waitForToast(page, message, timeout = 5000) {
  await page.waitForSelector(`text=${message}`, { timeout, state: 'visible' });
}

/**
 * Check if element is visible
 * @param {import('@playwright/test').Page} page
 * @param {string} selector
 * @returns {Promise<boolean>}
 */
async function isVisible(page, selector) {
  const count = await page.locator(selector).count();
  return count > 0 && await page.locator(selector).first().isVisible();
}

module.exports = {
  loginAsAndy,
  waitForApiResponse,
  createInterview,
  createNote,
  uploadResume,
  waitForToast,
  isVisible,
  BASE_URL,
  API_BASE_URL,
};

