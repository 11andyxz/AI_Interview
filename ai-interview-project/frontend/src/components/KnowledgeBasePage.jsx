import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Plus, Edit, Trash2, Book, Database, ArrowLeft } from 'lucide-react';
import LoadingSpinner from './common/LoadingSpinner';
import EmptyState from './common/EmptyState';
import ConfirmDialog from './common/ConfirmDialog';
import { useToast } from './common/useToast';
import ToastContainer from './common/ToastContainer';

const KnowledgeBasePage = () => {
  const navigate = useNavigate();
  const [knowledgeBases, setKnowledgeBases] = useState([]);
  const [loading, setLoading] = useState(true);
  const [filter, setFilter] = useState('all'); // all, user, system
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [editingKb, setEditingKb] = useState(null);
  const [deleteConfirm, setDeleteConfirm] = useState(null);
  const [formData, setFormData] = useState({ name: '', description: '', content: '' });
  const { toasts, removeToast, success, error } = useToast();

  useEffect(() => {
    loadKnowledgeBases();
  }, [filter]);

  const loadKnowledgeBases = async () => {
    try {
      setLoading(true);
      const accessToken = localStorage.getItem('accessToken');
      const headers = {
        'Content-Type': 'application/json',
      };
      if (accessToken) {
        headers['Authorization'] = `Bearer ${accessToken}`;
      }

      const url = filter === 'all'
        ? 'http://localhost:8080/api/knowledge-base'
        : `http://localhost:8080/api/knowledge-base?type=${filter}`;
      
      const response = await fetch(url, { headers });
      if (response.ok) {
        const data = await response.json();
        setKnowledgeBases(data);
      } else {
        error('Failed to load knowledge bases');
      }
    } catch (err) {
      error('Error loading knowledge bases');
    } finally {
      setLoading(false);
    }
  };

  const handleCreate = async () => {
    if (!formData.name.trim()) {
      error('Name is required');
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

      const response = await fetch('http://localhost:8080/api/knowledge-base', {
        method: 'POST',
        headers,
        body: JSON.stringify(formData)
      });

      if (response.ok) {
        success('Knowledge base created successfully');
        setShowCreateModal(false);
        setFormData({ name: '', description: '', content: '' });
        loadKnowledgeBases();
      } else {
        error('Failed to create knowledge base');
      }
    } catch (err) {
      error('Error creating knowledge base');
    }
  };

  const handleUpdate = async () => {
    if (!editingKb || !formData.name.trim()) {
      error('Name is required');
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

      const response = await fetch(`http://localhost:8080/api/knowledge-base/${editingKb.id}`, {
        method: 'PUT',
        headers,
        body: JSON.stringify(formData)
      });

      if (response.ok) {
        success('Knowledge base updated successfully');
        setEditingKb(null);
        setFormData({ name: '', description: '', content: '' });
        loadKnowledgeBases();
      } else {
        error('Failed to update knowledge base');
      }
    } catch (err) {
      error('Error updating knowledge base');
    }
  };

  const handleDelete = async (id) => {
    try {
      const accessToken = localStorage.getItem('accessToken');
      const headers = {
        'Content-Type': 'application/json',
      };
      if (accessToken) {
        headers['Authorization'] = `Bearer ${accessToken}`;
      }

      const response = await fetch(`http://localhost:8080/api/knowledge-base/${id}`, {
        method: 'DELETE',
        headers
      });

      if (response.ok) {
        success('Knowledge base deleted successfully');
        setDeleteConfirm(null);
        loadKnowledgeBases();
      } else {
        const data = await response.json();
        error(data.error || 'Failed to delete knowledge base');
      }
    } catch (err) {
      error('Error deleting knowledge base');
    }
  };

  const openEditModal = (kb) => {
    if (kb.type === 'system') {
      error('System knowledge bases cannot be edited');
      return;
    }
    setEditingKb(kb);
    setFormData({
      name: kb.name,
      description: kb.description || '',
      content: kb.content || ''
    });
    setShowCreateModal(true);
  };

  return (
    <div className="p-8 ml-64">
      <ToastContainer toasts={toasts} removeToast={removeToast} />
      
      <div className="mb-8">
        <button
          onClick={() => navigate('/')}
          className="flex items-center gap-2 text-gray-600 hover:text-gray-900 mb-4"
        >
          <ArrowLeft size={20} />
          Back to Dashboard
        </button>
      </div>
      
      <div className="flex items-center justify-between mb-8">
        <h2 className="text-2xl font-bold text-gray-800">Knowledge Base</h2>
        <button
          onClick={() => {
            setEditingKb(null);
            setFormData({ name: '', description: '', content: '' });
            setShowCreateModal(true);
          }}
          data-testid="create-knowledge-base-button"
          className="px-4 py-2 bg-purple-600 text-white rounded-lg hover:bg-purple-700 flex items-center gap-2"
        >
          <Plus size={20} />
          New Knowledge Base
        </button>
      </div>

      <div className="mb-6 flex gap-2">
        <button
          onClick={() => setFilter('all')}
          className={`px-4 py-2 rounded-lg ${filter === 'all' ? 'bg-purple-600 text-white' : 'bg-gray-200 text-gray-700'}`}
        >
          All
        </button>
        <button
          onClick={() => setFilter('user')}
          className={`px-4 py-2 rounded-lg ${filter === 'user' ? 'bg-purple-600 text-white' : 'bg-gray-200 text-gray-700'}`}
        >
          My Knowledge Bases
        </button>
        <button
          onClick={() => setFilter('system')}
          className={`px-4 py-2 rounded-lg ${filter === 'system' ? 'bg-purple-600 text-white' : 'bg-gray-200 text-gray-700'}`}
        >
          System Knowledge Bases
        </button>
      </div>

      {loading ? (
        <div className="flex justify-center py-12">
          <LoadingSpinner size="lg" />
        </div>
      ) : knowledgeBases.length === 0 ? (
        <EmptyState
          icon="file"
          title="No knowledge bases"
          message="Create your first knowledge base to get started"
          actionLabel="Create Knowledge Base"
          onAction={() => setShowCreateModal(true)}
        />
      ) : (
        <div data-testid="knowledge-bases-list" className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {knowledgeBases.map((kb) => (
            <div key={kb.id} className="bg-white p-6 rounded-xl shadow-sm border border-gray-100 hover:shadow-md transition-shadow">
              <div className="flex items-start justify-between mb-4">
                <div className="flex items-center gap-2">
                  {kb.type === 'system' ? (
                    <Database size={20} className="text-blue-600" />
                  ) : (
                    <Book size={20} className="text-purple-600" />
                  )}
                  <span className={`text-xs px-2 py-1 rounded-full ${
                    kb.type === 'system' ? 'bg-blue-100 text-blue-700' : 'bg-purple-100 text-purple-700'
                  }`}>
                    {kb.type}
                  </span>
                </div>
                {kb.type === 'user' && (
                  <div className="flex gap-2">
                    <button
                      onClick={() => openEditModal(kb)}
                      className="text-gray-400 hover:text-blue-600"
                    >
                      <Edit size={18} />
                    </button>
                    <button
                      onClick={() => setDeleteConfirm(kb.id)}
                      className="text-gray-400 hover:text-red-600"
                    >
                      <Trash2 size={18} />
                    </button>
                  </div>
                )}
              </div>
              <h3 className="font-bold text-gray-800 mb-2">{kb.name}</h3>
              <p className="text-sm text-gray-600 line-clamp-3 mb-4">
                {kb.description || 'No description'}
              </p>
              <p className="text-xs text-gray-400">
                {new Date(kb.createdAt).toLocaleDateString()}
              </p>
            </div>
          ))}
        </div>
      )}

      {/* Create/Edit Modal */}
      {showCreateModal && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg shadow-xl max-w-2xl w-full mx-4 p-6">
            <h3 className="text-xl font-bold mb-4">
              {editingKb ? 'Edit Knowledge Base' : 'Create New Knowledge Base'}
            </h3>
            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">Name *</label>
                <input
                  type="text"
                  value={formData.name}
                  onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                  className="w-full px-4 py-2 border border-gray-300 rounded-lg"
                  placeholder="Knowledge base name"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">Description</label>
                <textarea
                  value={formData.description}
                  onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                  className="w-full px-4 py-2 border border-gray-300 rounded-lg h-24"
                  placeholder="Description"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">Content (JSON)</label>
                <textarea
                  value={formData.content}
                  onChange={(e) => setFormData({ ...formData, content: e.target.value })}
                  className="w-full px-4 py-2 border border-gray-300 rounded-lg h-32 font-mono text-sm"
                  placeholder='{"key": "value"}'
                />
              </div>
              <div className="flex justify-end gap-3">
                <button
                  onClick={() => {
                    setShowCreateModal(false);
                    setEditingKb(null);
                    setFormData({ name: '', description: '', content: '' });
                  }}
                  className="px-4 py-2 bg-gray-200 text-gray-700 rounded-lg hover:bg-gray-300"
                >
                  Cancel
                </button>
                <button
                  onClick={editingKb ? handleUpdate : handleCreate}
                  className="px-4 py-2 bg-purple-600 text-white rounded-lg hover:bg-purple-700"
                >
                  {editingKb ? 'Update' : 'Create'}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      <ConfirmDialog
        isOpen={!!deleteConfirm}
        onClose={() => setDeleteConfirm(null)}
        onConfirm={() => handleDelete(deleteConfirm)}
        title="Delete Knowledge Base"
        message="Are you sure you want to delete this knowledge base? This action cannot be undone."
        confirmText="Delete"
        cancelText="Cancel"
        type="danger"
      />
    </div>
  );
};

export default KnowledgeBasePage;

