import React, { useState, useEffect } from 'react';
import { X } from 'lucide-react';

const NewInterviewModal = ({ isOpen, onClose, onSubmit }) => {
  const [candidates, setCandidates] = useState([]);
  const [formData, setFormData] = useState({
    candidateId: '',
    useCustomKnowledge: false,
    positionType: '',
    programmingLanguages: [],
    language: 'English'
  });
  const [agreeTerms, setAgreeTerms] = useState(false);

  useEffect(() => {
    if (isOpen) {
      // Fetch candidates list
      fetch('http://localhost:8080/api/resume/candidates')
        .then(res => res.json())
        .then(data => {
          setCandidates(data.candidates || []);
          if (data.candidates && data.candidates.length > 0) {
            setFormData(prev => ({ ...prev, candidateId: data.candidates[0].id }));
          }
        })
        .catch(err => console.error('Failed to load candidates:', err));
    }
  }, [isOpen]);

  const availableLanguages = ['JavaScript', 'Python', 'Java', 'Kotlin', 'TypeScript', 'Go', 'C++', 'Ruby'];

  const toggleLanguage = (lang) => {
    setFormData(prev => ({
      ...prev,
      programmingLanguages: prev.programmingLanguages.includes(lang)
        ? prev.programmingLanguages.filter(l => l !== lang)
        : [...prev.programmingLanguages, lang]
    }));
  };

  const handleSubmit = () => {
    if (!formData.positionType || formData.programmingLanguages.length === 0) {
      alert('Please fill in required fields');
      return;
    }
    if (!agreeTerms) {
      alert('Please agree to the terms of service');
      return;
    }
    onSubmit(formData);
    onClose();
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-md p-6 relative">
        {/* Close Button */}
        <button
          onClick={onClose}
          className="absolute top-4 right-4 text-gray-400 hover:text-gray-600"
        >
          <X size={24} />
        </button>

        {/* Title */}
        <h2 className="text-2xl font-bold text-gray-800 mb-6">New Interview</h2>

        {/* Form */}
        <div className="space-y-5">
          {/* Candidate Resume */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Interview Resume
            </label>
            <select
              value={formData.candidateId}
              onChange={(e) => setFormData({ ...formData, candidateId: e.target.value })}
              className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-purple-500 focus:border-transparent outline-none"
            >
              {candidates.map(c => (
                <option key={c.id} value={c.id}>{c.name}</option>
              ))}
            </select>
          </div>

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
              value={formData.positionType}
              onChange={(e) => setFormData({ ...formData, positionType: e.target.value })}
              placeholder="e.g., Internet/AI > Artificial Intelligence"
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
              value={formData.language}
              onChange={(e) => setFormData({ ...formData, language: e.target.value })}
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
            className="w-full bg-purple-600 text-white py-3 rounded-lg font-medium hover:bg-purple-700 focus:outline-none focus:ring-2 focus:ring-purple-500 focus:ring-offset-2 transition"
          >
            + Create
          </button>
        </div>
      </div>
    </div>
  );
};

export default NewInterviewModal;

