import React from 'react';
import { BrowserRouter as Router, Routes, Route } from 'react-router-dom';
import Sidebar from './components/Sidebar';
import Dashboard from './components/Dashboard';
import InterviewRoom from './components/InterviewRoom';

function App() {
  return (
    <Router>
      <Routes>
        {/* Dashboard Layout with Sidebar */}
        <Route path="/" element={
          <div className="min-h-screen bg-gray-50 flex">
            <Sidebar />
            <main className="flex-1">
              <Dashboard />
            </main>
          </div>
        } />
        
        {/* Standalone Interview Room (No Sidebar) */}
        <Route path="/interview/:id" element={<InterviewRoom />} />
      </Routes>
    </Router>
  );
}

export default App;
