import React from 'react';
import { AlertCircle, RefreshCw, Home, Bug, Copy } from 'lucide-react';
import { useToast } from './useToast';

class ErrorBoundary extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      hasError: false,
      error: null,
      errorInfo: null,
      errorId: null
    };
  }

  static getDerivedStateFromError(error) {
    return {
      hasError: true,
      error,
      errorId: Date.now().toString()
    };
  }

  componentDidCatch(error, errorInfo) {
    this.setState({ errorInfo });

    // Log error details for debugging
    const errorDetails = {
      errorId: this.state.errorId,
      message: error.message,
      stack: error.stack,
      componentStack: errorInfo.componentStack,
      timestamp: new Date().toISOString(),
      userAgent: navigator.userAgent,
      url: window.location.href,
      userId: localStorage.getItem('user') ? JSON.parse(localStorage.getItem('user')).id : 'unknown'
    };

    console.error('Error caught by boundary:', errorDetails);

    // In production, you would send this to an error reporting service
    // Example: errorReportingService.report(errorDetails);
  }

  handleRetry = () => {
    this.setState({
      hasError: false,
      error: null,
      errorInfo: null,
      errorId: null
    });
  };

  handleGoHome = () => {
    this.setState({
      hasError: false,
      error: null,
      errorInfo: null,
      errorId: null
    });
    window.location.href = '/';
  };

  handleCopyError = () => {
    const errorText = `
Error ID: ${this.state.errorId}
Time: ${new Date().toISOString()}
URL: ${window.location.href}
User Agent: ${navigator.userAgent}

Error: ${this.state.error?.message}
Stack: ${this.state.error?.stack}

Component Stack:
${this.state.errorInfo?.componentStack}
    `.trim();

    navigator.clipboard.writeText(errorText).then(() => {
      // Could show a toast notification here
      alert('Error details copied to clipboard');
    }).catch(() => {
      alert('Failed to copy error details');
    });
  };

  render() {
    if (this.state.hasError) {
      const isDevelopment = process.env.NODE_ENV === 'development';

      return (
        <div className="min-h-screen bg-gray-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-lg shadow-lg p-8 max-w-2xl w-full">
            <div className="text-center mb-6">
              <AlertCircle className="mx-auto text-red-600 mb-4" size={48} />
              <h2 className="text-2xl font-bold text-gray-900 mb-2">
                Oops! Something went wrong
              </h2>
              <p className="text-gray-600">
                We encountered an unexpected error. Don't worry, your data is safe.
              </p>
            </div>

            <div className="space-y-4 mb-6">
              {/* Error ID for support */}
              <div className="bg-gray-50 p-3 rounded-lg">
                <p className="text-sm text-gray-600">
                  Error ID: <code className="bg-gray-200 px-2 py-1 rounded text-xs">{this.state.errorId}</code>
                </p>
              </div>

              {/* Error message */}
              <div className="bg-red-50 border border-red-200 p-4 rounded-lg">
                <h3 className="font-medium text-red-800 mb-2">Error Details</h3>
                <p className="text-sm text-red-700">
                  {this.state.error?.message || 'An unexpected error occurred'}
                </p>
              </div>

              {/* Development mode: show stack trace */}
              {isDevelopment && (
                <details className="bg-gray-50 p-4 rounded-lg">
                  <summary className="cursor-pointer font-medium text-gray-900 mb-2">
                    Technical Details (Development Only)
                  </summary>
                  <div className="text-xs font-mono text-gray-700 bg-white p-3 rounded border overflow-auto max-h-48">
                    <pre>{this.state.error?.stack}</pre>
                    {this.state.errorInfo && (
                      <>
                        <br />
                        <strong>Component Stack:</strong>
                        <pre>{this.state.errorInfo.componentStack}</pre>
                      </>
                    )}
                  </div>
                </details>
              )}
            </div>

            {/* Action buttons */}
            <div className="flex flex-wrap gap-3 justify-center">
              <button
                onClick={this.handleRetry}
                className="inline-flex items-center gap-2 px-4 py-2 bg-purple-600 text-white rounded-lg hover:bg-purple-700 transition-colors"
              >
                <RefreshCw size={16} />
                Try Again
              </button>

              <button
                onClick={this.handleGoHome}
                className="inline-flex items-center gap-2 px-4 py-2 bg-gray-600 text-white rounded-lg hover:bg-gray-700 transition-colors"
              >
                <Home size={16} />
                Go to Home
              </button>

              <button
                onClick={this.handleCopyError}
                className="inline-flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
              >
                <Copy size={16} />
                Copy Error Details
              </button>
            </div>

            {/* Support information */}
            <div className="mt-6 pt-6 border-t border-gray-200 text-center">
              <p className="text-sm text-gray-600 mb-2">
                If this problem persists, please contact our support team.
              </p>
              <p className="text-xs text-gray-500">
                Include the Error ID above when reporting this issue.
              </p>
            </div>
          </div>
        </div>
      );
    }

    return this.props.children;
  }
}

export default ErrorBoundary;

