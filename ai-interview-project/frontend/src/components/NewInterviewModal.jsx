import React, { useState, useEffect } from 'react';
import LoadingSpinner from './common/LoadingSpinner';
import { X, FileText, BookOpen } from 'lucide-react';
import InterviewTemplateModal from './InterviewTemplateModal';

const NewInterviewModal = ({ isOpen, onClose, onSubmit }) => {
  const [candidates, setCandidates] = useState([]);
  const [resumes, setResumes] = useState([]);
  const [loading, setLoading] = useState(false);
  const [formData, setFormData] = useState({
    candidateId: '',
    useCustomKnowledge: false,
    positionType: '',
    programmingLanguages: [],
    language: 'English',
    templateId: null,
    questionSetId: null,
    interviewType: 'general', // 'general' or 'resume-based'
    resumeId: null
  });
  const [agreeTerms, setAgreeTerms] = useState(false);
  const [showTemplateModal, setShowTemplateModal] = useState(false);
  const [selectedTemplate, setSelectedTemplate] = useState(null);
  const [showQuestionSetModal, setShowQuestionSetModal] = useState(false);
  const [selectedQuestionSet, setSelectedQuestionSet] = useState(null);

  useEffect(() => {
    if (isOpen) {
      loadCandidates();
      loadResumes();
    }
  }, [isOpen]);

  const loadCandidates = async () => {
    try {
      setLoading(true);
      const accessToken = localStorage.getItem('accessToken');
      const headers = {
        'Content-Type': 'application/json',
      };
      if (accessToken) {
        headers['Authorization'] = `Bearer ${accessToken}`;
      }

      const response = await fetch('http://localhost:8080/api/resume/candidates', { headers });
      if (!response.ok) {
        if (response.status === 401) {
          console.error('Unauthorized: Token may be expired');
          return;
        }
        throw new Error(`HTTP ${response.status}`);
      }
      
      const data = await response.json();
      setCandidates(data.candidates || []);
      if (data.candidates && data.candidates.length > 0) {
        setFormData(prev => ({ ...prev, candidateId: data.candidates[0].id }));
      }
    } catch (err) {
      console.error('Failed to load candidates:', err);
      setCandidates([]);
    } finally {
      setLoading(false);
    }
  };

  const loadResumes = async () => {
    try {
      const accessToken = localStorage.getItem('accessToken');
      const headers = {
        'Content-Type': 'application/json',
      };
      if (accessToken) {
        headers['Authorization'] = `Bearer ${accessToken}`;
      }

      const response = await fetch('http://localhost:8080/api/user/resume', { headers });
      if (response.ok) {
        const resumesData = await response.json();
        // Filter only analyzed resumes for resume-based interviews
        const analyzedResumes = resumesData.filter(resume => resume.analyzed);
        setResumes(analyzedResumes);
      }
    } catch (err) {
      console.error('Failed to load resumes:', err);
      setResumes([]);
    }
  };

  const handleInterviewTypeChange = (interviewType) => {
    const defaultResumeId = resumes.length > 0 ? parseInt(resumes[0].id, 10) : null;

    setFormData(prev => ({
      ...prev,
      interviewType,
      // Reset fields when switching types
      candidateId: interviewType === 'general' ? (candidates.length > 0 ? candidates[0].id : '') : '',
      resumeId: interviewType === 'resume-based' ? defaultResumeId : null,
      positionType: '',
      programmingLanguages: []
    }));

    // If switching to resume-based and we have a default resume, load its analysis
    if (interviewType === 'resume-based' && defaultResumeId) {
      handleResumeChange(defaultResumeId);
    }

    // If switching to general and no candidate selected, try to set default after candidates load
    if (interviewType === 'general' && candidates.length > 0 && !formData.candidateId) {
      setFormData(prev => ({ ...prev, candidateId: candidates[0].id }));
    }
  };

  const handleResumeChange = async (resumeId) => {
    // Convert to number if it's a string
    const numericResumeId = resumeId ? parseInt(resumeId, 10) : null;
    setFormData(prev => ({ ...prev, resumeId: numericResumeId }));

    if (numericResumeId) {
      try {
        const accessToken = localStorage.getItem('accessToken');
        const headers = {
          'Content-Type': 'application/json',
        };
        if (accessToken) {
          headers['Authorization'] = `Bearer ${accessToken}`;
        }

        const response = await fetch(`http://localhost:8080/api/user/resume/${numericResumeId}/analysis`, { headers });
        if (response.ok) {
          const analysisData = await response.json();

          if (analysisData.analysisData) {
            const analysis = analysisData.analysisData;

            // Auto-fill position type and programming languages from resume analysis
            setFormData(prev => ({
              ...prev,
              positionType: generatePositionTypeFromAnalysis(analysis),
              programmingLanguages: analysis.techStack || []
            }));
          } else {
            console.warn('Resume analysis data not available');
          }
        } else {
          console.error(`Failed to load resume analysis: HTTP ${response.status}`);
        }
      } catch (err) {
        console.error('Failed to load resume analysis:', err);
      }
    }
  };

  const generatePositionTypeFromAnalysis = (analysis) => {
    if (!analysis) return '';

    const mainSkillAreas = analysis.mainSkillAreas || [];
    if (mainSkillAreas.length > 0) {
      return `${mainSkillAreas[0]} Developer`;
    }

    const techStack = analysis.techStack || [];
    if (techStack.length > 0) {
      return `${techStack[0]} Developer`;
    }

    return 'Software Developer';
  };

  const availableLanguages = ['JavaScript', 'Python', 'Java', 'Kotlin', 'TypeScript', 'Go', 'C++', 'Ruby'];

  const toggleLanguage = (lang) => {
    setFormData(prev => ({
      ...prev,
      programmingLanguages: prev.programmingLanguages.includes(lang)
        ? prev.programmingLanguages.filter(l => l !== lang)
        : [...prev.programmingLanguages, lang]
    }));
  };

  const handleTemplateSelect = (templateConfig) => {
    setSelectedTemplate(templateConfig);
    setFormData(prev => ({
      ...prev,
      templateId: templateConfig.templateId,
      positionType: templateConfig.title || prev.positionType,
      programmingLanguages: templateConfig.techStack ? [templateConfig.techStack] : prev.programmingLanguages,
      language: templateConfig.language || prev.language
    }));
  };

  const handleQuestionSetSelect = (questionSet) => {
    setSelectedQuestionSet(questionSet);
    setFormData(prev => ({
      ...prev,
      questionSetId: questionSet.id,
      positionType: questionSet.roleTitle || prev.positionType,
      programmingLanguages: questionSet.techStack ? [questionSet.techStack] : prev.programmingLanguages
    }));
  };

  const handleSubmit = () => {
    if (formData.interviewType === 'general' && !formData.candidateId) {
      alert('Please select a candidate for general interviews');
      return;
    }
    if (formData.interviewType === 'resume-based' && !formData.resumeId) {
      alert('Please select a resume for resume-based interviews');
      return;
    }
    if (!formData.positionType || formData.programmingLanguages.length === 0) {
      alert('Please fill in required fields');
      return;
    }
    if (!agreeTerms) {
      alert('Please agree to the terms of service');
      return;
    }
    onSubmit(formData);
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 p-4" data-testid="modal-backdrop" onClick={onClose}>
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-md max-h-[90vh] flex flex-col relative" onClick={(e) => e.stopPropagation()}>
        {/* Close Button */}
        <button
          onClick={onClose}
          className="absolute top-4 right-4 text-gray-400 hover:text-gray-600 z-10"
        >
          <X size={24} />
        </button>

        {/* Title */}
        <div className="p-6 pb-4 flex-shrink-0">
          <h2 className="text-2xl font-bold text-gray-800">New Interview</h2>
        </div>

        {/* Scrollable Content */}
        <div className="flex-1 overflow-y-auto px-6 pb-6">

        {/* Interview Type Selection */}
        <div className="mb-6">
          <label className="block text-sm font-medium text-gray-700 mb-3">
            Interview Type <span className="text-red-500">*</span>
          </label>
          <div className="space-y-2">
            <label className="flex items-center">
              <input
                type="radio"
                name="interviewType"
                value="general"
                checked={formData.interviewType === 'general'}
                onChange={(e) => handleInterviewTypeChange(e.target.value)}
                className="mr-2"
              />
              <span className="text-sm">General Interview</span>
            </label>
            <label className="flex items-center">
              <input
                type="radio"
                name="interviewType"
                value="resume-based"
                checked={formData.interviewType === 'resume-based'}
                onChange={(e) => handleInterviewTypeChange(e.target.value)}
                className="mr-2"
              />
              <span className="text-sm">Resume-based Interview</span>
            </label>
          </div>
        </div>

        {/* Template Selection */}
        <div className="mb-6">
          <button
            onClick={() => setShowTemplateModal(true)}
            className="w-full flex items-center justify-center gap-2 px-4 py-3 border-2 border-dashed border-purple-300 text-purple-600 rounded-lg hover:bg-purple-50 transition-colors"
          >
            <FileText size={20} />
            {selectedTemplate ? `Using: ${selectedTemplate.title}` : 'Use Interview Template (Optional)'}
          </button>
          {selectedTemplate && (
            <div className="mt-2 text-sm text-gray-600">
              {selectedTemplate.techStack} • {selectedTemplate.level} • {selectedTemplate.durationMinutes} min
            </div>
          )}
        </div>

        {/* Question Set Selection */}
        <div className="mb-6">
          <button
            onClick={() => setShowQuestionSetModal(true)}
            className="w-full flex items-center justify-center gap-2 px-4 py-3 border-2 border-dashed border-blue-300 text-blue-600 rounded-lg hover:bg-blue-50 transition-colors"
          >
            <BookOpen size={20} />
            {selectedQuestionSet ? `Using: ${selectedQuestionSet.name}` : 'Use Custom Question Set (Optional)'}
          </button>
          {selectedQuestionSet && (
            <div className="mt-2 text-sm text-gray-600">
              {selectedQuestionSet.questions?.length || 0} questions • {selectedQuestionSet.techStack} • {selectedQuestionSet.level}
            </div>
          )}
        </div>

        {/* Form */}
        <div className="space-y-5">
          {/* Candidate/Resume Selection */}
          {formData.interviewType === 'general' ? (
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Interview Candidate <span className="text-red-500">*</span>
              </label>
              {loading ? (
                <div className="flex items-center gap-2">
                  <LoadingSpinner size="sm" />
                  <span className="text-sm text-gray-500">Loading candidates...</span>
                </div>
              ) : candidates.length === 0 ? (
                <div className="text-sm text-gray-500 p-3 border border-gray-300 rounded-lg bg-gray-50">
                  No candidates available. Please add candidates first.
                </div>
              ) : (
                <select
                  name="candidateId"
                  value={formData.candidateId || ''}
                  onChange={(e) => setFormData({ ...formData, candidateId: e.target.value })}
                  className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-purple-500 focus:border-transparent outline-none bg-white cursor-pointer appearance-none relative z-10"
                  style={{ backgroundImage: "url(\"data:image/svg+xml,%3csvg xmlns='http://www.w3.org/2000/svg' fill='none' viewBox='0 0 20 20'%3e%3cpath stroke='%236b7280' stroke-linecap='round' stroke-linejoin='round' stroke-width='1.5' d='M6 8l4 4 4-4'/%3e%3c/svg%3e\")", backgroundPosition: 'right 0.5rem center', backgroundRepeat: 'no-repeat', backgroundSize: '1.5em 1.5em', paddingRight: '2.5rem' }}
                  data-testid="candidate-select"
                >
                  <option value="">Select a candidate...</option>
                  {candidates.map(c => (
                    <option key={c.id} value={c.id}>{c.name}</option>
                  ))}
                </select>
              )}
            </div>
          ) : (
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Resume for Analysis <span className="text-red-500">*</span>
              </label>
              {resumes.length === 0 ? (
                <div className="text-sm text-gray-500 p-3 border border-gray-300 rounded-lg">
                  No analyzed resumes available. Please upload and analyze a resume first.
                </div>
              ) : (
                <select
                  name="resumeId"
                  value={formData.resumeId || ''}
                  onChange={(e) => handleResumeChange(e.target.value)}
                  className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-purple-500 focus:border-transparent outline-none"
                >
                  <option value="">Select a resume...</option>
                  {resumes.map(resume => (
                    <option key={resume.id} value={resume.id}>
                      {resume.fileName} (Analyzed)
                    </option>
                  ))}
                </select>
              )}
              {formData.resumeId && (
                <div className="mt-2 p-3 bg-blue-50 border border-blue-200 rounded-lg">
                  <div className="text-sm text-blue-800">
                    Resume selected. Position and technologies will be auto-filled based on analysis.
                  </div>
                </div>
              )}
            </div>
          )}

          {/* Custom Knowledge Base */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Custom Knowledge Base
            </label>
            <select
              value={formData.useCustomKnowledge ? 'custom' : 'default'}
              onChange={(e) => setFormData({ ...formData, useCustomKnowledge: e.target.value === 'custom' })}
              className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-purple-500 focus:border-transparent outline-none"
            >
              <option value="default">Use Default Knowledge Base</option>
              <option value="custom">Use Custom Knowledge Base</option>
            </select>
          </div>

          {/* Position Type */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Position Type <span className="text-red-500">*</span>
            </label>
            <input
              type="text"
              name="positionType"
              value={formData.positionType}
              onChange={(e) => setFormData({ ...formData, positionType: e.target.value })}
              placeholder="e.g., Internet/AI > Artificial Intelligence"
              data-testid="position-type-input"
              className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-purple-500 focus:border-transparent outline-none"
            />
          </div>

          {/* Programming Languages */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Programming Languages <span className="text-red-500">*</span>
            </label>
            <div className="flex flex-wrap gap-2 border border-gray-300 rounded-lg p-3 min-h-[60px]">
              {formData.programmingLanguages.map(lang => (
                <span
                  key={lang}
                  className="inline-flex items-center gap-1 px-3 py-1 bg-purple-100 text-purple-700 rounded-full text-sm font-medium"
                >
                  {lang}
                  <button
                    onClick={() => toggleLanguage(lang)}
                    className="hover:bg-purple-200 rounded-full p-0.5"
                  >
                    <X size={14} />
                  </button>
                </span>
              ))}
            </div>
            <div className="flex flex-wrap gap-2 mt-2">
              {availableLanguages.filter(l => !formData.programmingLanguages.includes(l)).map(lang => (
                <button
                  key={lang}
                  onClick={() => toggleLanguage(lang)}
                  data-testid="language-button"
                  className="px-3 py-1 text-sm border border-gray-300 rounded-full hover:bg-gray-50 text-gray-700"
                >
                  + {lang}
                </button>
              ))}
            </div>
          </div>

          {/* Interview Language */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Language <span className="text-red-500">*</span>
            </label>
            <select
              name="language"
              value={formData.language}
              onChange={(e) => setFormData({ ...formData, language: e.target.value })}
              data-testid="interview-language"
              className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-purple-500 focus:border-transparent outline-none"
            >
              <option value="English">English</option>
              <option value="Chinese">中文</option>
            </select>
          </div>

          {/* Terms Agreement */}
          <div className="flex items-start gap-2">
            <input
              type="checkbox"
              id="terms"
              checked={agreeTerms}
              onChange={(e) => setAgreeTerms(e.target.checked)}
              className="mt-1"
            />
            <label htmlFor="terms" className="text-sm text-gray-600">
              I have read and agree to the{' '}
              <a href="#" className="text-purple-600 hover:underline">
                AI Interview Terms of Service
              </a>
            </label>
          </div>

          {/* Help Link */}
          <div className="text-center">
            <a href="#" className="text-sm text-purple-600 hover:underline flex items-center justify-center gap-1">
              <span>❓</span>
              <span>How to use in interview?</span>
            </a>
          </div>

          {/* Submit Button */}
          <button
            onClick={handleSubmit}
            data-testid="create-interview-button"
            className="w-full bg-purple-600 text-white py-3 rounded-lg font-medium hover:bg-purple-700 focus:outline-none focus:ring-2 focus:ring-purple-500 focus:ring-offset-2 transition"
          >
            Create
          </button>
        </div>
        </div>
      </div>

      {/* Template Modal */}
      <InterviewTemplateModal
        isOpen={showTemplateModal}
        onClose={() => setShowTemplateModal(false)}
        onSelectTemplate={handleTemplateSelect}
      />

      {/* Question Set Modal - Simple selection only */}
      {showQuestionSetModal && (
        <QuestionSetSelectorModal
          isOpen={showQuestionSetModal}
          onClose={() => setShowQuestionSetModal(false)}
          onSelectQuestionSet={handleQuestionSetSelect}
        />
      )}
    </div>
  );
};

// Simple question set selector component
const QuestionSetSelectorModal = ({ isOpen, onClose, onSelectQuestionSet }) => {
  const [questionSets, setQuestionSets] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (isOpen) {
      loadQuestionSets();
    }
  }, [isOpen]);

  const loadQuestionSets = async () => {
    try {
      setLoading(true);
      const accessToken = localStorage.getItem('accessToken');
      const headers = {
        'Content-Type': 'application/json',
      };
      if (accessToken) {
        headers['Authorization'] = `Bearer ${accessToken}`;
      }

      const response = await fetch('http://localhost:8080/api/question-sets', { headers });
      if (response.ok) {
        const data = await response.json();
        setQuestionSets(data);
      }
    } catch (err) {
      console.error('Error loading question sets:', err);
    } finally {
      setLoading(false);
    }
  };

  const handleSelect = (questionSet) => {
    onSelectQuestionSet(questionSet);
    onClose();
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-xl max-w-2xl w-full max-h-[80vh] overflow-hidden">
        <div className="flex items-center justify-between p-6 border-b border-gray-200">
          <h3 className="text-lg font-semibold text-gray-900">Select Question Set</h3>
          <button
            onClick={onClose}
            className="text-gray-400 hover:text-gray-600"
          >
            <X size={24} />
          </button>
        </div>

        <div className="p-6">
          {loading ? (
            <div className="flex justify-center py-8">
              <LoadingSpinner size="md" />
            </div>
          ) : (
            <div className="space-y-3 max-h-96 overflow-y-auto">
              {questionSets.map(set => (
                <div
                  key={set.id}
                  className="border border-gray-200 rounded-lg p-4 hover:border-blue-300 cursor-pointer transition-colors"
                  onClick={() => handleSelect(set)}
                >
                  <div className="flex items-start justify-between mb-2">
                    <h4 className="font-semibold text-gray-900">{set.name}</h4>
                    <span className="text-xs bg-blue-100 text-blue-700 px-2 py-1 rounded">
                      {set.questions?.length || 0} questions
                    </span>
                  </div>
                  <p className="text-sm text-gray-600 mb-2">
                    {set.description || `Question set for ${set.techStack}`}
                  </p>
                  <div className="flex items-center gap-4 text-xs text-gray-500">
                    <span>{set.techStack}</span>
                    <span className="capitalize">{set.level}</span>
                  </div>
                </div>
              ))}

              {questionSets.length === 0 && (
                <div className="text-center py-8 text-gray-500">
                  No question sets available. Create some in the Question Sets page.
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default NewInterviewModal;

