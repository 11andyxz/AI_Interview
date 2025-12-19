import React, { useState, useEffect } from 'react';
import { X, Save, Plus, Search, Star, Eye, Edit, Trash2, Copy } from 'lucide-react';
import LoadingSpinner from './common/LoadingSpinner';
import { useToast } from './common/useToast';
import ToastContainer from './common/ToastContainer';
import ConfirmDialog from './common/ConfirmDialog';

const InterviewTemplateModal = ({ isOpen, onClose, onSelectTemplate }) => {
  const [templates, setTemplates] = useState([]);
  const [userTemplates, setUserTemplates] = useState([]);
  const [loading, setLoading] = useState(false);
  const [searchTerm, setSearchTerm] = useState('');
  const [filterTechStack, setFilterTechStack] = useState('');
  const [filterLevel, setFilterLevel] = useState('');
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [editingTemplate, setEditingTemplate] = useState(null);
  const [deleteConfirm, setDeleteConfirm] = useState(null);
  const { toasts, removeToast, success, error: showError } = useToast();

  // Form state for creating/editing templates
  const [formData, setFormData] = useState({
    name: '',
    description: '',
    techStack: 'Java',
    level: 'mid',
    roleTitle: '',
    durationMinutes: 30,
    language: 'en',
    isPublic: false,
    tags: []
  });

  useEffect(() => {
    if (isOpen) {
      loadTemplates();
    }
  }, [isOpen]);

  const loadTemplates = async () => {
    try {
      setLoading(true);
      const accessToken = localStorage.getItem('accessToken');
      const headers = {
        'Content-Type': 'application/json',
      };
      if (accessToken) {
        headers['Authorization'] = `Bearer ${accessToken}`;
      }

      // Load all available templates
      const templatesResponse = await fetch('http://localhost:8080/api/templates', { headers });
      if (templatesResponse.ok) {
        const templatesData = await templatesResponse.json();
        setTemplates(templatesData);
      }

      // Load user's own templates
      const userTemplatesResponse = await fetch('http://localhost:8080/api/templates/my', { headers });
      if (userTemplatesResponse.ok) {
        const userTemplatesData = await userTemplatesResponse.json();
        setUserTemplates(userTemplatesData);
      }
    } catch (err) {
      console.error('Error loading templates:', err);
      showError('Failed to load templates');
    } finally {
      setLoading(false);
    }
  };

  const handleCreateTemplate = async () => {
    try {
      const accessToken = localStorage.getItem('accessToken');
      const headers = {
        'Content-Type': 'application/json',
      };
      if (accessToken) {
        headers['Authorization'] = `Bearer ${accessToken}`;
      }

      const response = await fetch('http://localhost:8080/api/templates', {
        method: 'POST',
        headers,
        body: JSON.stringify(formData)
      });

      if (response.ok) {
        const data = await response.json();
        success('Template created successfully');
        setShowCreateForm(false);
        resetForm();
        loadTemplates();
      } else {
        showError('Failed to create template');
      }
    } catch (err) {
      console.error('Error creating template:', err);
      showError('Error creating template');
    }
  };

  const handleUpdateTemplate = async () => {
    try {
      const accessToken = localStorage.getItem('accessToken');
      const headers = {
        'Content-Type': 'application/json',
      };
      if (accessToken) {
        headers['Authorization'] = `Bearer ${accessToken}`;
      }

      const response = await fetch(`http://localhost:8080/api/templates/${editingTemplate.id}`, {
        method: 'PUT',
        headers,
        body: JSON.stringify(formData)
      });

      if (response.ok) {
        success('Template updated successfully');
        setEditingTemplate(null);
        resetForm();
        loadTemplates();
      } else {
        showError('Failed to update template');
      }
    } catch (err) {
      console.error('Error updating template:', err);
      showError('Error updating template');
    }
  };

  const handleDeleteTemplate = (templateId) => {
    setDeleteConfirm(templateId);
  };

  const confirmDeleteTemplate = async () => {
    if (!deleteConfirm) return;

    try {
      const accessToken = localStorage.getItem('accessToken');
      const headers = {
        'Content-Type': 'application/json',
      };
      if (accessToken) {
        headers['Authorization'] = `Bearer ${accessToken}`;
      }

      const response = await fetch(`http://localhost:8080/api/templates/${deleteConfirm}`, {
        method: 'DELETE',
        headers
      });

      if (response.ok) {
        success('Template deleted successfully');
        loadTemplates();
      } else {
        showError('Failed to delete template');
      }
    } catch (err) {
      console.error('Error deleting template:', err);
      showError('Error deleting template');
    } finally {
      setDeleteConfirm(null);
    }
  };

  const handleUseTemplate = async (template) => {
    // Record template usage
    try {
      const accessToken = localStorage.getItem('accessToken');
      const headers = {
        'Content-Type': 'application/json',
      };
      if (accessToken) {
        headers['Authorization'] = `Bearer ${accessToken}`;
      }

      await fetch(`http://localhost:8080/api/templates/${template.id}/use`, {
        method: 'POST',
        headers
      });
    } catch (err) {
      console.error('Error recording template usage:', err);
    }

    // Convert template to interview configuration and pass to parent
    const interviewConfig = {
      title: template.roleTitle,
      techStack: template.techStack,
      level: template.level,
      durationMinutes: template.durationMinutes,
      language: template.language,
      templateId: template.id
    };

    onSelectTemplate(interviewConfig);
    onClose();
  };

  const resetForm = () => {
    setFormData({
      name: '',
      description: '',
      techStack: 'Java',
      level: 'mid',
      roleTitle: '',
      durationMinutes: 30,
      language: 'en',
      isPublic: false,
      tags: []
    });
  };

  const startEditing = (template) => {
    setEditingTemplate(template);
    setFormData({
      name: template.name || '',
      description: template.description || '',
      techStack: template.techStack || 'Java',
      level: template.level || 'mid',
      roleTitle: template.roleTitle || '',
      durationMinutes: template.durationMinutes || 30,
      language: template.language || 'en',
      isPublic: template.isPublic || false,
      tags: template.tags || []
    });
  };

  const cancelEditing = () => {
    setEditingTemplate(null);
    setShowCreateForm(false);
    resetForm();
  };

  const filteredTemplates = templates.filter(template => {
    const matchesSearch = template.name?.toLowerCase().includes(searchTerm.toLowerCase()) ||
                         template.description?.toLowerCase().includes(searchTerm.toLowerCase()) ||
                         template.roleTitle?.toLowerCase().includes(searchTerm.toLowerCase());
    const matchesTechStack = !filterTechStack || template.techStack === filterTechStack;
    const matchesLevel = !filterLevel || template.level === filterLevel;

    return matchesSearch && matchesTechStack && matchesLevel;
  });

  const isUserTemplate = (template) => {
    return userTemplates.some(userTemplate => userTemplate.id === template.id);
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 p-4">
      <ToastContainer toasts={toasts} removeToast={removeToast} />

      <div className="bg-white rounded-xl max-w-6xl w-full max-h-[90vh] overflow-hidden">
        <div className="flex items-center justify-between p-6 border-b border-gray-200">
          <h2 className="text-xl font-semibold text-gray-900">Interview Templates</h2>
          <button
            onClick={onClose}
            className="text-gray-400 hover:text-gray-600"
          >
            <X size={24} />
          </button>
        </div>

        <div className="p-6">
          {/* Search and Filters */}
          <div className="flex flex-wrap gap-4 mb-6">
            <div className="flex-1 min-w-64">
              <div className="relative">
                <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 text-gray-400" size={20} />
                <input
                  type="text"
                  placeholder="Search templates..."
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
              Create Template
            </button>
          </div>

          {/* Template List */}
          {loading ? (
            <div className="flex justify-center py-12">
              <LoadingSpinner size="lg" />
            </div>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4 max-h-96 overflow-y-auto">
              {filteredTemplates.map(template => (
                <div
                  key={template.id}
                  className="border border-gray-200 rounded-lg p-4 hover:border-purple-300 transition-colors"
                >
                  <div className="flex items-start justify-between mb-2">
                    <h3 className="font-semibold text-gray-900">{template.name}</h3>
                    {template.isPublic && (
                      <Eye size={16} className="text-gray-400" title="Public template" />
                    )}
                  </div>

                  <p className="text-sm text-gray-600 mb-3 line-clamp-2">
                    {template.description || `${template.roleTitle} interview template`}
                  </p>

                  <div className="space-y-1 mb-4">
                    <div className="flex items-center justify-between text-xs text-gray-500">
                      <span>{template.techStack}</span>
                      <span className="capitalize">{template.level}</span>
                    </div>
                    <div className="flex items-center justify-between text-xs text-gray-500">
                      <span>{template.durationMinutes} min</span>
                      <span>{template.language?.toUpperCase()}</span>
                    </div>
                    {template.usageCount > 0 && (
                      <div className="flex items-center gap-1 text-xs text-yellow-600">
                        <Star size={12} />
                        <span>Used {template.usageCount} times</span>
                      </div>
                    )}
                  </div>

                  <div className="flex items-center gap-2">
                    <button
                      onClick={() => handleUseTemplate(template)}
                      className="flex-1 px-3 py-2 bg-purple-600 text-white text-sm rounded hover:bg-purple-700"
                    >
                      Use Template
                    </button>

                    {isUserTemplate(template) && (
                      <>
                        <button
                          onClick={() => startEditing(template)}
                          className="p-2 text-gray-400 hover:text-gray-600"
                          title="Edit template"
                        >
                          <Edit size={16} />
                        </button>
                        <button
                          onClick={() => handleDeleteTemplate(template.id)}
                          className="p-2 text-gray-400 hover:text-red-600"
                          title="Delete template"
                        >
                          <Trash2 size={16} />
                        </button>
                      </>
                    )}
                  </div>
                </div>
              ))}

              {filteredTemplates.length === 0 && (
                <div className="col-span-full text-center py-12">
                  <div className="text-gray-400 mb-4">
                    <Search size={48} className="mx-auto" />
                  </div>
                  <h3 className="text-lg font-semibold text-gray-900 mb-2">No templates found</h3>
                  <p className="text-gray-600">Try adjusting your search criteria or create a new template.</p>
                </div>
              )}
            </div>
          )}
        </div>

        {/* Create/Edit Template Form */}
        {(showCreateForm || editingTemplate) && (
          <div className="border-t border-gray-200 p-6 bg-gray-50">
            <h3 className="text-lg font-semibold text-gray-900 mb-4">
              {editingTemplate ? 'Edit Template' : 'Create New Template'}
            </h3>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mb-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Template Name</label>
                <input
                  type="text"
                  value={formData.name}
                  onChange={(e) => setFormData({...formData, name: e.target.value})}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-purple-500 focus:border-transparent"
                  placeholder="e.g., Senior Java Developer Interview"
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Role Title</label>
                <input
                  type="text"
                  value={formData.roleTitle}
                  onChange={(e) => setFormData({...formData, roleTitle: e.target.value})}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-purple-500 focus:border-transparent"
                  placeholder="e.g., Senior Java Developer"
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

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Duration (minutes)</label>
                <input
                  type="number"
                  value={formData.durationMinutes}
                  onChange={(e) => setFormData({...formData, durationMinutes: parseInt(e.target.value) || 30})}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-purple-500 focus:border-transparent"
                  min="15"
                  max="120"
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Language</label>
                <select
                  value={formData.language}
                  onChange={(e) => setFormData({...formData, language: e.target.value})}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-purple-500 focus:border-transparent"
                >
                  <option value="en">English</option>
                  <option value="zh">中文</option>
                  <option value="ja">日本語</option>
                </select>
              </div>
            </div>

            <div className="mb-4">
              <label className="block text-sm font-medium text-gray-700 mb-1">Description</label>
              <textarea
                value={formData.description}
                onChange={(e) => setFormData({...formData, description: e.target.value})}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-purple-500 focus:border-transparent"
                rows="3"
                placeholder="Describe what this interview template covers..."
              />
            </div>

            <div className="flex items-center justify-between">
              <label className="flex items-center gap-2">
                <input
                  type="checkbox"
                  checked={formData.isPublic}
                  onChange={(e) => setFormData({...formData, isPublic: e.target.checked})}
                  className="rounded border-gray-300 text-purple-600 focus:ring-purple-500"
                />
                <span className="text-sm text-gray-700">Make this template public</span>
              </label>

              <div className="flex gap-2">
                <button
                  onClick={cancelEditing}
                  className="px-4 py-2 text-gray-600 border border-gray-300 rounded-lg hover:bg-gray-50"
                >
                  Cancel
                </button>
                <button
                  onClick={editingTemplate ? handleUpdateTemplate : handleCreateTemplate}
                  className="px-4 py-2 bg-purple-600 text-white rounded-lg hover:bg-purple-700"
                >
                  {editingTemplate ? 'Update Template' : 'Create Template'}
                </button>
              </div>
            </div>
          </div>
        )}
      </div>

      <ConfirmDialog
        isOpen={!!deleteConfirm}
        onClose={() => setDeleteConfirm(null)}
        onConfirm={confirmDeleteTemplate}
        title="Delete Template"
        message="Are you sure you want to delete this template? This action cannot be undone."
        confirmText="Delete"
        cancelText="Cancel"
        type="danger"
      />
    </div>
  );
};

export default InterviewTemplateModal;
