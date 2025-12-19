import React, { useState, useEffect } from 'react';
import { CheckCircle, Clock, Target, MessageSquare, Lightbulb, Play, ArrowLeft } from 'lucide-react';
import LoadingSpinner from './common/LoadingSpinner';
import { useToast } from './common/useToast';
import ToastContainer from './common/ToastContainer';

const InterviewPreparation = ({ interviewData, onStartInterview, onBack }) => {
  const [preparationData, setPreparationData] = useState(null);
  const [loading, setLoading] = useState(true);
  const { toasts, removeToast, error: showError } = useToast();

  useEffect(() => {
    loadPreparationData();
  }, [interviewData]);

  const loadPreparationData = async () => {
    try {
      setLoading(true);

      // Get preparation data from knowledge base
      const accessToken = localStorage.getItem('accessToken');
      const headers = {
        'Content-Type': 'application/json',
      };
      if (accessToken) {
        headers['Authorization'] = `Bearer ${accessToken}`;
      }

      // Get knowledge base data for the role
      const response = await fetch('http://localhost:8080/api/knowledge-base', { headers });
      if (response.ok) {
        const knowledgeBase = await response.json();

        // Find role-specific data
        const roleData = findRoleData(knowledgeBase, interviewData?.title || '');
        setPreparationData(roleData);
      } else {
        // Fallback to generic preparation data
        setPreparationData(getFallbackPreparationData());
      }
    } catch (err) {
      console.error('Error loading preparation data:', err);
      setPreparationData(getFallbackPreparationData());
      showError('Failed to load preparation data');
    } finally {
      setLoading(false);
    }
  };

  const findRoleData = (knowledgeBase, title) => {
    // Try to match role from title
    const roleKeywords = {
      'backend': 'backend_java',
      'frontend': 'frontend_react',
      'fullstack': 'fullstack',
      'java': 'backend_java',
      'react': 'frontend_react',
      'python': 'backend_python',
      'javascript': 'frontend_react'
    };

    let matchedRole = null;
    const titleLower = title.toLowerCase();

    for (const [keyword, role] of Object.entries(roleKeywords)) {
      if (titleLower.includes(keyword)) {
        matchedRole = role;
        break;
      }
    }

    // Find role in knowledge base
    if (matchedRole && knowledgeBase.roles && knowledgeBase.roles[matchedRole]) {
      const roleInfo = knowledgeBase.roles[matchedRole];
      return {
        roleName: roleInfo.name || matchedRole,
        focusAreas: roleInfo.focus_areas || [],
        keySkills: extractKeySkills(roleInfo),
        commonQuestions: generateCommonQuestions(roleInfo),
        tips: generateInterviewTips(roleInfo),
        estimatedDuration: '45-60 minutes'
      };
    }

    return getFallbackPreparationData();
  };

  const extractKeySkills = (roleInfo) => {
    const skills = [];
    if (roleInfo.levels) {
      // Extract skills from different levels
      Object.values(roleInfo.levels).forEach(level => {
        if (level.key_skills) {
          skills.push(...level.key_skills);
        }
        if (level.focus_areas) {
          skills.push(...level.focus_areas);
        }
      });
    }
    return [...new Set(skills)].slice(0, 8); // Return unique skills, max 8
  };

  const generateCommonQuestions = (roleInfo) => {
    // Generate common questions based on role
    const commonQuestions = [
      "Tell me about yourself and your background",
      "What are your strengths and weaknesses?",
      "Why are you interested in this position?",
      "Where do you see yourself in 5 years?"
    ];

    // Add role-specific questions
    if (roleInfo.levels) {
      Object.values(roleInfo.levels).forEach(level => {
        if (level.common_questions) {
          commonQuestions.push(...level.common_questions.slice(0, 3));
        }
      });
    }

    return commonQuestions.slice(0, 6);
  };

  const generateInterviewTips = (roleInfo) => {
    const tips = [
      "Be specific about your past experiences and achievements",
      "Use the STAR method (Situation, Task, Action, Result) for behavioral questions",
      "Prepare questions to ask the interviewer",
      "Practice explaining technical concepts clearly",
      "Show enthusiasm for the role and company",
      "Follow up with a thank-you note after the interview"
    ];

    // Add role-specific tips
    if (roleInfo.name?.toLowerCase().includes('backend')) {
      tips.push("Be ready to discuss system design and architecture decisions");
      tips.push("Prepare to explain your coding approach and problem-solving process");
    } else if (roleInfo.name?.toLowerCase().includes('frontend')) {
      tips.push("Discuss your experience with modern frameworks and tools");
      tips.push("Be prepared to talk about UI/UX principles and user experience");
    }

    return tips.slice(0, 8);
  };

  const getFallbackPreparationData = () => {
    return {
      roleName: interviewData?.title || 'Software Developer',
      focusAreas: ['Problem Solving', 'Technical Skills', 'Communication'],
      keySkills: [
        'Programming Languages',
        'Data Structures & Algorithms',
        'System Design',
        'Database Management',
        'Version Control',
        'Testing & Debugging',
        'API Development',
        'Security Best Practices'
      ],
      commonQuestions: [
        "Tell me about yourself",
        "What are your strengths?",
        "Describe a challenging project",
        "How do you handle difficult situations?",
        "What are your career goals?",
        "Do you have any questions for us?"
      ],
      tips: [
        "Research the company and role thoroughly",
        "Practice coding problems regularly",
        "Prepare examples of your past work",
        "Be honest about what you don't know",
        "Show enthusiasm and curiosity",
        "Ask thoughtful questions about the role"
      ],
      estimatedDuration: '30-45 minutes'
    };
  };

  const handleStartInterview = () => {
    onStartInterview();
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-gradient-to-br from-blue-50 to-indigo-100 flex items-center justify-center">
        <div className="text-center">
          <LoadingSpinner size="xl" className="mx-auto mb-4" />
          <p className="text-gray-600">Preparing your interview...</p>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gradient-to-br from-blue-50 to-indigo-100 py-8 px-4">
      <ToastContainer toasts={toasts} removeToast={removeToast} />

      <div className="max-w-4xl mx-auto">
        {/* Header */}
        <div className="text-center mb-8">
          <button
            onClick={onBack}
            className="flex items-center gap-2 text-gray-600 hover:text-gray-900 mb-4 mx-auto"
          >
            <ArrowLeft size={20} />
            Back to Dashboard
          </button>
          <h1 className="text-4xl font-bold text-gray-900 mb-2">Interview Preparation</h1>
          <p className="text-xl text-gray-600">Get ready for your {preparationData?.roleName} interview</p>
        </div>

        <div className="grid md:grid-cols-2 gap-8 mb-8">
          {/* Interview Overview */}
          <div className="bg-white rounded-xl shadow-lg p-6">
            <div className="flex items-center gap-3 mb-4">
              <Target className="text-blue-600" size={24} />
              <h2 className="text-xl font-bold text-gray-900">Interview Overview</h2>
            </div>

            <div className="space-y-4">
              <div className="flex items-center gap-3">
                <Clock className="text-gray-400" size={20} />
                <div>
                  <p className="font-medium text-gray-900">Estimated Duration</p>
                  <p className="text-gray-600">{preparationData?.estimatedDuration}</p>
                </div>
              </div>

              <div>
                <p className="font-medium text-gray-900 mb-2">Focus Areas</p>
                <div className="flex flex-wrap gap-2">
                  {preparationData?.focusAreas?.map((area, idx) => (
                    <span key={idx} className="px-3 py-1 bg-blue-100 text-blue-800 rounded-full text-sm">
                      {area}
                    </span>
                  ))}
                </div>
              </div>
            </div>
          </div>

          {/* Key Skills */}
          <div className="bg-white rounded-xl shadow-lg p-6">
            <div className="flex items-center gap-3 mb-4">
              <CheckCircle className="text-green-600" size={24} />
              <h2 className="text-xl font-bold text-gray-900">Key Skills to Review</h2>
            </div>

            <div className="grid grid-cols-2 gap-2">
              {preparationData?.keySkills?.map((skill, idx) => (
                <div key={idx} className="flex items-center gap-2">
                  <CheckCircle className="text-green-500" size={16} />
                  <span className="text-gray-700 text-sm">{skill}</span>
                </div>
              ))}
            </div>
          </div>
        </div>

        <div className="grid md:grid-cols-2 gap-8 mb-8">
          {/* Common Questions */}
          <div className="bg-white rounded-xl shadow-lg p-6">
            <div className="flex items-center gap-3 mb-4">
              <MessageSquare className="text-purple-600" size={24} />
              <h2 className="text-xl font-bold text-gray-900">Common Questions</h2>
            </div>

            <div className="space-y-3">
              {preparationData?.commonQuestions?.map((question, idx) => (
                <div key={idx} className="p-3 bg-gray-50 rounded-lg">
                  <p className="text-gray-800 text-sm">{question}</p>
                </div>
              ))}
            </div>
          </div>

          {/* Interview Tips */}
          <div className="bg-white rounded-xl shadow-lg p-6">
            <div className="flex items-center gap-3 mb-4">
              <Lightbulb className="text-yellow-600" size={24} />
              <h2 className="text-xl font-bold text-gray-900">Interview Tips</h2>
            </div>

            <div className="space-y-3">
              {preparationData?.tips?.map((tip, idx) => (
                <div key={idx} className="flex gap-3">
                  <Lightbulb className="text-yellow-500 mt-1 flex-shrink-0" size={16} />
                  <p className="text-gray-700 text-sm">{tip}</p>
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* Start Interview Button */}
        <div className="text-center">
          <button
            onClick={handleStartInterview}
            className="inline-flex items-center gap-3 bg-gradient-to-r from-purple-600 to-indigo-600 hover:from-purple-700 hover:to-indigo-700 text-white px-8 py-4 rounded-xl font-semibold text-lg shadow-lg hover:shadow-xl transition-all transform hover:scale-105"
          >
            <Play size={24} />
            Start Interview
          </button>
          <p className="text-gray-600 mt-4">
            Make sure you're in a quiet environment and have a stable internet connection
          </p>
        </div>
      </div>
    </div>
  );
};

export default InterviewPreparation;
