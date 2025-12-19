// Error handling utilities with retry mechanisms and offline detection

class ErrorHandler {
  constructor() {
    this.retryConfig = {
      maxRetries: 3,
      baseDelay: 1000,
      maxDelay: 10000,
      backoffFactor: 2
    };
    this.isOnline = navigator.onLine;
    this.setupOnlineDetection();
  }

  setupOnlineDetection() {
    window.addEventListener('online', () => {
      this.isOnline = true;
      this.onReconnect && this.onReconnect();
    });

    window.addEventListener('offline', () => {
      this.isOnline = false;
      this.onDisconnect && this.onDisconnect();
    });
  }

  setOnlineCallbacks(onReconnect, onDisconnect) {
    this.onReconnect = onReconnect;
    this.onDisconnect = onDisconnect;
  }

  /**
   * Check if the error is retryable
   */
  isRetryableError(error) {
    // Network errors
    if (!this.isOnline) return false;

    // HTTP status codes that are retryable
    const retryableStatuses = [408, 429, 500, 502, 503, 504];

    if (error.status && retryableStatuses.includes(error.status)) {
      return true;
    }

    // Network errors (fetch failures)
    if (error.name === 'TypeError' && error.message.includes('fetch')) {
      return true;
    }

    // Timeout errors
    if (error.name === 'AbortError') {
      return true;
    }

    return false;
  }

  /**
   * Calculate delay for retry with exponential backoff
   */
  calculateRetryDelay(attempt) {
    const delay = this.retryConfig.baseDelay * Math.pow(this.retryConfig.backoffFactor, attempt - 1);
    return Math.min(delay, this.retryConfig.maxDelay);
  }

  /**
   * Execute a function with retry logic
   */
  async withRetry(fn, options = {}) {
    const config = { ...this.retryConfig, ...options };
    let lastError;

    for (let attempt = 0; attempt <= config.maxRetries; attempt++) {
      try {
        return await fn();
      } catch (error) {
        lastError = error;

        if (attempt < config.maxRetries && this.isRetryableError(error)) {
          const delay = this.calculateRetryDelay(attempt);
          console.warn(`Attempt ${attempt + 1} failed, retrying in ${delay}ms:`, error);

          // Add jitter to prevent thundering herd
          const jitteredDelay = delay + Math.random() * 1000;
          await new Promise(resolve => setTimeout(resolve, jitteredDelay));
          continue;
        }

        break;
      }
    }

    throw lastError;
  }

  /**
   * Format error for user display
   */
  formatError(error) {
    if (!this.isOnline) {
      return {
        type: 'offline',
        title: 'You are offline',
        message: 'Please check your internet connection and try again.',
        action: 'retry'
      };
    }

    if (error.status === 401) {
      return {
        type: 'auth',
        title: 'Authentication required',
        message: 'Please log in to continue.',
        action: 'login'
      };
    }

    if (error.status === 403) {
      return {
        type: 'permission',
        title: 'Access denied',
        message: 'You don\'t have permission to perform this action.',
        action: 'contact'
      };
    }

    if (error.status === 404) {
      return {
        type: 'not_found',
        title: 'Not found',
        message: 'The requested resource was not found.',
        action: 'back'
      };
    }

    if (error.status >= 500) {
      return {
        type: 'server',
        title: 'Server error',
        message: 'Something went wrong on our end. Please try again later.',
        action: 'retry'
      };
    }

    if (error.status === 429) {
      return {
        type: 'rate_limit',
        title: 'Too many requests',
        message: 'Please wait a moment before trying again.',
        action: 'retry'
      };
    }

    // Network error
    if (error.name === 'TypeError' && error.message.includes('fetch')) {
      return {
        type: 'network',
        title: 'Connection error',
        message: 'Unable to connect to the server. Please check your connection.',
        action: 'retry'
      };
    }

    // Timeout
    if (error.name === 'AbortError') {
      return {
        type: 'timeout',
        title: 'Request timeout',
        message: 'The request took too long. Please try again.',
        action: 'retry'
      };
    }

    // Default error
    return {
      type: 'unknown',
      title: 'Something went wrong',
      message: error.message || 'An unexpected error occurred.',
      action: 'retry'
    };
  }

  /**
   * Handle API errors globally
   */
  handleApiError(error, context = {}) {
    const formattedError = this.formatError(error);

    // Log error for debugging
    console.error('API Error:', {
      error,
      formattedError,
      context,
      timestamp: new Date().toISOString(),
      userAgent: navigator.userAgent,
      url: window.location.href
    });

    return formattedError;
  }
}

// Create singleton instance
const errorHandler = new ErrorHandler();

export default errorHandler;

// Utility functions for common use cases
export const withRetry = (fn, options) => errorHandler.withRetry(fn, options);
export const formatError = (error) => errorHandler.formatError(error);
export const handleApiError = (error, context) => errorHandler.handleApiError(error, context);
export const isOnline = () => errorHandler.isOnline;
export const setOnlineCallbacks = (onReconnect, onDisconnect) =>
  errorHandler.setOnlineCallbacks(onReconnect, onDisconnect);
