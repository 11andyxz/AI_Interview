import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Download, ArrowLeft, FileText, BarChart3, TrendingUp, Target, Award, Play, Square } from 'lucide-react';
import {
  RadarChart,
  PolarGrid,
  PolarAngleAxis,
  PolarRadiusAxis,
  Radar,
  ResponsiveContainer,
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  BarChart,
  Bar
} from 'recharts';
import LoadingSpinner from './common/LoadingSpinner';
import { ReportSkeleton } from './common/LoadingSkeleton';
import { useToast } from './common/useToast';
import ToastContainer from './common/ToastContainer';

const ReportPage = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const [report, setReport] = useState(null);
  const [historicalReports, setHistoricalReports] = useState([]);
  const [loading, setLoading] = useState(true);
  const [recordings, setRecordings] = useState([]);
  const [playingRecording, setPlayingRecording] = useState(null);
  const [audioElement, setAudioElement] = useState(null);
  const { toasts, removeToast, success, error: showError } = useToast();

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

      // Load historical reports for comparison
      await loadHistoricalReports(headers);

      // Load recordings for this interview
      await loadRecordings(headers);
      } else {
        showError('Failed to load report');
      }
    } catch (err) {
      showError('Error loading report');
    } finally {
      setLoading(false);
    }
  };

  const loadHistoricalReports = async (headers) => {
    try {
      // Get user info
      const user = JSON.parse(localStorage.getItem('user') || '{}');
      if (!user.id) return;

      // Load all user interviews
      const interviewsResponse = await fetch('http://localhost:8080/api/interviews', { headers });
      if (interviewsResponse.ok) {
        const interviews = await interviewsResponse.json();
        const completedInterviews = interviews.filter(i => i.status === 'Completed' && i.id !== id);

        // Load reports for last 5 completed interviews
        const historicalData = [];
        for (const interview of completedInterviews.slice(0, 5)) {
          try {
            const reportResponse = await fetch(`http://localhost:8080/api/interviews/${interview.id}/report`, { headers });
            if (reportResponse.ok) {
              const reportData = await reportResponse.json();
              historicalData.push({
                id: interview.id,
                title: reportData.title,
                date: reportData.date,
                averageScore: calculateAverageScore(reportData.conversationHistory || [])
              });
            }
          } catch (e) {
            // Skip failed reports
          }
        }
        setHistoricalReports(historicalData);
      }
    } catch (err) {
      console.error('Error loading historical reports:', err);
    }
  };

  const loadRecordings = async (headers) => {
    try {
      const response = await fetch(`http://localhost:8080/api/interviews/${id}/recordings`, { headers });
      if (response.ok) {
        const recordingsData = await response.json();
        setRecordings(recordingsData);
      }
    } catch (err) {
      console.error('Error loading recordings:', err);
    }
  };

  const playRecording = (recording) => {
    if (playingRecording === recording.id) {
      // Stop playing
      if (audioElement) {
        audioElement.pause();
        audioElement.currentTime = 0;
      }
      setPlayingRecording(null);
      setAudioElement(null);
    } else {
      // Start playing
      if (audioElement) {
        audioElement.pause();
      }

      const audio = new Audio(`http://localhost:8080/api/interviews/recording/${recording.id}/download`);
      audio.addEventListener('ended', () => {
        setPlayingRecording(null);
        setAudioElement(null);
      });

      audio.play();
      setPlayingRecording(recording.id);
      setAudioElement(audio);
    }
  };

  const downloadRecording = (recording) => {
    const link = document.createElement('a');
    link.href = `http://localhost:8080/api/interviews/recording/${recording.id}/download`;
    link.download = recording.originalFilename || `interview-recording-${recording.id}.webm`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  const calculateAverageScore = (conversationHistory) => {
    if (!conversationHistory || conversationHistory.length === 0) return 0;

    const scores = conversationHistory
      .map(qa => qa.score)
      .filter(score => score != null);

    if (scores.length === 0) return 0;
    return scores.reduce((sum, score) => sum + score, 0) / scores.length;
  };

  // Prepare radar chart data for skills
  const getSkillsRadarData = () => {
    if (!report?.conversationHistory) return [];

    const skills = { technicalAccuracy: 0, depth: 0, experience: 0, communication: 0 };
    let count = 0;

    report.conversationHistory.forEach(qa => {
      if (qa.detailedScores) {
        skills.technicalAccuracy += qa.detailedScores.technicalAccuracy || 0;
        skills.depth += qa.detailedScores.depth || 0;
        skills.experience += qa.detailedScores.experience || 0;
        skills.communication += qa.detailedScores.communication || 0;
        count++;
      }
    });

    if (count === 0) return [];

    return [
      { skill: 'Technical\nAccuracy', value: Math.round(skills.technicalAccuracy / count) },
      { skill: 'Depth', value: Math.round(skills.depth / count) },
      { skill: 'Experience', value: Math.round(skills.experience / count) },
      { skill: 'Communication', value: Math.round(skills.communication / count) }
    ];
  };

  // Prepare trend chart data
  const getTrendData = () => {
    if (!report?.conversationHistory) return [];

    return report.conversationHistory.map((qa, index) => ({
      question: `Q${index + 1}`,
      score: qa.score || 0,
      technicalAccuracy: qa.detailedScores?.technicalAccuracy || 0,
      depth: qa.detailedScores?.depth || 0,
      experience: qa.detailedScores?.experience || 0,
      communication: qa.detailedScores?.communication || 0
    }));
  };

  // Prepare comparison chart data
  const getComparisonData = () => {
    const currentScore = calculateAverageScore(report?.conversationHistory || []);

    return [
      ...historicalReports.map(h => ({
        name: h.title?.substring(0, 20) + '...' || 'Previous',
        score: h.averageScore,
        date: new Date(h.date).toLocaleDateString()
      })),
      {
        name: 'Current Interview',
        score: currentScore,
        date: new Date().toLocaleDateString(),
        current: true
      }
    ];
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
      <div className="min-h-screen bg-gray-50 p-8 ml-64">
        <ReportSkeleton />
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
          <div className="flex gap-3" data-tour="download-buttons">
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
          <div className="flex items-center gap-3 mb-6" data-tour="report-summary">
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

          {/* Skills Radar Chart */}
          {getSkillsRadarData().length > 0 && (
            <div className="mb-8">
              <h2 className="text-xl font-bold text-gray-900 mb-4 flex items-center gap-2">
                <Target size={24} className="text-purple-600" />
                Skills Assessment
              </h2>
              <div className="bg-white border border-gray-200 rounded-lg p-6">
                <ResponsiveContainer width="100%" height={300}>
                  <RadarChart data={getSkillsRadarData()}>
                    <PolarGrid />
                    <PolarAngleAxis dataKey="skill" />
                    <PolarRadiusAxis angle={90} domain={[0, 10]} />
                    <Radar
                      name="Score"
                      dataKey="value"
                      stroke="#6366f1"
                      fill="#6366f1"
                      fillOpacity={0.3}
                      strokeWidth={2}
                    />
                    <Tooltip />
                  </RadarChart>
                </ResponsiveContainer>
              </div>
            </div>
          )}

          {/* Performance Trend Chart */}
          {getTrendData().length > 1 && (
            <div className="mb-8">
              <h2 className="text-xl font-bold text-gray-900 mb-4 flex items-center gap-2">
                <TrendingUp size={24} className="text-green-600" />
                Performance Trend
              </h2>
              <div className="bg-white border border-gray-200 rounded-lg p-6">
                <ResponsiveContainer width="100%" height={300}>
                  <LineChart data={getTrendData()}>
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis dataKey="question" />
                    <YAxis domain={[0, 100]} />
                    <Tooltip />
                    <Legend />
                    <Line
                      type="monotone"
                      dataKey="score"
                      stroke="#6366f1"
                      strokeWidth={2}
                      name="Overall Score"
                    />
                  </LineChart>
                </ResponsiveContainer>
              </div>
            </div>
          )}

          {/* Historical Comparison */}
          {historicalReports.length > 0 && (
            <div className="mb-8">
              <h2 className="text-xl font-bold text-gray-900 mb-4 flex items-center gap-2">
                <Award size={24} className="text-yellow-600" />
                Performance Comparison
              </h2>
              <div className="bg-white border border-gray-200 rounded-lg p-6">
                <ResponsiveContainer width="100%" height={300}>
                  <BarChart data={getComparisonData()}>
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis dataKey="name" />
                    <YAxis domain={[0, 100]} />
                    <Tooltip
                      formatter={(value, name) => [value.toFixed(1), 'Average Score']}
                      labelFormatter={(label) => {
                        const item = getComparisonData().find(d => d.name === label);
                        return item ? `${label} (${item.date})` : label;
                      }}
                    />
                    <Bar
                      dataKey="score"
                      fill="#6366f1"
                      radius={[4, 4, 0, 0]}
                    />
                  </BarChart>
                </ResponsiveContainer>
                <p className="text-sm text-gray-600 mt-4 text-center">
                  Compare your current performance with previous interviews
                </p>
              </div>
            </div>
          )}

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

          {/* Interview Recordings */}
          {recordings.length > 0 && (
            <div className="mb-8">
              <h2 className="text-xl font-bold text-gray-900 mb-4 flex items-center gap-2">
                <div className="w-2 h-2 bg-green-500 rounded-full"></div>
                Interview Recordings
              </h2>
              <div className="space-y-3">
                {recordings.map(recording => (
                  <div key={recording.id} className="border border-gray-200 rounded-lg p-4 bg-gray-50">
                    <div className="flex items-center justify-between mb-3">
                      <div>
                        <h3 className="font-medium text-gray-900">
                          Interview Recording
                        </h3>
                        <div className="text-sm text-gray-600 mt-1">
                          Duration: {recording.durationSeconds ? `${Math.floor(recording.durationSeconds / 60)}:${(recording.durationSeconds % 60).toString().padStart(2, '0')}` : 'Unknown'} •
                          Size: {recording.fileSize ? `${(recording.fileSize / (1024 * 1024)).toFixed(1)} MB` : 'Unknown'} •
                          Recorded: {new Date(recording.createdAt).toLocaleString()}
                        </div>
                      </div>
                      <div className="flex gap-2">
                        <button
                          onClick={() => playRecording(recording)}
                          className={`px-4 py-2 rounded-lg text-sm font-medium flex items-center gap-2 ${
                            playingRecording === recording.id
                              ? 'bg-red-600 text-white hover:bg-red-700'
                              : 'bg-blue-600 text-white hover:bg-blue-700'
                          }`}
                        >
                          {playingRecording === recording.id ? (
                            <>
                              <Square size={14} />
                              Stop
                            </>
                          ) : (
                            <>
                              <Play size={14} />
                              Play
                            </>
                          )}
                        </button>
                        <button
                          onClick={() => downloadRecording(recording)}
                          className="px-4 py-2 bg-gray-600 text-white rounded-lg hover:bg-gray-700 text-sm font-medium"
                        >
                          Download
                        </button>
                      </div>
                    </div>
                    {playingRecording === recording.id && (
                      <div className="text-sm text-green-600 font-medium">
                        Playing recording...
                      </div>
                    )}
                  </div>
                ))}
              </div>
            </div>
          )}

          {report.conversationHistory && report.conversationHistory.length > 0 && (
            <div data-tour="conversation-history">
              <h2 className="text-xl font-bold text-gray-900 mb-4">Conversation History</h2>
              <div className="space-y-4">
                {report.conversationHistory.map((qa, idx) => (
                  <div key={idx} className="border border-gray-200 rounded-lg p-4">
                    <div className="mb-3">
                      <div className="flex items-center justify-between mb-2">
                        <div className="flex items-center gap-2">
                          <FileText size={16} className="text-purple-600" />
                          <span className="text-sm font-semibold text-gray-700">Question {idx + 1}</span>
                        </div>
                        {qa.score && (
                          <div className="flex items-center gap-2">
                            <span className={`px-2 py-1 rounded text-xs font-medium ${
                              qa.score >= 80 ? 'bg-green-100 text-green-800' :
                              qa.score >= 60 ? 'bg-yellow-100 text-yellow-800' :
                              'bg-red-100 text-red-800'
                            }`}>
                              Score: {qa.score.toFixed(1)}
                            </span>
                            {qa.rubricLevel && (
                              <span className="text-xs text-gray-500 capitalize">
                                {qa.rubricLevel}
                              </span>
                            )}
                          </div>
                        )}
                      </div>
                      <p className="text-gray-800">{qa.questionText}</p>
                    </div>
                    <div className="mb-3">
                      <p className="text-sm font-semibold text-gray-700 mb-2">Answer:</p>
                      <p className="text-gray-600">{qa.answerText}</p>
                    </div>

                    {/* Evaluation Details */}
                    {(qa.strengths?.length > 0 || qa.improvements?.length > 0 || qa.followUpQuestions?.length > 0) && (
                      <div className="border-t border-gray-100 pt-3 mt-3">
                        {qa.strengths?.length > 0 && (
                          <div className="mb-2">
                            <p className="text-xs font-semibold text-green-700 mb-1">Strengths:</p>
                            <ul className="text-xs text-green-600 list-disc list-inside">
                              {qa.strengths.map((strength, i) => (
                                <li key={i}>{strength}</li>
                              ))}
                            </ul>
                          </div>
                        )}

                        {qa.improvements?.length > 0 && (
                          <div className="mb-2">
                            <p className="text-xs font-semibold text-orange-700 mb-1">Areas for Improvement:</p>
                            <ul className="text-xs text-orange-600 list-disc list-inside">
                              {qa.improvements.map((improvement, i) => (
                                <li key={i}>{improvement}</li>
                              ))}
                            </ul>
                          </div>
                        )}

                        {qa.followUpQuestions?.length > 0 && (
                          <div>
                            <p className="text-xs font-semibold text-blue-700 mb-1">Suggested Follow-up Questions:</p>
                            <ul className="text-xs text-blue-600 list-disc list-inside">
                              {qa.followUpQuestions.map((question, i) => (
                                <li key={i}>{question}</li>
                              ))}
                            </ul>
                          </div>
                        )}
                      </div>
                    )}

                    {/* Detailed Scores */}
                    {qa.detailedScores && (
                      <div className="border-t border-gray-100 pt-3 mt-3">
                        <p className="text-xs font-semibold text-gray-700 mb-2">Detailed Scores:</p>
                        <div className="grid grid-cols-2 gap-2 text-xs">
                          {Object.entries(qa.detailedScores).map(([skill, score]) => (
                            <div key={skill} className="flex justify-between">
                              <span className="text-gray-600 capitalize">
                                {skill.replace(/([A-Z])/g, ' $1').trim()}:
                              </span>
                              <span className={`font-medium ${
                                score >= 8 ? 'text-green-600' :
                                score >= 6 ? 'text-yellow-600' :
                                'text-red-600'
                              }`}>
                                {score}/10
                              </span>
                            </div>
                          ))}
                        </div>
                      </div>
                    )}
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

