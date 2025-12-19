// API configuration and utility functions

export const API_BASE_URL = process.env.REACT_APP_API_BASE_URL || 'http://localhost:8080';
export const WS_BASE_URL = process.env.REACT_APP_WS_BASE_URL || 'http://localhost:8080';

// API endpoints
export const API_ENDPOINTS = {
  // Auth
  LOGIN: '/api/auth/login',
  REGISTER: '/api/auth/register',

  // User
  USER_PROFILE: '/api/user/profile',
  USER_POINTS: '/api/user/points',
  USER_SUBSCRIPTION_STATUS: '/api/user/subscription-status',

  // Interviews
  INTERVIEWS: '/api/interviews',
  INTERVIEW_SESSION: (id) => `/api/interviews/${id}/session`,
  INTERVIEW_HISTORY: (id) => `/api/interviews/${id}/history`,
  INTERVIEW_CHAT: (id) => `/api/interviews/${id}/chat`,
  INTERVIEW_END: (id) => `/api/interviews/${id}/end`,
  INTERVIEW_REPORT: (id) => `/api/interviews/${id}/report`,
  INTERVIEW_REPORT_JSON: (id) => `/api/interviews/${id}/report/json`,
  INTERVIEW_REPORT_DOWNLOAD: (id) => `/api/interviews/${id}/report/download`,

  // Notes
  NOTES: '/api/notes',

  // Resume
  USER_RESUME: '/api/user/resume',
  USER_RESUME_DOWNLOAD: (id) => `/api/user/resume/${id}/download`,
  USER_RESUME_ANALYZE: (id) => `/api/user/resume/${id}/analyze`,

  // Knowledge Base
  KNOWLEDGE_BASE: '/api/knowledge-base',
  KNOWLEDGE_BASE_SYSTEM: '/api/knowledge-base/system',

  // Mock Interviews
  MOCK_INTERVIEWS: '/api/mock-interviews',
  MOCK_INTERVIEW_DETAIL: (id) => `/api/mock-interviews/${id}`,
  MOCK_INTERVIEW_RETRY: (id) => `/api/mock-interviews/${id}/retry`,
  MOCK_INTERVIEW_HINTS: (id) => `/api/mock-interviews/${id}/hints`,

  // Candidates
  CANDIDATES: '/api/resume/candidates',

  // Payment
  PAYMENT_PLANS: '/api/payment/plans',
  PAYMENT_CHECKOUT: '/api/payment/checkout',
  PAYMENT_SUBSCRIPTIONS: '/api/payment/subscriptions',
  PAYMENT_HISTORY: '/api/payment/history',
};

// Utility function to build full URL
export const buildUrl = (endpoint) => {
  return `${API_BASE_URL}${endpoint}`;
};

// Common fetch options with auth
export const getAuthHeaders = () => {
  const accessToken = localStorage.getItem('accessToken');
  const headers = {
    'Content-Type': 'application/json',
  };
  if (accessToken) {
    headers['Authorization'] = `Bearer ${accessToken}`;
  }
  return headers;
};

// Common API call wrapper with error handling
export const apiCall = async (endpoint, options = {}) => {
  const url = typeof endpoint === 'string' ? buildUrl(endpoint) : buildUrl(endpoint.url);
  const headers = { ...getAuthHeaders(), ...options.headers };

  try {
    const response = await fetch(url, {
      ...options,
      headers
    });

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: ${response.statusText}`);
    }

    return await response.json();
  } catch (error) {
    console.error('API call failed:', error);
    throw error;
  }
};

// WebSocket URL builder
export const buildWebSocketUrl = (endpoint) => {
  return `${WS_BASE_URL}${endpoint}`;
};
