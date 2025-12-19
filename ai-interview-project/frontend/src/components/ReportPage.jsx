import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Download, ArrowLeft, FileText, BarChart3 } from 'lucide-react';
import LoadingSpinner from './common/LoadingSpinner';
import { useToast } from './common/useToast';
import ToastContainer from './common/ToastContainer';

const ReportPage = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const [report, setReport] = useState(null);
  const [loading, setLoading] = useState(true);
  const { toasts, removeToast, success, error } = useToast();

  useEffect(() => {
    loadReport();
  }, [id]);

  const loadReport = async () => {
    try {
      setLoading(true);
      const accessToken = localStorage.getItem('accessToken');
      const headers = {
        'Content-Type': 'application/json',
      };
      if (accessToken) {
        headers['Authorization'] = `Bearer ${accessToken}`;
      }

      const response = await fetch(`http://localhost:8080/api/interviews/${id}/report`, { headers });
      if (response.ok) {
        const data = await response.json();
        setReport(data);
      } else {
        error('Failed to load report');
      }
    } catch (err) {
      error('Error loading report');
    } finally {
      setLoading(false);
    }
  };

  const handleDownload = async (format = 'json') => {
    try {
      const accessToken = localStorage.getItem('accessToken');
      const headers = {};
      if (accessToken) {
        headers['Authorization'] = `Bearer ${accessToken}`;
      }

      const endpoint = format === 'pdf'
        ? `http://localhost:8080/api/interviews/${id}/report/download`
        : `http://localhost:8080/api/interviews/${id}/report/json`;

      const response = await fetch(endpoint, { headers });
      
      if (response.ok) {
        if (format === 'json') {
          const data = await response.json();
          const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
          const url = window.URL.createObjectURL(blob);
          const a = document.createElement('a');
          a.href = url;
          a.download = `interview-report-${id}.json`;
          document.body.appendChild(a);
          a.click();
          window.URL.revokeObjectURL(url);
          document.body.removeChild(a);
        } else {
          const blob = await response.blob();
          const url = window.URL.createObjectURL(blob);
          const a = document.createElement('a');
          a.href = url;
          a.download = `interview-report-${id}.pdf`;
          document.body.appendChild(a);
          a.click();
          window.URL.revokeObjectURL(url);
          document.body.removeChild(a);
        }
        success(`Report downloaded as ${format.toUpperCase()}`);
      } else {
        error('Failed to download report');
      }
    } catch (err) {
      error('Error downloading report');
    }
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center">
        <LoadingSpinner size="xl" />
      </div>
    );
  }

  if (!report) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center">
        <div className="text-center">
          <h2 className="text-2xl font-bold text-gray-900 mb-4">Report not found</h2>
          <button
            onClick={() => navigate('/')}
            className="px-6 py-2 bg-purple-600 text-white rounded-lg hover:bg-purple-700"
          >
            Go to Home
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50 p-8 ml-64">
      <ToastContainer toasts={toasts} removeToast={removeToast} />
      
      <div className="max-w-4xl mx-auto">
        <div className="flex items-center justify-between mb-8">
          <button
            onClick={() => navigate(-1)}
            className="flex items-center gap-2 text-gray-600 hover:text-gray-900"
          >
            <ArrowLeft size={20} />
            Back
          </button>
          <div className="flex gap-3">
            <button
              onClick={() => handleDownload('json')}
              className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 flex items-center gap-2"
            >
              <Download size={18} />
              Download JSON
            </button>
            <button
              onClick={() => handleDownload('pdf')}
              className="px-4 py-2 bg-purple-600 text-white rounded-lg hover:bg-purple-700 flex items-center gap-2"
            >
              <Download size={18} />
              Download PDF
            </button>
          </div>
        </div>

        <div className="bg-white rounded-lg shadow-lg p-8">
          <div className="flex items-center gap-3 mb-6">
            <BarChart3 size={32} className="text-purple-600" />
            <div>
              <h1 className="text-3xl font-bold text-gray-900">Interview Report</h1>
              <p className="text-gray-600">{report.title || 'Interview Report'}</p>
            </div>
          </div>

          <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-8">
            <div className="bg-gray-50 p-4 rounded-lg">
              <p className="text-sm text-gray-600">Status</p>
              <p className="text-lg font-semibold text-gray-900">{report.status}</p>
            </div>
            <div className="bg-gray-50 p-4 rounded-lg">
              <p className="text-sm text-gray-600">Total Questions</p>
              <p className="text-lg font-semibold text-gray-900">{report.totalQuestions || 0}</p>
            </div>
            <div className="bg-gray-50 p-4 rounded-lg">
              <p className="text-sm text-gray-600">Date</p>
              <p className="text-lg font-semibold text-gray-900">
                {new Date(report.date || report.createdAt).toLocaleDateString()}
              </p>
            </div>
            <div className="bg-gray-50 p-4 rounded-lg">
              <p className="text-sm text-gray-600">Language</p>
              <p className="text-lg font-semibold text-gray-900">{report.language || 'N/A'}</p>
            </div>
          </div>

          {report.statistics && (
            <div className="mb-8">
              <h2 className="text-xl font-bold text-gray-900 mb-4">Statistics</h2>
              <div className="bg-gray-50 p-6 rounded-lg">
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <p className="text-sm text-gray-600">Total Exchanges</p>
                    <p className="text-2xl font-bold text-gray-900">{report.statistics.totalExchanges || 0}</p>
                  </div>
                  <div>
                    <p className="text-sm text-gray-600">Average Answer Length</p>
                    <p className="text-2xl font-bold text-gray-900">
                      {report.statistics.averageAnswerLength?.toFixed(0) || 0} chars
                    </p>
                  </div>
                </div>
              </div>
            </div>
          )}

          {report.feedback && (
            <div className="mb-8">
              <h2 className="text-xl font-bold text-gray-900 mb-4">Feedback</h2>
              <div className="bg-blue-50 border border-blue-200 rounded-lg p-6">
                <p className="text-gray-800 whitespace-pre-wrap">{report.feedback}</p>
              </div>
            </div>
          )}

          {report.conversationHistory && report.conversationHistory.length > 0 && (
            <div>
              <h2 className="text-xl font-bold text-gray-900 mb-4">Conversation History</h2>
              <div className="space-y-4">
                {report.conversationHistory.map((qa, idx) => (
                  <div key={idx} className="border border-gray-200 rounded-lg p-4">
                    <div className="mb-3">
                      <div className="flex items-center gap-2 mb-2">
                        <FileText size={16} className="text-purple-600" />
                        <span className="text-sm font-semibold text-gray-700">Question {idx + 1}</span>
                      </div>
                      <p className="text-gray-800">{qa.questionText}</p>
                    </div>
                    <div>
                      <p className="text-sm font-semibold text-gray-700 mb-2">Answer:</p>
                      <p className="text-gray-600">{qa.answerText}</p>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default ReportPage;

