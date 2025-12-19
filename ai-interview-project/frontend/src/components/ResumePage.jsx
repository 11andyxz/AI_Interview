import React, { useState, useEffect } from 'react';
import { Upload, Download, Trash2, FileText, CheckCircle } from 'lucide-react';
import LoadingSpinner from './common/LoadingSpinner';
import EmptyState from './common/EmptyState';
import ConfirmDialog from './common/ConfirmDialog';
import { useToast } from './common/useToast';
import ToastContainer from './common/ToastContainer';

const ResumePage = () => {
  const [resumes, setResumes] = useState([]);
  const [loading, setLoading] = useState(true);
  const [deleteConfirm, setDeleteConfirm] = useState(null);
  const [uploading, setUploading] = useState(false);
  const { toasts, removeToast, success, error } = useToast();

  useEffect(() => {
    loadResumes();
  }, []);

  const loadResumes = async () => {
    try {
      setLoading(true);
      const accessToken = localStorage.getItem('accessToken');
      const headers = {
        'Content-Type': 'application/json',
      };
      if (accessToken) {
        headers['Authorization'] = `Bearer ${accessToken}`;
      }

      const response = await fetch('http://localhost:8080/api/user/resume', { headers });
      if (response.ok) {
        const data = await response.json();
        setResumes(data);
      } else {
        error('Failed to load resumes');
      }
    } catch (err) {
      error('Error loading resumes');
    } finally {
      setLoading(false);
    }
  };

  const handleUpload = async (e) => {
    const file = e.target.files[0];
    if (!file) return;

    try {
      setUploading(true);
      const accessToken = localStorage.getItem('accessToken');
      const formData = new FormData();
      formData.append('file', file);

      const headers = {};
      if (accessToken) {
        headers['Authorization'] = `Bearer ${accessToken}`;
      }

      const response = await fetch('http://localhost:8080/api/user/resume', {
        method: 'POST',
        headers,
        body: formData
      });

      if (response.ok) {
        success('Resume uploaded successfully');
        loadResumes();
      } else {
        error('Failed to upload resume');
      }
    } catch (err) {
      error('Error uploading resume');
    } finally {
      setUploading(false);
      e.target.value = ''; // Reset file input
    }
  };

  const handleDownload = async (id) => {
    try {
      const accessToken = localStorage.getItem('accessToken');
      const headers = {};
      if (accessToken) {
        headers['Authorization'] = `Bearer ${accessToken}`;
      }

      const response = await fetch(`http://localhost:8080/api/user/resume/${id}/download`, {
        headers
      });

      if (response.ok) {
        const blob = await response.blob();
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = resumes.find(r => r.id === id)?.fileName || 'resume';
        document.body.appendChild(a);
        a.click();
        window.URL.revokeObjectURL(url);
        document.body.removeChild(a);
        success('Resume downloaded successfully');
      } else {
        error('Failed to download resume');
      }
    } catch (err) {
      error('Error downloading resume');
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

      const response = await fetch(`http://localhost:8080/api/user/resume/${id}`, {
        method: 'DELETE',
        headers
      });

      if (response.ok) {
        success('Resume deleted successfully');
        setDeleteConfirm(null);
        loadResumes();
      } else {
        error('Failed to delete resume');
      }
    } catch (err) {
      error('Error deleting resume');
    }
  };

  const formatFileSize = (bytes) => {
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i];
  };

  return (
    <div className="p-8 ml-64">
      <ToastContainer toasts={toasts} removeToast={removeToast} />
      
      <div className="flex items-center justify-between mb-8">
        <h2 className="text-2xl font-bold text-gray-800">My Resume</h2>
        <label className="px-4 py-2 bg-purple-600 text-white rounded-lg hover:bg-purple-700 flex items-center gap-2 cursor-pointer">
          <Upload size={20} />
          {uploading ? 'Uploading...' : 'Upload Resume'}
          <input
            type="file"
            className="hidden"
            onChange={handleUpload}
            accept=".pdf,.doc,.docx"
            disabled={uploading}
          />
        </label>
      </div>

      {loading ? (
        <div className="flex justify-center py-12">
          <LoadingSpinner size="lg" />
        </div>
      ) : resumes.length === 0 ? (
        <EmptyState
          icon="file"
          title="No resumes uploaded"
          message="Upload your resume to get started"
          actionLabel="Upload Resume"
          onAction={() => document.querySelector('input[type="file"]')?.click()}
        />
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {resumes.map((resume) => (
            <div key={resume.id} className="bg-white p-6 rounded-xl shadow-sm border border-gray-100 hover:shadow-md transition-shadow">
              <div className="flex items-start justify-between mb-4">
                <div className="flex items-center gap-2">
                  <FileText size={24} className="text-purple-600" />
                  <div>
                    <h3 className="font-bold text-gray-800">{resume.fileName}</h3>
                    <p className="text-xs text-gray-500">
                      {formatFileSize(resume.fileSize || 0)}
                    </p>
                  </div>
                </div>
                {resume.analyzed && (
                  <CheckCircle size={20} className="text-green-600" title="Analyzed" />
                )}
              </div>
              
              <div className="flex gap-2 mt-4">
                <button
                  onClick={() => handleDownload(resume.id)}
                  className="flex-1 px-3 py-2 bg-blue-100 text-blue-700 rounded-lg hover:bg-blue-200 flex items-center justify-center gap-2 text-sm"
                >
                  <Download size={16} />
                  Download
                </button>
                <button
                  onClick={() => setDeleteConfirm(resume.id)}
                  className="px-3 py-2 bg-red-100 text-red-700 rounded-lg hover:bg-red-200 flex items-center justify-center gap-2 text-sm"
                >
                  <Trash2 size={16} />
                </button>
              </div>
              
              <p className="text-xs text-gray-400 mt-4">
                Uploaded: {new Date(resume.createdAt).toLocaleDateString()}
              </p>
            </div>
          ))}
        </div>
      )}

      <ConfirmDialog
        isOpen={!!deleteConfirm}
        onClose={() => setDeleteConfirm(null)}
        onConfirm={() => handleDelete(deleteConfirm)}
        title="Delete Resume"
        message="Are you sure you want to delete this resume? This action cannot be undone."
        confirmText="Delete"
        cancelText="Cancel"
        type="danger"
      />
    </div>
  );
};

export default ResumePage;

