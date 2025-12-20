import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Upload, Download, Trash2, FileText, CheckCircle, ArrowLeft, X } from 'lucide-react';
import LoadingSpinner from './common/LoadingSpinner';
import EmptyState from './common/EmptyState';
import ConfirmDialog from './common/ConfirmDialog';
import { useToast } from './common/useToast';
import ToastContainer from './common/ToastContainer';

const ResumePage = () => {
  const navigate = useNavigate();
  const [resumes, setResumes] = useState([]);
  const [loading, setLoading] = useState(true);
  const [deleteConfirm, setDeleteConfirm] = useState(null);
  const [uploading, setUploading] = useState(false);
  const [autoAnalyze, setAutoAnalyze] = useState(true);
  const [analyzingResumeId, setAnalyzingResumeId] = useState(null);
  const [analysisModal, setAnalysisModal] = useState(null);
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

      // Add autoAnalyze parameter
      formData.append('autoAnalyze', autoAnalyze.toString());

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

  const handleAnalyze = async (resumeId) => {
    try {
      setAnalyzingResumeId(resumeId);
      const accessToken = localStorage.getItem('accessToken');
      const headers = {
        'Authorization': accessToken ? `Bearer ${accessToken}` : undefined,
      };

      const response = await fetch(`http://localhost:8080/api/user/resume/${resumeId}/analyze`, {
        method: 'POST',
        headers
      });

      if (response.ok) {
        const data = await response.json();
        success('Resume analysis completed successfully');
        loadResumes(); // Refresh the list
        // Show analysis modal with results
        if (data.analysisData) {
          setAnalysisModal(data.analysisData);
        }
      } else {
        error('Failed to analyze resume');
      }
    } catch (err) {
      error('Error analyzing resume');
    } finally {
      setAnalyzingResumeId(null);
    }
  };

  const handleViewAnalysis = async (resumeId) => {
    try {
      const accessToken = localStorage.getItem('accessToken');
      const headers = {
        'Authorization': accessToken ? `Bearer ${accessToken}` : undefined,
      };

      const response = await fetch(`http://localhost:8080/api/user/resume/${resumeId}/analysis`, { headers });

      if (response.ok) {
        const data = await response.json();
        if (data.analysisData) {
          setAnalysisModal(data.analysisData);
        } else {
          error('Analysis data not available');
        }
      } else {
        error('Failed to load analysis data');
      }
    } catch (err) {
      error('Error loading analysis data');
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
        <h2 className="text-2xl font-bold text-gray-800">My Resume</h2>
        <div className="flex items-center gap-4">
          <label className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              checked={autoAnalyze}
              onChange={(e) => setAutoAnalyze(e.target.checked)}
              className="rounded"
            />
            Auto-analyze after upload
          </label>
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
                <div className="flex items-center gap-2">
                  {resume.analyzed ? (
                    <CheckCircle size={20} className="text-green-600" title="Analyzed" />
                  ) : (
                    <div className="w-5 h-5 border-2 border-gray-300 rounded-full" title="Not analyzed"></div>
                  )}
                </div>
              </div>

              {/* Analysis summary */}
              {resume.analyzed && (
                <div className="mb-4 p-3 bg-green-50 border border-green-200 rounded-lg">
                  <div className="text-sm text-green-800">
                    <div className="font-medium">Analysis Complete</div>
                    <div className="text-xs mt-1">Click "View Analysis" to see detailed results</div>
                  </div>
                </div>
              )}
              
              <div className="flex gap-2 mt-4">
                <button
                  onClick={() => handleDownload(resume.id)}
                  className="flex-1 px-3 py-2 bg-blue-100 text-blue-700 rounded-lg hover:bg-blue-200 flex items-center justify-center gap-2 text-sm"
                >
                  <Download size={16} />
                  Download
                </button>
                {resume.analyzed ? (
                  <button
                    onClick={() => handleViewAnalysis(resume.id)}
                    className="px-3 py-2 bg-purple-100 text-purple-700 rounded-lg hover:bg-purple-200 flex items-center justify-center gap-2 text-sm"
                  >
                    <FileText size={16} />
                    View Analysis
                  </button>
                ) : (
                  <button
                    onClick={() => handleAnalyze(resume.id)}
                    disabled={analyzingResumeId === resume.id}
                    className="px-3 py-2 bg-orange-100 text-orange-700 rounded-lg hover:bg-orange-200 flex items-center justify-center gap-2 text-sm disabled:opacity-50"
                  >
                    {analyzingResumeId === resume.id ? (
                      <>
                        <LoadingSpinner size="sm" />
                        Analyzing...
                      </>
                    ) : (
                      <>
                        <FileText size={16} />
                        Analyze
                      </>
                    )}
                  </button>
                )}
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

      {/* Analysis Results Modal */}
      {analysisModal && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-xl max-w-2xl w-full max-h-[80vh] overflow-hidden">
            <div className="flex items-center justify-between p-6 border-b border-gray-200">
              <h3 className="text-lg font-semibold text-gray-900">Resume Analysis Results</h3>
              <button
                onClick={() => setAnalysisModal(null)}
                className="text-gray-400 hover:text-gray-600"
              >
                <X size={24} />
              </button>
            </div>

            <div className="p-6 overflow-y-auto max-h-[calc(80vh-120px)]">
              <div className="space-y-4">
                <div className="grid grid-cols-2 gap-4">
                  <div className="p-4 bg-blue-50 border border-blue-200 rounded-lg">
                    <div className="text-sm font-medium text-blue-800">Experience Level</div>
                    <div className="text-lg font-bold text-blue-900">{analysisModal.level || 'N/A'}</div>
                  </div>
                  <div className="p-4 bg-green-50 border border-green-200 rounded-lg">
                    <div className="text-sm font-medium text-green-800">Years of Experience</div>
                    <div className="text-lg font-bold text-green-900">{analysisModal.experienceYears || 'N/A'}</div>
                  </div>
                </div>

                <div className="p-4 bg-purple-50 border border-purple-200 rounded-lg">
                  <div className="text-sm font-medium text-purple-800 mb-2">Technical Skills</div>
                  <div className="flex flex-wrap gap-2">
                    {(analysisModal.techStack || []).map((tech, index) => (
                      <span key={index} className="px-3 py-1 bg-purple-100 text-purple-700 rounded-full text-sm">
                        {tech}
                      </span>
                    ))}
                  </div>
                </div>

                <div className="p-4 bg-yellow-50 border border-yellow-200 rounded-lg">
                  <div className="text-sm font-medium text-yellow-800 mb-2">Main Skill Areas</div>
                  <div className="flex flex-wrap gap-2">
                    {(analysisModal.mainSkillAreas || []).map((area, index) => (
                      <span key={index} className="px-3 py-1 bg-yellow-100 text-yellow-700 rounded-full text-sm">
                        {area}
                      </span>
                    ))}
                  </div>
                </div>

                <div className="p-4 bg-gray-50 border border-gray-200 rounded-lg">
                  <div className="text-sm font-medium text-gray-800 mb-2">Education</div>
                  <div className="text-gray-900">{analysisModal.education || 'Not specified'}</div>
                </div>

                <div className="p-4 bg-indigo-50 border border-indigo-200 rounded-lg">
                  <div className="text-sm font-medium text-indigo-800 mb-2">Professional Summary</div>
                  <div className="text-indigo-900">{analysisModal.summary || 'No summary available'}</div>
                </div>
              </div>
            </div>

            <div className="flex justify-end gap-3 p-6 border-t border-gray-200">
              <button
                onClick={() => setAnalysisModal(null)}
                className="px-4 py-2 bg-gray-100 text-gray-700 rounded-lg hover:bg-gray-200"
              >
                Close
              </button>
            </div>
          </div>
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

