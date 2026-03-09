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
import ProgressDashboard from './ProgressDashboard';
import SkillProgressPage from './SkillProgressPage';
import SettingsPage from './SettingsPage';
import CustomQuestionSetPage from './CustomQuestionSetPage';
import NotFoundPage from './NotFoundPage';
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
      const candidateId = formData.candidateId === '' || formData.candidateId == null
        ? null
        : Number(formData.candidateId);
      const resumeId = formData.resumeId === '' || formData.resumeId == null
        ? null
        : Number(formData.resumeId);

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
          candidateId: Number.isNaN(candidateId) ? null : candidateId,
          positionType: formData.positionType,
          programmingLanguages: formData.programmingLanguages,
          language: formData.language,
          useCustomKnowledge: formData.useCustomKnowledge,
          interviewType: formData.interviewType,
          resumeId: Number.isNaN(resumeId) ? null : resumeId,
          templateId: formData.templateId,
          questionSetId: formData.questionSetId
        }),
      });

      if (response.ok) {
        const data = await response.json();
        const knowledgeBase = data.knowledgeBase;
        const interviewId = data?.interview?.id || data?.id;

        if (!interviewId) {
          error('Interview created, but no interview id was returned.');
          return;
        }

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
          <Route path="/mock-interview/:id" element={<MockInterviewPage />} />
          <Route path="/payment" element={<PaymentPage />} />
          <Route path="/profile" element={<UserProfilePage />} />
          <Route path="/progress" element={<ProgressDashboard />} />
          <Route path="/skills" element={<SkillProgressPage />} />
          <Route path="/settings" element={<SettingsPage />} />
          <Route path="/question-sets" element={<CustomQuestionSetPage />} />
          <Route path="*" element={<NotFoundPage />} />
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
