import React from 'react';

const LoadingSpinner = ({
  size = 'md',
  className = '',
  color = 'purple',
  thickness = '4',
  overlay = false
}) => {
  const sizes = {
    xs: 'w-3 h-3',
    sm: 'w-4 h-4',
    md: 'w-8 h-8',
    lg: 'w-12 h-12',
    xl: 'w-16 h-16'
  };

  const colors = {
    purple: 'border-purple-600',
    blue: 'border-blue-600',
    green: 'border-green-600',
    red: 'border-red-600',
    gray: 'border-gray-600'
  };

  const spinner = (
    <div className={`${sizes[size]} ${className}`}>
      <div className={`animate-spin rounded-full border-${thickness} border-gray-200 border-t-${colors[color] || colors.purple}`}></div>
    </div>
  );

  if (overlay) {
    return (
      <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
        {spinner}
      </div>
    );
  }

  return spinner;
};

// Loading overlay component for full page loading
export const LoadingOverlay = ({ message = 'Loading...', size = 'lg' }) => (
  <div className="fixed inset-0 bg-black bg-opacity-50 flex flex-col items-center justify-center z-50">
    <LoadingSpinner size={size} />
    {message && (
      <p className="mt-4 text-white text-sm font-medium">{message}</p>
    )}
  </div>
);

// Inline loading component for buttons and small areas
export const InlineLoading = ({ size = 'sm', className = '' }) => (
  <LoadingSpinner size={size} className={`inline-block ${className}`} />
);

export default LoadingSpinner;

