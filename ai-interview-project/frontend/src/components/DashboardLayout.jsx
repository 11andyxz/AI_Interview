import React, { useState } from 'react';
import { useNavigate, Routes, Route } from 'react-router-dom';
import Sidebar from './Sidebar';
import Dashboard from './Dashboard';
import NewInterviewModal from './NewInterviewModal';
import NotesPage from './NotesPage';
import ResumePage from './ResumePage';
import KnowledgeBasePage from './KnowledgeBasePage';
import MockInterviewPage from './MockInterviewPage';
import PaymentPage from './PaymentPage';
import UserProfilePage from './UserProfilePage';
import { useToast } from './common/useToast';
import ToastContainer from './common/ToastContainer';

const DashboardLayout = () => {
  const [isModalOpen, setIsModalOpen] = useState(false);
  const navigate = useNavigate();
  const { toasts, removeToast, success, error } = useToast();

  const handleNewInterview = () => {
    setIsModalOpen(true);
  };

  const handleModalSubmit = async (formData) => {
    try {
      const accessToken = localStorage.getItem('accessToken');
      const headers = {
        'Content-Type': 'application/json',
      };
      if (accessToken) {
        headers['Authorization'] = `Bearer ${accessToken}`;
      }

      const response = await fetch('http://localhost:8080/api/interviews', {
        method: 'POST',
        headers,
        body: JSON.stringify({
          candidateId: formData.candidateId,
          positionType: formData.positionType,
          programmingLanguages: formData.programmingLanguages,
          language: formData.language,
          useCustomKnowledge: formData.useCustomKnowledge
        }),
      });

      if (response.ok) {
        const data = await response.json();
        const knowledgeBase = data.knowledgeBase;
        const interviewId = data?.interview?.id || Date.now();
        
        localStorage.setItem(`interview_${interviewId}_kb`, JSON.stringify(knowledgeBase));
        
        success('Interview created successfully');
        navigate(`/interview/${interviewId}`);
      } else {
        const errorData = await response.json().catch(() => ({}));
        error(errorData.error || 'Failed to create interview. Please try again.');
      }
    } catch (err) {
      console.error('Error creating interview:', err);
      error('Error creating interview. Please try again.');
    }
  };

  return (
    <div className="min-h-screen bg-gray-50 flex">
      <ToastContainer toasts={toasts} removeToast={removeToast} />
      <Sidebar onNewInterview={handleNewInterview} />
      <main className="flex-1">
        <Routes>
          <Route path="/" element={<Dashboard />} />
          <Route path="/notes" element={<NotesPage />} />
          <Route path="/resume" element={<ResumePage />} />
          <Route path="/knowledge-base" element={<KnowledgeBasePage />} />
          <Route path="/mock-interview" element={<MockInterviewPage />} />
          <Route path="/payment" element={<PaymentPage />} />
          <Route path="/profile" element={<UserProfilePage />} />
        </Routes>
      </main>
      <NewInterviewModal
        isOpen={isModalOpen}
        onClose={() => setIsModalOpen(false)}
        onSubmit={handleModalSubmit}
      />
    </div>
  );
};

export default DashboardLayout;

