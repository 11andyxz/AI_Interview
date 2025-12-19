import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Plus, Search, Edit, Trash2, Copy, BookOpen, Eye, ArrowLeft, Save, X } from 'lucide-react';
import LoadingSpinner from './common/LoadingSpinner';
import LoadingSkeleton from './common/LoadingSkeleton';
import { useToast } from './common/useToast';
import ToastContainer from './common/ToastContainer';
import ConfirmDialog from './common/ConfirmDialog';

const CustomQuestionSetPage = ({ onBack }) => {
  const navigate = useNavigate();
  const [questionSets, setQuestionSets] = useState([]);
  const [userQuestionSets, setUserQuestionSets] = useState([]);
  const [loading, setLoading] = useState(true);

  const handleBack = () => {
    if (onBack) {
      onBack();
    } else {
      navigate('/');
    }
  };
  const [searchTerm, setSearchTerm] = useState('');
  const [filterTechStack, setFilterTechStack] = useState('');
  const [filterLevel, setFilterLevel] = useState('');
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [editingSet, setEditingSet] = useState(null);
  const [showQuestionEditor, setShowQuestionEditor] = useState(false);
  const [deleteConfirm, setDeleteConfirm] = useState(null);
  const { toasts, removeToast, success, error: showError } = useToast();

  // Form state for creating/editing question sets
  const [formData, setFormData] = useState({
    name: '',
    description: '',
    techStack: 'Java',
    level: 'mid',
    isPublic: false,
    questions: [],
    tags: []
  });

  // Question editor state
  const [currentQuestion, setCurrentQuestion] = useState('');

  useEffect(() => {
    if (!showCreateForm && !editingSet) {
      loadQuestionSets();
    }
  }, [showCreateForm, editingSet]);

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

      // Load all available question sets
      const questionSetsResponse = await fetch('http://localhost:8080/api/question-sets', { headers });
      if (questionSetsResponse.ok) {
        const questionSetsData = await questionSetsResponse.json();
        setQuestionSets(questionSetsData);
      }

      // Load user's own question sets
      const userQuestionSetsResponse = await fetch('http://localhost:8080/api/question-sets/my', { headers });
      if (userQuestionSetsResponse.ok) {
        const userQuestionSetsData = await userQuestionSetsResponse.json();
        setUserQuestionSets(userQuestionSetsData);
      }
    } catch (err) {
      console.error('Error loading question sets:', err);
      showError('Failed to load question sets');
    } finally {
      setLoading(false);
    }
  };

  const handleCreateQuestionSet = async () => {
    if (!formData.name.trim() || formData.questions.length === 0) {
      showError('Please provide a name and at least one question');
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

      const response = await fetch('http://localhost:8080/api/question-sets', {
        method: 'POST',
        headers,
        body: JSON.stringify(formData)
      });

      if (response.ok) {
        const data = await response.json();
        success('Question set created successfully');
        setShowCreateForm(false);
        resetForm();
        loadQuestionSets();
      } else {
        showError('Failed to create question set');
      }
    } catch (err) {
      console.error('Error creating question set:', err);
      showError('Error creating question set');
    }
  };

  const handleUpdateQuestionSet = async () => {
    if (!formData.name.trim() || formData.questions.length === 0) {
      showError('Please provide a name and at least one question');
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

      const response = await fetch(`http://localhost:8080/api/question-sets/${editingSet.id}`, {
        method: 'PUT',
        headers,
        body: JSON.stringify(formData)
      });

      if (response.ok) {
        success('Question set updated successfully');
        setEditingSet(null);
        resetForm();
        loadQuestionSets();
      } else {
        showError('Failed to update question set');
      }
    } catch (err) {
      console.error('Error updating question set:', err);
      showError('Error updating question set');
    }
  };

  const handleDeleteQuestionSet = (questionSetId) => {
    setDeleteConfirm(questionSetId);
  };

  const confirmDeleteQuestionSet = async () => {
    if (!deleteConfirm) return;

    try {
      const accessToken = localStorage.getItem('accessToken');
      const headers = {
        'Content-Type': 'application/json',
      };
      if (accessToken) {
        headers['Authorization'] = `Bearer ${accessToken}`;
      }

      const response = await fetch(`http://localhost:8080/api/question-sets/${deleteConfirm}`, {
        method: 'DELETE',
        headers
      });

      if (response.ok) {
        success('Question set deleted successfully');
        loadQuestionSets();
      } else {
        showError('Failed to delete question set');
      }
    } catch (err) {
      console.error('Error deleting question set:', err);
      showError('Error deleting question set');
    } finally {
      setDeleteConfirm(null);
    }
  };

  const handleUseQuestionSet = async (questionSet) => {
    // Record question set usage
    try {
      const accessToken = localStorage.getItem('accessToken');
      const headers = {
        'Content-Type': 'application/json',
      };
      if (accessToken) {
        headers['Authorization'] = `Bearer ${accessToken}`;
      }

      await fetch(`http://localhost:8080/api/question-sets/${questionSet.id}/use`, {
        method: 'POST',
        headers
      });
    } catch (err) {
      console.error('Error recording question set usage:', err);
    }

    // Here you could emit an event or callback to use this question set in interview creation
    success(`Question set "${questionSet.name}" selected for use`);
  };

  const resetForm = () => {
    setFormData({
      name: '',
      description: '',
      techStack: 'Java',
      level: 'mid',
      isPublic: false,
      questions: [],
      tags: []
    });
    setCurrentQuestion('');
  };

  const startEditing = (questionSet) => {
    setEditingSet(questionSet);
    setFormData({
      name: questionSet.name || '',
      description: questionSet.description || '',
      techStack: questionSet.techStack || 'Java',
      level: questionSet.level || 'mid',
      isPublic: questionSet.isPublic || false,
      questions: questionSet.questions || [],
      tags: questionSet.tags || []
    });
  };

  const cancelEditing = () => {
    setEditingSet(null);
    setShowCreateForm(false);
    setShowQuestionEditor(false);
    resetForm();
  };

  const addQuestion = () => {
    if (currentQuestion.trim()) {
      setFormData(prev => ({
        ...prev,
        questions: [...prev.questions, currentQuestion.trim()]
      }));
      setCurrentQuestion('');
    }
  };

  const removeQuestion = (index) => {
    setFormData(prev => ({
      ...prev,
      questions: prev.questions.filter((_, i) => i !== index)
    }));
  };

  const filteredQuestionSets = questionSets.filter(set => {
    const matchesSearch = set.name?.toLowerCase().includes(searchTerm.toLowerCase()) ||
                         set.description?.toLowerCase().includes(searchTerm.toLowerCase());
    const matchesTechStack = !filterTechStack || set.techStack === filterTechStack;
    const matchesLevel = !filterLevel || set.level === filterLevel;

    return matchesSearch && matchesTechStack && matchesLevel;
  });

  const isUserSet = (questionSet) => {
    return userQuestionSets.some(userSet => userSet.id === questionSet.id);
  };

  return (
    <div className="p-8 ml-64">
      <ToastContainer toasts={toasts} removeToast={removeToast} />

      {/* Header */}
      <div className="mb-8">
        <button
          onClick={handleBack}
          className="flex items-center gap-2 text-gray-600 hover:text-gray-900 mb-4"
        >
          <ArrowLeft size={20} />
          Back to Dashboard
        </button>
        <h2 className="text-2xl font-bold text-gray-800 mb-2">Custom Question Sets</h2>
        <p className="text-gray-600">Create and manage your own interview question collections</p>
      </div>

      {/* Search and Filters */}
      <div className="flex flex-wrap gap-4 mb-6">
        <div className="flex-1 min-w-64">
          <div className="relative">
            <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 text-gray-400" size={20} />
            <input
              type="text"
              placeholder="Search question sets..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              className="w-full pl-10 pr-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-purple-500 focus:border-transparent"
            />
          </div>
        </div>

        <select
          value={filterTechStack}
          onChange={(e) => setFilterTechStack(e.target.value)}
          className="px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-purple-500 focus:border-transparent"
        >
          <option value="">All Tech Stacks</option>
          <option value="Java">Java</option>
          <option value="Python">Python</option>
          <option value="JavaScript">JavaScript</option>
          <option value="React">React</option>
          <option value="Node.js">Node.js</option>
          <option value="Spring Boot">Spring Boot</option>
        </select>

        <select
          value={filterLevel}
          onChange={(e) => setFilterLevel(e.target.value)}
          className="px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-purple-500 focus:border-transparent"
        >
          <option value="">All Levels</option>
          <option value="junior">Junior</option>
          <option value="mid">Mid-level</option>
          <option value="senior">Senior</option>
        </select>

        <button
          onClick={() => setShowCreateForm(true)}
          className="flex items-center gap-2 px-4 py-2 bg-purple-600 text-white rounded-lg hover:bg-purple-700"
        >
          <Plus size={16} />
          Create Set
        </button>
      </div>

      {/* Question Sets List */}
      {loading ? (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {Array.from({ length: 6 }, (_, i) => (
            <LoadingSkeleton key={i} variant="card" className="h-48" />
          ))}
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {filteredQuestionSets.map(set => (
            <div
              key={set.id}
              className="border border-gray-200 rounded-lg p-4 hover:border-purple-300 transition-colors"
            >
              <div className="flex items-start justify-between mb-2">
                <h3 className="font-semibold text-gray-900">{set.name}</h3>
                {set.isPublic && (
                  <Eye size={16} className="text-gray-400" title="Public question set" />
                )}
              </div>

              <p className="text-sm text-gray-600 mb-3 line-clamp-2">
                {set.description || `${set.questions?.length || 0} questions`}
              </p>

              <div className="space-y-1 mb-4">
                <div className="flex items-center justify-between text-xs text-gray-500">
                  <span>{set.techStack}</span>
                  <span className="capitalize">{set.level}</span>
                </div>
                <div className="flex items-center justify-between text-xs text-gray-500">
                  <span>{set.questions?.length || 0} questions</span>
                  {set.usageCount > 0 && (
                    <span>Used {set.usageCount} times</span>
                  )}
                </div>
              </div>

              <div className="flex items-center gap-2">
                <button
                  onClick={() => handleUseQuestionSet(set)}
                  className="flex-1 px-3 py-2 bg-purple-600 text-white text-sm rounded hover:bg-purple-700"
                >
                  Use Set
                </button>

                {isUserSet(set) && (
                  <>
                    <button
                      onClick={() => startEditing(set)}
                      className="p-2 text-gray-400 hover:text-gray-600"
                      title="Edit question set"
                    >
                      <Edit size={16} />
                    </button>
                    <button
                      onClick={() => handleDeleteQuestionSet(set.id)}
                      className="p-2 text-gray-400 hover:text-red-600"
                      title="Delete question set"
                    >
                      <Trash2 size={16} />
                    </button>
                  </>
                )}
              </div>
            </div>
          ))}

          {filteredQuestionSets.length === 0 && (
            <div className="col-span-full text-center py-12">
              <BookOpen size={48} className="mx-auto text-gray-400 mb-4" />
              <h3 className="text-lg font-semibold text-gray-900 mb-2">No question sets found</h3>
              <p className="text-gray-600">Try adjusting your search criteria or create a new question set.</p>
            </div>
          )}
        </div>
      )}

      {/* Create/Edit Question Set Modal */}
      {(showCreateForm || editingSet) && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-xl max-w-4xl w-full max-h-[90vh] overflow-hidden">
            <div className="flex items-center justify-between p-6 border-b border-gray-200">
              <h3 className="text-lg font-semibold text-gray-900">
                {editingSet ? 'Edit Question Set' : 'Create New Question Set'}
              </h3>
              <button
                onClick={cancelEditing}
                className="text-gray-400 hover:text-gray-600"
              >
                <X size={24} />
              </button>
            </div>

            <div className="p-6 max-h-[calc(90vh-140px)] overflow-y-auto">
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mb-6">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Set Name</label>
                  <input
                    type="text"
                    value={formData.name}
                    onChange={(e) => setFormData({...formData, name: e.target.value})}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-purple-500 focus:border-transparent"
                    placeholder="e.g., Java Spring Boot Interview Questions"
                  />
                </div>

                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Tech Stack</label>
                  <select
                    value={formData.techStack}
                    onChange={(e) => setFormData({...formData, techStack: e.target.value})}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-purple-500 focus:border-transparent"
                  >
                    <option value="Java">Java</option>
                    <option value="Python">Python</option>
                    <option value="JavaScript">JavaScript</option>
                    <option value="React">React</option>
                    <option value="Node.js">Node.js</option>
                    <option value="Spring Boot">Spring Boot</option>
                    <option value="Other">Other</option>
                  </select>
                </div>

                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Level</label>
                  <select
                    value={formData.level}
                    onChange={(e) => setFormData({...formData, level: e.target.value})}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-purple-500 focus:border-transparent"
                  >
                    <option value="junior">Junior</option>
                    <option value="mid">Mid-level</option>
                    <option value="senior">Senior</option>
                  </select>
                </div>
              </div>

              <div className="mb-6">
                <label className="block text-sm font-medium text-gray-700 mb-1">Description</label>
                <textarea
                  value={formData.description}
                  onChange={(e) => setFormData({...formData, description: e.target.value})}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-purple-500 focus:border-transparent"
                  rows="3"
                  placeholder="Describe what this question set covers..."
                />
              </div>

              {/* Questions Section */}
              <div className="mb-6">
                <div className="flex items-center justify-between mb-3">
                  <h4 className="text-md font-medium text-gray-900">Questions ({formData.questions.length})</h4>
                  <button
                    onClick={() => setShowQuestionEditor(!showQuestionEditor)}
                    className="text-sm text-purple-600 hover:text-purple-700"
                  >
                    {showQuestionEditor ? 'Hide Editor' : 'Add Questions'}
                  </button>
                </div>

                {showQuestionEditor && (
                  <div className="mb-4 p-4 bg-gray-50 rounded-lg">
                    <div className="flex gap-2 mb-2">
                      <input
                        type="text"
                        value={currentQuestion}
                        onChange={(e) => setCurrentQuestion(e.target.value)}
                        onKeyPress={(e) => e.key === 'Enter' && addQuestion()}
                        className="flex-1 px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-purple-500 focus:border-transparent"
                        placeholder="Enter a question..."
                      />
                      <button
                        onClick={addQuestion}
                        disabled={!currentQuestion.trim()}
                        className="px-4 py-2 bg-purple-600 text-white rounded-lg hover:bg-purple-700 disabled:opacity-50 disabled:cursor-not-allowed"
                      >
                        Add
                      </button>
                    </div>
                  </div>
                )}

                <div className="max-h-60 overflow-y-auto border border-gray-200 rounded-lg">
                  {formData.questions.length === 0 ? (
                    <div className="text-center py-8 text-gray-500">
                      No questions added yet. Click "Add Questions" to get started.
                    </div>
                  ) : (
                    <div className="divide-y divide-gray-200">
                      {formData.questions.map((question, index) => (
                        <div key={index} className="flex items-start gap-3 p-3">
                          <span className="text-sm font-medium text-gray-500 mt-0.5">
                            {index + 1}.
                          </span>
                          <span className="flex-1 text-sm text-gray-900">{question}</span>
                          <button
                            onClick={() => removeQuestion(index)}
                            className="text-gray-400 hover:text-red-600 p-1"
                          >
                            <X size={16} />
                          </button>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              </div>

              <div className="flex items-center justify-between">
                <label className="flex items-center gap-2">
                  <input
                    type="checkbox"
                    checked={formData.isPublic}
                    onChange={(e) => setFormData({...formData, isPublic: e.target.checked})}
                    className="rounded border-gray-300 text-purple-600 focus:ring-purple-500"
                  />
                  <span className="text-sm text-gray-700">Make this question set public</span>
                </label>

                <div className="flex gap-2">
                  <button
                    onClick={cancelEditing}
                    className="px-4 py-2 text-gray-600 border border-gray-300 rounded-lg hover:bg-gray-50"
                  >
                    Cancel
                  </button>
                  <button
                    onClick={editingSet ? handleUpdateQuestionSet : handleCreateQuestionSet}
                    disabled={!formData.name.trim() || formData.questions.length === 0}
                    className="px-4 py-2 bg-purple-600 text-white rounded-lg hover:bg-purple-700 disabled:opacity-50 disabled:cursor-not-allowed"
                  >
                    {editingSet ? 'Update Set' : 'Create Set'}
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      )}

      <ConfirmDialog
        isOpen={!!deleteConfirm}
        onClose={() => setDeleteConfirm(null)}
        onConfirm={confirmDeleteQuestionSet}
        title="Delete Question Set"
        message="Are you sure you want to delete this question set? This action cannot be undone."
        confirmText="Delete"
        cancelText="Cancel"
        type="danger"
      />
    </div>
  );
};

export default CustomQuestionSetPage;
