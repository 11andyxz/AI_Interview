// API interceptor for automatic token refresh and 401 handling

import { refreshAccessToken, logout } from './auth';

// Intercept fetch calls to handle 401 errors
const originalFetch = window.fetch;

window.fetch = async function(...args) {
  const [url, options = {}] = args;
  
  // Only intercept API calls to our backend
  if (typeof url === 'string' && url.startsWith('http://localhost:8080/api/')) {
    // Skip public endpoints
    const publicEndpoints = [
      '/api/auth/login',
      '/api/auth/register',
      '/api/health',
      '/api/payment/webhook'
    ];
    
    const isPublic = publicEndpoints.some(endpoint => url.includes(endpoint));
    
    if (!isPublic) {
      const accessToken = localStorage.getItem('accessToken');
      
      // Check if body is FormData - if so, don't set Content-Type (let browser set it)
      const isFormData = options.body instanceof FormData;
      
      // Add token to headers
      const headers = {
        ...options.headers,
      };
      
      // Only set Content-Type if not FormData and not already set
      if (!isFormData && !headers['Content-Type']) {
        headers['Content-Type'] = 'application/json';
      }
      
      if (accessToken) {
        headers['Authorization'] = `Bearer ${accessToken}`;
      }
      
      let response = await originalFetch(url, {
        ...options,
        headers
      });
      
      // Handle 401 - try to refresh token
      if (response.status === 401) {
        try {
          const newAccessToken = await refreshAccessToken();
          headers['Authorization'] = `Bearer ${newAccessToken}`;
          
          // Retry the request
          response = await originalFetch(url, {
            ...options,
            headers
          });
          
          // If still 401, logout
          if (response.status === 401) {
            logout();
            return response;
          }
        } catch (error) {
          // Refresh failed, logout
          logout();
          return response;
        }
      }
      
      return response;
    }
  }
  
  // For non-API calls, use original fetch
  return originalFetch.apply(this, args);
};

