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

/**
 * Wait for modal to appear and be ready
 * @param {import('@playwright/test').Page} page
 * @param {number} timeout
 * @returns {Promise<void>}
 */
async function waitForModalReady(page, timeout = 10000) {
  await page.waitForSelector('[data-testid="modal-backdrop"]', { timeout });
  await page.waitForTimeout(1500); // Give modal time to fully render
}

/**
 * Fill interview creation form
 * @param {import('@playwright/test').Page} page
 * @param {Object} data
 * @returns {Promise<void>}
 */
async function fillInterviewForm(page, data = {}) {
  // Fill position type
  if (data.positionType) {
    await page.fill('[data-testid="position-type-input"]', data.positionType);
  }

  // Select programming languages
  if (data.programmingLanguages && data.programmingLanguages.length > 0) {
    for (const lang of data.programmingLanguages) {
      const langButton = page.locator('[data-testid="language-button"]').filter({ hasText: lang });
      if (await langButton.count() > 0) {
        await langButton.click();
      }
    }
  }

  // Select language
  if (data.language) {
    await page.selectOption('[data-testid="interview-language"]', { label: data.language });
  }

  // Agree to terms if specified
  if (data.agreeTerms) {
    const termsCheckbox = page.locator('input[type="checkbox"]').last();
    if (await termsCheckbox.count() > 0) {
      await termsCheckbox.check();
    }
  }
}

/**
 * Navigate to a specific page using sidebar navigation
 * @param {import('@playwright/test').Page} page
 * @param {string} pageName
 * @returns {Promise<void>}
 */
async function navigateToPage(page, pageName) {
  const navMap = {
    dashboard: 'nav-dashboard',
    progress: 'nav-progress',
    skills: 'nav-skills',
    settings: 'nav-settings',
    'question-sets': 'nav-question-sets',
    notes: 'nav-notes',
    'mock-interview': 'nav-mock-interview',
    resume: 'nav-resume',
    'knowledge-base': 'nav-knowledge-base',
    payment: 'nav-payment',
    profile: 'nav-profile'
  };

  const testId = navMap[pageName];
  if (!testId) {
    throw new Error(`Unknown page: ${pageName}`);
  }

  await page.click(`[data-testid="${testId}"]`);
}

/**
 * Create a complete interview flow
 * @param {import('@playwright/test').Page} page
 * @param {Object} interviewData
 * @returns {Promise<string>} interviewId
 */
async function createCompleteInterview(page, interviewData = {}) {
  // Click New Interview button
  await page.click('[data-testid="sidebar-new-interview-button"]');

  // Wait for modal
  await waitForModalReady(page);

  // Fill form
  await fillInterviewForm(page, {
    positionType: interviewData.positionType || 'Backend Java Developer',
    programmingLanguages: interviewData.programmingLanguages || ['Java'],
    language: interviewData.language || 'English',
    agreeTerms: true
  });

  // Submit
  await page.click('[data-testid="create-interview-button"]');

  // Wait for navigation
  await page.waitForURL('**/interview/**', { timeout: 15000 });

  // Extract interview ID
  const url = page.url();
  const match = url.match(/\/interview\/([^\/]+)/);
  return match ? match[1] : null;
}

/**
 * Page Object for Dashboard
 */
class DashboardPage {
  constructor(page) {
    this.page = page;
  }

  async navigate() {
    await this.page.click('[data-testid="nav-dashboard"]');
    await this.page.waitForURL('**/');
  }

  async getInterviewCount() {
    return await this.page.locator('[data-testid="interview-card"]').count();
  }

  async clickNewInterview() {
    await this.page.click('[data-testid="sidebar-new-interview-button"]');
  }

  async filterByStatus(status) {
    await this.page.selectOption('[data-testid="interview-filter"]', status);
  }

  async sortBy(criteria) {
    await this.page.selectOption('[data-testid="interview-sort"]', criteria);
  }

  async searchInterviews(query) {
    await this.page.fill('[data-testid="interview-search"]', query);
  }

  async clickInterviewCard(index = 0) {
    await this.page.locator('[data-testid="interview-card"]').nth(index).click();
  }
}

/**
 * Page Object for Interview Room
 */
class InterviewRoomPage {
  constructor(page) {
    this.page = page;
  }

  async sendMessage(message) {
    await this.page.fill('[data-testid="chat-input"]', message);
    await this.page.click('[data-testid="send-message-button"]');
  }

  async endInterview() {
    await this.page.click('[data-testid="end-interview-button"]');
    // Handle confirmation dialog
    const confirmButton = this.page.locator('button:has-text("End Interview")');
    if (await confirmButton.count() > 0) {
      await confirmButton.click();
    }
  }

  async waitForAIResponse(timeout = 10000) {
    await this.page.waitForTimeout(2000); // Basic wait for AI response
  }
}

module.exports = {
  loginAsAndy,
  waitForApiResponse,
  createInterview,
  createNote,
  uploadResume,
  waitForToast,
  isVisible,
  waitForModalReady,
  fillInterviewForm,
  navigateToPage,
  createCompleteInterview,
  DashboardPage,
  InterviewRoomPage,
  BASE_URL,
  API_BASE_URL,
};

