import React from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import DashboardLayout from './components/DashboardLayout';
import InterviewRoom from './components/InterviewRoom';
import Login from './components/Login';

// 路由保护组件
const ProtectedRoute = ({ children }) => {
  const isAuthenticated = localStorage.getItem('isAuthenticated') === 'true';
  return isAuthenticated ? children : <Navigate to="/login" replace />;
};

function App() {
  return (
    <Router>
      <Routes>
        {/* 登录页面 */}
        <Route path="/login" element={<Login />} />
        
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
      </Routes>
    </Router>
  );
}

export default App;
