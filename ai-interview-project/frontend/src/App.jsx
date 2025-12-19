import React from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
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
import NotFoundPage from './components/NotFoundPage';
import ErrorBoundary from './components/common/ErrorBoundary';

// 路由保护组件
const ProtectedRoute = ({ children }) => {
  const isAuthenticated = localStorage.getItem('isAuthenticated') === 'true';
  return isAuthenticated ? children : <Navigate to="/login" replace />;
};

function App() {
  return (
    <ErrorBoundary>
      <Router>
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
          
          {/* 404 Page */}
          <Route path="*" element={<NotFoundPage />} />
        </Routes>
      </Router>
    </ErrorBoundary>
  );
}

export default App;
