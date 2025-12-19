import React, { useState, useEffect } from 'react';
import { Plus, RotateCcw, Lightbulb, Play, ArrowLeft, MessageSquare } from 'lucide-react';
import { useNavigate, useParams } from 'react-router-dom';
import LoadingSpinner from './common/LoadingSpinner';
import EmptyState from './common/EmptyState';
import { useToast } from './common/useToast';
import ToastContainer from './common/ToastContainer';

const MockInterviewPage = () => {
  const { id } = useParams();
  const [mockInterviews, setMockInterviews] = useState([]);
  const [currentInterview, setCurrentInterview] = useState(null);
  const [loading, setLoading] = useState(true);
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [currentHint, setCurrentHint] = useState('');
  const [hintLoading, setHintLoading] = useState(false);
  const [formData, setFormData] = useState({
    title: '',
    positionType: '',
    programmingLanguages: [],
    language: 'English'
  });
  const navigate = useNavigate();
  const { toasts, removeToast, success, error } = useToast();

  const availableLanguages = ['JavaScript', 'Python', 'Java', 'Kotlin', 'TypeScript', 'Go', 'C++', 'Ruby'];

  useEffect(() => {
    if (id) {
      loadMockInterviewDetail(id);
    } else {
      loadMockInterviews();
    }
  }, [id]);

  const loadMockInterviews = async () => {
    try {
      setLoading(true);
      const accessToken = localStorage.getItem('accessToken');
      const headers = {
        'Content-Type': 'application/json',
      };
      if (accessToken) {
        headers['Authorization'] = `Bearer ${accessToken}`;
      }

      const response = await fetch('http://localhost:8080/api/mock-interviews', { headers });
      if (response.ok) {
        const data = await response.json();
        setMockInterviews(data);
      } else {
        error('Failed to load mock interviews');
      }
    } catch (err) {
      error('Error loading mock interviews');
    } finally {
      setLoading(false);
    }
  };

  const loadMockInterviewDetail = async (interviewId) => {
    try {
      setLoading(true);
      const accessToken = localStorage.getItem('accessToken');
      const headers = {
        'Content-Type': 'application/json',
      };
      if (accessToken) {
        headers['Authorization'] = `Bearer ${accessToken}`;
      }

      const response = await fetch(`http://localhost:8080/api/mock-interviews/${interviewId}`, { headers });
      if (response.ok) {
        const data = await response.json();
        setCurrentInterview(data.mockInterview);
        setMockInterviews([]); // Clear list when showing detail
      } else {
        error('Failed to load mock interview details');
        navigate('/mock-interview');
      }
    } catch (err) {
      error('Error loading mock interview details');
      navigate('/mock-interview');
    } finally {
      setLoading(false);
    }
  };

  const getHints = async () => {
    if (!currentInterview) return;

    try {
      setHintLoading(true);
      const accessToken = localStorage.getItem('accessToken');
      const headers = {
        'Content-Type': 'application/json',
      };
      if (accessToken) {
        headers['Authorization'] = `Bearer ${accessToken}`;
      }

      const response = await fetch(`http://localhost:8080/api/mock-interviews/${currentInterview.id}/hints`, { headers });
      if (response.ok) {
        const data = await response.json();
        setCurrentHint(data.hint);
        success('Hint loaded successfully');
      } else {
        error('Failed to get hints');
      }
    } catch (err) {
      error('Error getting hints');
    } finally {
      setHintLoading(false);
    }
  };

  const handleCreate = async () => {
    if (!formData.title || !formData.positionType || formData.programmingLanguages.length === 0) {
      error('Please fill in all required fields');
      return;
    }

    try {
      const accessToken = localStorage.getItem('accessToken');
      const headers = {
        'Content-Type': 'application/json',
      };
      if (accessToken) {
        headers['Authorization'] = `Bearer ${accessToken}`;
      }

      const response = await fetch('http://localhost:8080/api/mock-interviews', {
        method: 'POST',
        headers,
        body: JSON.stringify({
          ...formData,
          programmingLanguages: JSON.stringify(formData.programmingLanguages)
        })
      });

      if (response.ok) {
        const data = await response.json();
        success('Mock interview created successfully');
        setShowCreateModal(false);
        setFormData({ title: '', positionType: '', programmingLanguages: [], language: 'English' });
        navigate(`/mock-interview/${data.mockInterview.id}`);
      } else {
        error('Failed to create mock interview');
      }
    } catch (err) {
      error('Error creating mock interview');
    }
  };

  const handleRetry = async (id) => {
    try {
      const accessToken = localStorage.getItem('accessToken');
      const headers = {
        'Content-Type': 'application/json',
      };
      if (accessToken) {
        headers['Authorization'] = `Bearer ${accessToken}`;
      }

      const response = await fetch(`http://localhost:8080/api/mock-interviews/${id}/retry`, {
        method: 'POST',
        headers
      });

      if (response.ok) {
        success('Mock interview reset successfully');
        navigate(`/mock-interview/${id}`);
      } else {
        error('Failed to retry mock interview');
      }
    } catch (err) {
      error('Error retrying mock interview');
    }
  };

  const toggleLanguage = (lang) => {
    setFormData(prev => ({
      ...prev,
      programmingLanguages: prev.programmingLanguages.includes(lang)
        ? prev.programmingLanguages.filter(l => l !== lang)
        : [...prev.programmingLanguages, lang]
    }));
  };

  // 如果有ID参数，显示详情页面
  if (id && currentInterview) {
    return (
      <div className="p-8 ml-64">
        <ToastContainer toasts={toasts} removeToast={removeToast} />

        <div className="flex items-center justify-between mb-8">
          <div className="flex items-center gap-4">
            <button
              onClick={() => navigate('/mock-interview')}
              className="p-2 text-gray-600 hover:text-gray-900 hover:bg-gray-100 rounded-lg"
            >
              <ArrowLeft size={20} />
            </button>
            <div>
              <h2 className="text-2xl font-bold text-gray-800">{currentInterview.title}</h2>
              <p className="text-gray-600 mt-1">Mock Interview Details</p>
            </div>
          </div>
          <div className="flex gap-3">
            <button
              onClick={getHints}
              disabled={hintLoading}
              className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 flex items-center gap-2 disabled:opacity-50"
            >
              <Lightbulb size={18} />
              {hintLoading ? 'Loading...' : 'Get Hint'}
            </button>
            {currentInterview.status === 'completed' && (
              <button
                onClick={() => handleRetry(currentInterview.id)}
                className="px-4 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700 flex items-center gap-2"
              >
                <RotateCcw size={18} />
                Retry
              </button>
            )}
          </div>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
          <div className="bg-white p-6 rounded-xl shadow-sm border border-gray-100">
            <h3 className="text-xl font-bold text-gray-800 mb-4">Interview Information</h3>
            <div className="space-y-3">
              <div className="flex justify-between">
                <span className="text-gray-600">Position:</span>
                <span className="font-medium">{currentInterview.positionType}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-gray-600">Language:</span>
                <span className="font-medium">{currentInterview.language}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-gray-600">Status:</span>
                <span className={`font-medium px-2 py-1 rounded-full text-xs ${
                  currentInterview.status === 'completed' ? 'bg-green-100 text-green-700' :
                  'bg-blue-100 text-blue-700'
                }`}>
                  {currentInterview.status}
                </span>
              </div>
              {currentInterview.score && (
                <div className="flex justify-between">
                  <span className="text-gray-600">Score:</span>
                  <span className="font-medium">{currentInterview.score}</span>
                </div>
              )}
            </div>
          </div>

          {currentHint && (
            <div className="bg-white p-6 rounded-xl shadow-sm border border-gray-100">
              <h3 className="text-xl font-bold text-gray-800 mb-4 flex items-center gap-2">
                <Lightbulb size={20} className="text-yellow-500" />
                Practice Hint
              </h3>
              <p className="text-gray-700 leading-relaxed">{currentHint}</p>
            </div>
          )}
        </div>

        {/* 这里可以添加更多面试详情，如问题历史等 */}
      </div>
    );
  }

  return (
    <div className="p-8 ml-64">
      <ToastContainer toasts={toasts} removeToast={removeToast} />

      <div className="flex items-center justify-between mb-8">
        <div>
          <h2 className="text-2xl font-bold text-gray-800">Mock Interview</h2>
          <p className="text-gray-600 mt-1">Practice mode with hints and retry options</p>
        </div>
        <button
          onClick={() => setShowCreateModal(true)}
          className="px-4 py-2 bg-purple-600 text-white rounded-lg hover:bg-purple-700 flex items-center gap-2"
        >
          <Plus size={20} />
          New Mock Interview
        </button>
      </div>

      {loading ? (
        <div className="flex justify-center py-12">
          <LoadingSpinner size="lg" />
        </div>
      ) : mockInterviews.length === 0 ? (
        <EmptyState
          icon="file"
          title="No mock interviews"
          message="Create a mock interview to practice"
          actionLabel="Create Mock Interview"
          onAction={() => setShowCreateModal(true)}
        />
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {mockInterviews.map((interview) => (
            <div key={interview.id} className="bg-white p-6 rounded-xl shadow-sm border border-gray-100 hover:shadow-md transition-shadow">
              <div className="flex items-start justify-between mb-4">
                <div>
                  <h3 className="font-bold text-gray-800 mb-1">{interview.title || 'Mock Interview'}</h3>
                  <span className={`text-xs px-2 py-1 rounded-full ${
                    interview.status === 'completed' ? 'bg-green-100 text-green-700' : 'bg-purple-100 text-purple-700'
                  }`}>
                    {interview.status}
                  </span>
                </div>
              </div>
              
              <div className="space-y-2 mb-4">
                <p className="text-sm text-gray-600">
                  <span className="font-medium">Position:</span> {interview.positionType}
                </p>
                <p className="text-sm text-gray-600">
                  <span className="font-medium">Language:</span> {interview.language}
                </p>
                {interview.score && (
                  <p className="text-sm text-gray-600">
                    <span className="font-medium">Score:</span> {interview.score}
                  </p>
                )}
              </div>
              
              <div className="flex gap-2">
                <button
                  onClick={() => navigate(`/mock-interview/${interview.id}`)}
                  className="flex-1 px-3 py-2 bg-purple-600 text-white rounded-lg hover:bg-purple-700 flex items-center justify-center gap-2 text-sm"
                >
                  <Play size={16} />
                  {interview.status === 'practice' ? 'Continue' : 'View'}
                </button>
                {interview.status === 'completed' && (
                  <button
                    onClick={() => handleRetry(interview.id)}
                    className="px-3 py-2 bg-blue-100 text-blue-700 rounded-lg hover:bg-blue-200 flex items-center justify-center gap-2 text-sm"
                    title="Retry"
                  >
                    <RotateCcw size={16} />
                  </button>
                )}
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Create Modal */}
      {showCreateModal && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg shadow-xl max-w-md w-full mx-4 p-6">
            <h3 className="text-xl font-bold mb-4">Create Mock Interview</h3>
            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">Title</label>
                <input
                  type="text"
                  value={formData.title}
                  onChange={(e) => setFormData({ ...formData, title: e.target.value })}
                  className="w-full px-4 py-2 border border-gray-300 rounded-lg"
                  placeholder="Interview title"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">Position Type *</label>
                <input
                  type="text"
                  value={formData.positionType}
                  onChange={(e) => setFormData({ ...formData, positionType: e.target.value })}
                  className="w-full px-4 py-2 border border-gray-300 rounded-lg"
                  placeholder="e.g., Backend Java Developer"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">Programming Languages *</label>
                <div className="flex flex-wrap gap-2 border border-gray-300 rounded-lg p-3 min-h-[60px]">
                  {formData.programmingLanguages.map(lang => (
                    <span
                      key={lang}
                      className="inline-flex items-center gap-1 px-3 py-1 bg-purple-100 text-purple-700 rounded-full text-sm"
                    >
                      {lang}
                    </span>
                  ))}
                </div>
                <div className="flex flex-wrap gap-2 mt-2">
                  {availableLanguages.filter(l => !formData.programmingLanguages.includes(l)).map(lang => (
                    <button
                      key={lang}
                      onClick={() => toggleLanguage(lang)}
                      className="px-3 py-1 text-sm border border-gray-300 rounded-full hover:bg-gray-50"
                    >
                      + {lang}
                    </button>
                  ))}
                </div>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">Language</label>
                <select
                  value={formData.language}
                  onChange={(e) => setFormData({ ...formData, language: e.target.value })}
                  className="w-full px-4 py-2 border border-gray-300 rounded-lg"
                >
                  <option value="English">English</option>
                  <option value="Chinese">中文</option>
                </select>
              </div>
              <div className="flex justify-end gap-3">
                <button
                  onClick={() => {
                    setShowCreateModal(false);
                    setFormData({ title: '', positionType: '', programmingLanguages: [], language: 'English' });
                  }}
                  className="px-4 py-2 bg-gray-200 text-gray-700 rounded-lg hover:bg-gray-300"
                >
                  Cancel
                </button>
                <button
                  onClick={handleCreate}
                  className="px-4 py-2 bg-purple-600 text-white rounded-lg hover:bg-purple-700"
                >
                  Create
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default MockInterviewPage;

