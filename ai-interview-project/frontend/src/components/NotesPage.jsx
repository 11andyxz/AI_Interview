import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Plus, Edit, Trash2, FileText, MessageSquare, ArrowLeft } from 'lucide-react';
import LoadingSpinner from './common/LoadingSpinner';
import EmptyState from './common/EmptyState';
import ConfirmDialog from './common/ConfirmDialog';
import { useToast } from './common/useToast';
import ToastContainer from './common/ToastContainer';

const NotesPage = () => {
  const navigate = useNavigate();
  const [notes, setNotes] = useState([]);
  const [loading, setLoading] = useState(true);
  const [filter, setFilter] = useState('all'); // all, interview, general
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [editingNote, setEditingNote] = useState(null);
  const [deleteConfirm, setDeleteConfirm] = useState(null);
  const [formData, setFormData] = useState({ title: '', content: '', type: 'general', interviewId: '' });
  const { toasts, removeToast, success, error } = useToast();

  useEffect(() => {
    loadNotes();
  }, [filter]);

  const loadNotes = async () => {
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
        ? 'http://localhost:8080/api/notes'
        : `http://localhost:8080/api/notes?type=${filter}`;
      
      const response = await fetch(url, { headers });
      if (response.ok) {
        const data = await response.json();
        setNotes(data);
      } else {
        error('Failed to load notes');
      }
    } catch (err) {
      error('Error loading notes');
    } finally {
      setLoading(false);
    }
  };

  const handleCreate = async () => {
    if (!formData.title.trim()) {
      error('Title is required');
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

      const response = await fetch('http://localhost:8080/api/notes', {
        method: 'POST',
        headers,
        body: JSON.stringify(formData)
      });

      if (response.ok) {
        success('Note created successfully');
        setShowCreateModal(false);
        setFormData({ title: '', content: '', type: 'general', interviewId: '' });
        loadNotes();
      } else {
        error('Failed to create note');
      }
    } catch (err) {
      error('Error creating note');
    }
  };

  const handleUpdate = async () => {
    if (!editingNote || !formData.title.trim()) {
      error('Title is required');
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

      const response = await fetch(`http://localhost:8080/api/notes/${editingNote.id}`, {
        method: 'PUT',
        headers,
        body: JSON.stringify({
          title: formData.title,
          content: formData.content
        })
      });

      if (response.ok) {
        success('Note updated successfully');
        setEditingNote(null);
        setFormData({ title: '', content: '', type: 'general', interviewId: '' });
        loadNotes();
      } else {
        error('Failed to update note');
      }
    } catch (err) {
      error('Error updating note');
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

      const response = await fetch(`http://localhost:8080/api/notes/${id}`, {
        method: 'DELETE',
        headers
      });

      if (response.ok) {
        success('Note deleted successfully');
        setDeleteConfirm(null);
        loadNotes();
      } else {
        error('Failed to delete note');
      }
    } catch (err) {
      error('Error deleting note');
    }
  };

  const openEditModal = (note) => {
    setEditingNote(note);
    setFormData({
      title: note.title,
      content: note.content || '',
      type: note.type,
      interviewId: note.interviewId || ''
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
        <h2 className="text-2xl font-bold text-gray-800">My Notes</h2>
        <button
          onClick={() => {
            setEditingNote(null);
            setFormData({ title: '', content: '', type: 'general', interviewId: '' });
            setShowCreateModal(true);
          }}
          className="px-4 py-2 bg-purple-600 text-white rounded-lg hover:bg-purple-700 flex items-center gap-2"
        >
          <Plus size={20} />
          New Note
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
          onClick={() => setFilter('interview')}
          className={`px-4 py-2 rounded-lg ${filter === 'interview' ? 'bg-purple-600 text-white' : 'bg-gray-200 text-gray-700'}`}
        >
          Interview Notes
        </button>
        <button
          onClick={() => setFilter('general')}
          className={`px-4 py-2 rounded-lg ${filter === 'general' ? 'bg-purple-600 text-white' : 'bg-gray-200 text-gray-700'}`}
        >
          General Notes
        </button>
      </div>

      {loading ? (
        <div className="flex justify-center py-12">
          <LoadingSpinner size="lg" />
        </div>
      ) : notes.length === 0 ? (
        <EmptyState
          icon="file"
          title="No notes yet"
          message="Create your first note to get started"
          actionLabel="Create Note"
          onAction={() => setShowCreateModal(true)}
        />
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {notes.map((note) => (
            <div key={note.id} className="bg-white p-6 rounded-xl shadow-sm border border-gray-100 hover:shadow-md transition-shadow">
              <div className="flex items-start justify-between mb-4">
                <div className="flex items-center gap-2">
                  {note.type === 'interview' ? (
                    <MessageSquare size={20} className="text-purple-600" />
                  ) : (
                    <FileText size={20} className="text-gray-600" />
                  )}
                  <span className="text-xs px-2 py-1 bg-purple-100 text-purple-700 rounded-full">
                    {note.type}
                  </span>
                </div>
                <div className="flex gap-2">
                  <button
                    onClick={() => openEditModal(note)}
                    className="text-gray-400 hover:text-blue-600"
                  >
                    <Edit size={18} />
                  </button>
                  <button
                    onClick={() => setDeleteConfirm(note.id)}
                    className="text-gray-400 hover:text-red-600"
                  >
                    <Trash2 size={18} />
                  </button>
                </div>
              </div>
              <h3 className="font-bold text-gray-800 mb-2">{note.title}</h3>
              <p className="text-sm text-gray-600 line-clamp-3 mb-4">
                {note.content || 'No content'}
              </p>
              <p className="text-xs text-gray-400">
                {new Date(note.createdAt).toLocaleDateString()}
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
              {editingNote ? 'Edit Note' : 'Create New Note'}
            </h3>
            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">Type</label>
                <select
                  value={formData.type}
                  onChange={(e) => setFormData({ ...formData, type: e.target.value })}
                  className="w-full px-4 py-2 border border-gray-300 rounded-lg"
                  disabled={!!editingNote}
                >
                  <option value="general">General</option>
                  <option value="interview">Interview</option>
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">Title *</label>
                <input
                  type="text"
                  value={formData.title}
                  onChange={(e) => setFormData({ ...formData, title: e.target.value })}
                  className="w-full px-4 py-2 border border-gray-300 rounded-lg"
                  placeholder="Note title"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">Content</label>
                <textarea
                  value={formData.content}
                  onChange={(e) => setFormData({ ...formData, content: e.target.value })}
                  className="w-full px-4 py-2 border border-gray-300 rounded-lg h-32"
                  placeholder="Note content"
                />
              </div>
              <div className="flex justify-end gap-3">
                <button
                  onClick={() => {
                    setShowCreateModal(false);
                    setEditingNote(null);
                    setFormData({ title: '', content: '', type: 'general', interviewId: '' });
                  }}
                  className="px-4 py-2 bg-gray-200 text-gray-700 rounded-lg hover:bg-gray-300"
                >
                  Cancel
                </button>
                <button
                  onClick={editingNote ? handleUpdate : handleCreate}
                  className="px-4 py-2 bg-purple-600 text-white rounded-lg hover:bg-purple-700"
                >
                  {editingNote ? 'Update' : 'Create'}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Delete Confirmation */}
      <ConfirmDialog
        isOpen={!!deleteConfirm}
        onClose={() => setDeleteConfirm(null)}
        onConfirm={() => handleDelete(deleteConfirm)}
        title="Delete Note"
        message="Are you sure you want to delete this note? This action cannot be undone."
        confirmText="Delete"
        cancelText="Cancel"
        type="danger"
      />
    </div>
  );
};

export default NotesPage;

