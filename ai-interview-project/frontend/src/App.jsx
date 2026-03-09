import React, { useState, useEffect } from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate, useNavigate } from 'react-router-dom';
import DashboardLayout from './components/DashboardLayout';
import InterviewRoom from './components/InterviewRoom';
import Login from './components/Login';
import RegisterPage from './components/RegisterPage';
import PaymentSuccessPage from './components/PaymentSuccessPage';
import PaymentCancelPage from './components/PaymentCancelPage';
import ReportPage from './components/ReportPage';
import NotFoundPage from './components/NotFoundPage';
import { useGlobalShortcuts } from './hooks/useKeyboardShortcuts';
import KeyboardShortcutsHelp from './components/KeyboardShortcutsHelp';
import ErrorBoundary from './components/common/ErrorBoundary';
import OnboardingGuide from './components/OnboardingGuide';

// 路由保护组件
const ProtectedRoute = ({ children }) => {
  const isAuthenticated = localStorage.getItem('isAuthenticated') === 'true';
  return isAuthenticated ? children : <Navigate to="/login" replace />;
};

// Global shortcuts provider component
const GlobalShortcutsProvider = ({ children }) => {
  const [showKeyboardHelp, setShowKeyboardHelp] = useState(false);
  const navigate = useNavigate();

  const handleShowHelp = () => {
    setShowKeyboardHelp(true);
  };

  const handleGoHome = () => {
    navigate('/');
  };

  const handleGoProgress = () => {
    navigate('/progress');
  };

  const handleGoSkills = () => {
    navigate('/skills');
  };

  const handleGoSettings = () => {
    navigate('/settings');
  };

  useGlobalShortcuts({
    onHelp: handleShowHelp,
    onGoHome: handleGoHome,
    onGoProgress: handleGoProgress,
    onGoSkills: handleGoSkills,
    onGoSettings: handleGoSettings
  });

  return (
    <>
      {children}
      <KeyboardShortcutsHelp
        isOpen={showKeyboardHelp}
        onClose={() => setShowKeyboardHelp(false)}
      />
    </>
  );
};

function App() {
  const [showOnboarding, setShowOnboarding] = useState(false);

  useEffect(() => {
    // Check if user is authenticated and hasn't completed onboarding
    const isAuthenticated = localStorage.getItem('isAuthenticated') === 'true';
    const hasCompletedOnboarding = localStorage.getItem('onboardingCompleted') === 'true';

    if (isAuthenticated && !hasCompletedOnboarding) {
      // Delay showing onboarding to allow page to load
      const timer = setTimeout(() => {
        setShowOnboarding(true);
      }, 2000);
      return () => clearTimeout(timer);
    }
  }, []);

  const handleOnboardingComplete = () => {
    setShowOnboarding(false);
  };

  return (
    <ErrorBoundary>
      <Router>
        <GlobalShortcutsProvider>
        <OnboardingGuide
          run={showOnboarding}
          onComplete={handleOnboardingComplete}
        />
        <Routes>
          {/* 登录和注册页面 */}
          <Route path="/login" element={<Login />} />
          <Route path="/register" element={<RegisterPage />} />

          {/* Dashboard Layout with Sidebar */}
          <Route path="/*" element={
            <ProtectedRoute>
              <DashboardLayout />
            </ProtectedRoute>
          } />

          {/* Standalone Interview Room (No Sidebar) */}
          <Route path="/interview/:id" element={
            <ProtectedRoute>
              <InterviewRoom />
            </ProtectedRoute>
          } />

          {/* Payment callback pages */}
          <Route path="/payment/success" element={
            <ProtectedRoute>
              <PaymentSuccessPage />
            </ProtectedRoute>
          } />
          <Route path="/payment/cancel" element={
            <ProtectedRoute>
              <PaymentCancelPage />
            </ProtectedRoute>
          } />

          {/* Standalone report page */}
          <Route path="/report/:id" element={
            <ProtectedRoute>
              <ReportPage />
            </ProtectedRoute>
          } />

          {/* 404 Page */}
          <Route path="*" element={<NotFoundPage />} />
        </Routes>
        </GlobalShortcutsProvider>
      </Router>
    </ErrorBoundary>
  );
}

export default App;
