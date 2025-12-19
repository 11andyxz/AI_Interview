import React, { useState, useEffect } from 'react';
import { AlertTriangle, RefreshCw, Wifi, WifiOff, LogIn, ArrowLeft, Mail } from 'lucide-react';
import LoadingSpinner from './LoadingSpinner';
import errorHandler, { isOnline } from '../../utils/errorHandler';

const ErrorRetry = ({
  error,
  onRetry,
  onLogin,
  onBack,
  onContact,
  className = '',
  showIcon = true,
  compact = false
}) => {
  const [retrying, setRetrying] = useState(false);
  const [online, setOnline] = useState(isOnline());

  useEffect(() => {
    const handleOnline = () => setOnline(true);
    const handleOffline = () => setOnline(false);

    errorHandler.setOnlineCallbacks(handleOnline, handleOffline);

    return () => {
      errorHandler.setOnlineCallbacks(null, null);
    };
  }, []);

  const handleRetry = async () => {
    if (!onRetry) return;

    setRetrying(true);
    try {
      await onRetry();
    } finally {
      setRetrying(false);
    }
  };

  const getErrorIcon = (errorType) => {
    switch (errorType) {
      case 'offline':
        return online ? <Wifi className="text-green-600" /> : <WifiOff className="text-red-600" />;
      case 'auth':
        return <LogIn className="text-blue-600" />;
      case 'network':
        return <WifiOff className="text-orange-600" />;
      case 'server':
        return <AlertTriangle className="text-red-600" />;
      case 'timeout':
        return <RefreshCw className="text-yellow-600" />;
      case 'rate_limit':
        return <AlertTriangle className="text-yellow-600" />;
      case 'permission':
        return <AlertTriangle className="text-red-600" />;
      case 'not_found':
        return <AlertTriangle className="text-gray-600" />;
      default:
        return <AlertTriangle className="text-gray-600" />;
    }
  };

  const getActionButton = (action, disabled) => {
    const baseClasses = "inline-flex items-center gap-2 px-4 py-2 text-sm font-medium rounded-lg transition-colors";
    const disabledClasses = disabled ? "opacity-50 cursor-not-allowed" : "hover:bg-opacity-80";

    switch (action) {
      case 'retry':
        return (
          <button
            onClick={handleRetry}
            disabled={disabled || retrying}
            className={`${baseClasses} bg-purple-600 text-white ${disabledClasses}`}
          >
            {retrying ? <LoadingSpinner size="sm" /> : <RefreshCw size={16} />}
            {retrying ? 'Retrying...' : 'Try Again'}
          </button>
        );
      case 'login':
        return onLogin ? (
          <button
            onClick={onLogin}
            className={`${baseClasses} bg-blue-600 text-white ${disabledClasses}`}
          >
            <LogIn size={16} />
            Log In
          </button>
        ) : null;
      case 'back':
        return onBack ? (
          <button
            onClick={onBack}
            className={`${baseClasses} bg-gray-600 text-white ${disabledClasses}`}
          >
            <ArrowLeft size={16} />
            Go Back
          </button>
        ) : null;
      case 'contact':
        return onContact ? (
          <button
            onClick={onContact}
            className={`${baseClasses} bg-green-600 text-white ${disabledClasses}`}
          >
            <Mail size={16} />
            Contact Support
          </button>
        ) : null;
      default:
        return null;
    }
  };

  if (compact) {
    return (
      <div className={`flex items-center gap-3 p-3 bg-red-50 border border-red-200 rounded-lg ${className}`}>
        {showIcon && getErrorIcon(error.type)}
        <div className="flex-1">
          <p className="text-sm text-red-800">{error.message}</p>
        </div>
        {getActionButton(error.action, !online && error.type === 'offline')}
      </div>
    );
  }

  return (
    <div className={`text-center py-12 px-4 ${className}`}>
      <div className="max-w-md mx-auto">
        {showIcon && (
          <div className="flex justify-center mb-4">
            {getErrorIcon(error.type)}
          </div>
        )}

        <h3 className="text-lg font-semibold text-gray-900 mb-2">
          {error.title}
        </h3>

        <p className="text-gray-600 mb-6">
          {error.message}
        </p>

        <div className="flex justify-center gap-3">
          {getActionButton(error.action, !online && error.type === 'offline')}

          {/* Show online/offline status */}
          {!online && error.type !== 'offline' && (
            <div className="flex items-center gap-2 text-sm text-orange-600">
              <WifiOff size={16} />
              You're offline
            </div>
          )}

          {online && error.type === 'offline' && (
            <div className="flex items-center gap-2 text-sm text-green-600">
              <Wifi size={16} />
              Back online!
            </div>
          )}
        </div>

        {/* Additional help text */}
        {error.type === 'server' && (
          <p className="text-xs text-gray-500 mt-4">
            If this problem persists, please try again later or contact support.
          </p>
        )}

        {error.type === 'rate_limit' && (
          <p className="text-xs text-gray-500 mt-4">
            Rate limits are temporary and will reset automatically.
          </p>
        )}
      </div>
    </div>
  );
};

// Pre-configured error states for common scenarios
export const NetworkError = ({ onRetry, compact = false }) => (
  <ErrorRetry
    error={{
      type: 'network',
      title: 'Connection Error',
      message: 'Unable to connect to the server. Please check your connection.',
      action: 'retry'
    }}
    onRetry={onRetry}
    compact={compact}
  />
);

export const ServerError = ({ onRetry, compact = false }) => (
  <ErrorRetry
    error={{
      type: 'server',
      title: 'Server Error',
      message: 'Something went wrong on our end. Please try again later.',
      action: 'retry'
    }}
    onRetry={onRetry}
    compact={compact}
  />
);

export const OfflineError = ({ compact = false }) => (
  <ErrorRetry
    error={{
      type: 'offline',
      title: 'You are offline',
      message: 'Please check your internet connection and try again.',
      action: 'retry'
    }}
    compact={compact}
  />
);

export const AuthError = ({ onLogin, compact = false }) => (
  <ErrorRetry
    error={{
      type: 'auth',
      title: 'Authentication required',
      message: 'Please log in to continue.',
      action: 'login'
    }}
    onLogin={onLogin}
    compact={compact}
  />
);

export default ErrorRetry;
