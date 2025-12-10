import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Sidebar from './Sidebar';
import Dashboard from './Dashboard';
import NewInterviewModal from './NewInterviewModal';

const DashboardLayout = () => {
  const [isModalOpen, setIsModalOpen] = useState(false);
  const navigate = useNavigate();

  const handleNewInterview = () => {
    setIsModalOpen(true);
  };

  const handleModalSubmit = async (formData) => {
    try {
      // Call backend to analyze resume and generate knowledge base
      const response = await fetch('http://localhost:8080/api/resume/analyze', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          candidateId: formData.candidateId,
          positionType: formData.positionType,
          programmingLanguages: formData.programmingLanguages,
          language: formData.language
        }),
      });

      if (response.ok) {
        const knowledgeBase = await response.json();
        
        // TODO: Store knowledge base for this interview session
        // For now, just navigate to interview room with a new ID
        const interviewId = Date.now();
        localStorage.setItem(`interview_${interviewId}_kb`, JSON.stringify(knowledgeBase));
        
        navigate(`/interview/${interviewId}`);
      } else {
        console.error('Failed to analyze resume');
        alert('Failed to create interview. Please try again.');
      }
    } catch (error) {
      console.error('Error creating interview:', error);
      alert('Error creating interview. Please try again.');
    }
  };

  return (
    <div className="min-h-screen bg-gray-50 flex">
      <Sidebar onNewInterview={handleNewInterview} />
      <main className="flex-1">
        <Dashboard />
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

