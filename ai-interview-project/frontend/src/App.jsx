import React, { useState, useEffect } from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate, useNavigate } from 'react-router-dom';
import DashboardLayout from './components/DashboardLayout';
import InterviewRoom from './components/InterviewRoom';
import Login from './components/Login';
import RegisterPage from './components/RegisterPage';
import PaymentPage from './components/PaymentPage';
import PaymentSuccessPage from './components/PaymentSuccessPage';
import PaymentCancelPage from './components/PaymentCancelPage';
import NotesPage from './components/NotesPage';
import ResumePage from './components/ResumePage';
import KnowledgeBasePage from './components/KnowledgeBasePage';
import MockInterviewPage from './components/MockInterviewPage';
import ReportPage from './components/ReportPage';
import UserProfilePage from './components/UserProfilePage';
import ProgressDashboard from './components/ProgressDashboard';
import SkillProgressPage from './components/SkillProgressPage';
import SettingsPage from './components/SettingsPage';
import CustomQuestionSetPage from './components/CustomQuestionSetPage';
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
          <Route path="/" element={
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

          {/* Payment Pages */}
          <Route path="/payment" element={
            <ProtectedRoute>
              <PaymentPage />
            </ProtectedRoute>
          } />
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

          {/* Feature Pages */}
          <Route path="/notes" element={
            <ProtectedRoute>
              <NotesPage />
            </ProtectedRoute>
          } />
          <Route path="/resume" element={
            <ProtectedRoute>
              <ResumePage />
            </ProtectedRoute>
          } />
          <Route path="/knowledge-base" element={
            <ProtectedRoute>
              <KnowledgeBasePage />
            </ProtectedRoute>
          } />
          <Route path="/mock-interview" element={
            <ProtectedRoute>
              <MockInterviewPage />
            </ProtectedRoute>
          } />
          <Route path="/mock-interview/:id" element={
            <ProtectedRoute>
              <MockInterviewPage />
            </ProtectedRoute>
          } />
          <Route path="/report/:id" element={
            <ProtectedRoute>
              <ReportPage />
            </ProtectedRoute>
          } />
          <Route path="/profile" element={
            <ProtectedRoute>
              <UserProfilePage />
            </ProtectedRoute>
          } />
          <Route path="/progress" element={
            <ProtectedRoute>
              <ProgressDashboard />
            </ProtectedRoute>
          } />
          <Route path="/skills" element={
            <ProtectedRoute>
              <SkillProgressPage />
            </ProtectedRoute>
          } />
          <Route path="/settings" element={
            <ProtectedRoute>
              <SettingsPage />
            </ProtectedRoute>
          } />
          <Route path="/question-sets" element={
            <ProtectedRoute>
              <CustomQuestionSetPage />
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
