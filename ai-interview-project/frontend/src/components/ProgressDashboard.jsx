import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { TrendingUp, Target, Calendar, BarChart3, CheckCircle, ArrowLeft } from 'lucide-react';
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
  RadarChart,
  PolarGrid,
  PolarAngleAxis,
  PolarRadiusAxis,
  Radar,
  BarChart,
  Bar
} from 'recharts';
import LoadingSpinner from './common/LoadingSpinner';
import { InterviewCardSkeleton } from './common/LoadingSkeleton';
import { useToast } from './common/useToast';
import ToastContainer from './common/ToastContainer';

const ProgressDashboard = ({ onBack }) => {
  const navigate = useNavigate();
  const [interviews, setInterviews] = useState([]);
  const [selectedInterviews, setSelectedInterviews] = useState([]);
  const [comparisonData, setComparisonData] = useState(null);
  const [loading, setLoading] = useState(true);
  const { toasts, removeToast, error: showError } = useToast();

  const handleBack = () => {
    if (onBack) {
      onBack();
    } else {
      navigate('/');
    }
  };

  useEffect(() => {
    loadInterviews();
  }, []);

  useEffect(() => {
    if (selectedInterviews.length >= 2) {
      loadComparisonData();
    } else {
      setComparisonData(null);
    }
  }, [selectedInterviews]);

  const loadInterviews = async () => {
    try {
      setLoading(true);
      const accessToken = localStorage.getItem('accessToken');
      const headers = {
        'Content-Type': 'application/json',
      };
      if (accessToken) {
        headers['Authorization'] = `Bearer ${accessToken}`;
      }

      const response = await fetch('http://localhost:8080/api/interviews', { headers });
      if (response.ok) {
        const data = await response.json();
        // Filter only completed interviews for comparison
        const completedInterviews = data.filter(i => i.status === 'Completed');
        setInterviews(completedInterviews);
      } else {
        showError('Failed to load interviews');
      }
    } catch (err) {
      console.error('Error loading interviews:', err);
      showError('Error loading interviews');
    } finally {
      setLoading(false);
    }
  };

  const loadComparisonData = async () => {
    try {
      const accessToken = localStorage.getItem('accessToken');
      const headers = {
        'Content-Type': 'application/json',
      };
      if (accessToken) {
        headers['Authorization'] = `Bearer ${accessToken}`;
      }

      // Load detailed reports for selected interviews
      const reportsPromises = selectedInterviews.map(async (interviewId) => {
        const response = await fetch(`http://localhost:8080/api/interviews/${interviewId}/report`, { headers });
        if (response.ok) {
          const report = await response.json();
          return { interviewId, report };
        }
        return null;
      });

      const reports = await Promise.all(reportsPromises);
      const validReports = reports.filter(r => r !== null);

      if (validReports.length >= 2) {
        const comparison = generateComparisonData(validReports);
        setComparisonData(comparison);
      }
    } catch (err) {
      console.error('Error loading comparison data:', err);
      showError('Error loading comparison data');
    }
  };

  const generateComparisonData = (reports) => {
    // Sort reports by date
    const sortedReports = reports.sort((a, b) =>
      new Date(a.report.date || a.report.createdAt) - new Date(b.report.date || b.report.createdAt)
    );

    // Generate trend data
    const trendData = sortedReports.map((item, index) => {
      const avgScore = calculateAverageScore(item.report.conversationHistory || []);
      const interview = interviews.find(i => i.id === item.interviewId);
      return {
        interview: `Interview ${index + 1}`,
        date: new Date(item.report.date || item.report.createdAt).toLocaleDateString(),
        title: item.report.title || interview?.title || `Interview ${index + 1}`,
        score: avgScore,
        questionCount: item.report.conversationHistory?.length || 0,
        duration: interview?.durationSeconds ? Math.round(interview.durationSeconds / 60) : 0
      };
    });

    // Generate skill improvement data
    const skillProgression = generateSkillProgression(sortedReports);

    // Calculate overall improvements
    const improvements = calculateImprovements(trendData);

    return {
      trendData,
      skillProgression,
      improvements
    };
  };

  const calculateAverageScore = (conversationHistory) => {
    if (!conversationHistory || conversationHistory.length === 0) return 0;
    const scores = conversationHistory.map(qa => qa.score).filter(score => score != null);
    return scores.length > 0 ? scores.reduce((sum, score) => sum + score, 0) / scores.length : 0;
  };

  const generateSkillProgression = (reports) => {
    const skills = ['technicalAccuracy', 'depth', 'experience', 'communication'];
    const skillData = {};

    reports.forEach((item, index) => {
      const history = item.report.conversationHistory || [];
      const interview = interviews.find(i => i.id === item.interviewId);
      const interviewName = `Interview ${index + 1}`;

      skills.forEach(skill => {
        if (!skillData[skill]) skillData[skill] = [];

        // Calculate average skill score across all questions in this interview
        let totalScore = 0;
        let count = 0;

        history.forEach(qa => {
          if (qa.detailedScores && qa.detailedScores[skill] != null) {
            totalScore += qa.detailedScores[skill];
            count++;
          }
        });

        const avgScore = count > 0 ? totalScore / count : 0;
        skillData[skill].push({
          interview: interviewName,
          score: Math.round(avgScore),
          date: new Date(item.report.date || item.report.createdAt).toLocaleDateString()
        });
      });
    });

    return skillData;
  };

  const calculateImprovements = (trendData) => {
    if (trendData.length < 2) return {};

    const firstInterview = trendData[0];
    const lastInterview = trendData[trendData.length - 1];

    const scoreImprovement = lastInterview.score - firstInterview.score;
    const questionImprovement = lastInterview.questionCount - firstInterview.questionCount;

    return {
      scoreImprovement: Math.round(scoreImprovement * 100) / 100,
      questionImprovement,
      totalInterviews: trendData.length,
      averageScore: Math.round(trendData.reduce((sum, item) => sum + item.score, 0) / trendData.length * 100) / 100
    };
  };

  const toggleInterviewSelection = (interviewId) => {
    setSelectedInterviews(prev => {
      if (prev.includes(interviewId)) {
        return prev.filter(id => id !== interviewId);
      } else if (prev.length < 5) { // Limit to 5 interviews for comparison
        return [...prev, interviewId];
      }
      return prev;
    });
  };

  const COLORS = ['#6366f1', '#8b5cf6', '#06b6d4', '#10b981', '#f59e0b'];

  if (loading) {
    return (
      <div className="p-8 ml-64">
        <InterviewCardSkeleton count={6} />
      </div>
    );
  }

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
        <h2 className="text-2xl font-bold text-gray-800 mb-2">My Progress</h2>
        <p className="text-gray-600">Compare your interview performance across multiple sessions</p>
      </div>

      {/* Interview Selection */}
      <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-6 mb-8">
        <h3 className="text-lg font-semibold text-gray-900 mb-4">Select Interviews to Compare</h3>
        <p className="text-gray-600 text-sm mb-4">
          Choose 2-5 completed interviews to analyze your progress and improvement trends.
        </p>

        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {interviews.map(interview => (
            <div
              key={interview.id}
              className={`border-2 rounded-lg p-4 cursor-pointer transition-all ${
                selectedInterviews.includes(interview.id)
                  ? 'border-purple-600 bg-purple-50'
                  : 'border-gray-200 hover:border-gray-300'
              }`}
              onClick={() => toggleInterviewSelection(interview.id)}
            >
              <div className="flex items-start justify-between mb-2">
                <h4 className="font-medium text-gray-900 text-sm">{interview.title}</h4>
                {selectedInterviews.includes(interview.id) && (
                  <CheckCircle className="text-purple-600" size={20} />
                )}
              </div>
              <div className="text-xs text-gray-600 space-y-1">
                <p>{new Date(interview.createdAt).toLocaleDateString()}</p>
                <p className="capitalize">{interview.status}</p>
              </div>
            </div>
          ))}
        </div>

        {selectedInterviews.length > 0 && (
          <div className="mt-4 p-3 bg-blue-50 border border-blue-200 rounded-lg">
            <p className="text-sm text-blue-800">
              Selected {selectedInterviews.length} interview{selectedInterviews.length > 1 ? 's' : ''} for comparison
              {selectedInterviews.length < 2 && ' (select at least 2 to see comparison)'}
            </p>
          </div>
        )}
      </div>

      {/* Comparison Results */}
      {comparisonData && selectedInterviews.length >= 2 && (
        <>
          {/* Improvement Summary */}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-8">
            <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-6">
              <div className="flex items-center gap-3 mb-2">
                <TrendingUp className="text-green-600" size={24} />
                <span className="text-sm font-medium text-gray-600">Score Improvement</span>
              </div>
              <div className={`text-3xl font-bold ${
                comparisonData.improvements.scoreImprovement >= 0 ? 'text-green-600' : 'text-red-600'
              }`}>
                {comparisonData.improvements.scoreImprovement >= 0 ? '+' : ''}
                {comparisonData.improvements.scoreImprovement}%
              </div>
              <p className="text-xs text-gray-600 mt-1">Overall performance change</p>
            </div>

            <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-6">
              <div className="flex items-center gap-3 mb-2">
                <Target className="text-blue-600" size={24} />
                <span className="text-sm font-medium text-gray-600">Average Score</span>
              </div>
              <div className="text-3xl font-bold text-blue-600">
                {comparisonData.improvements.averageScore}%
              </div>
              <p className="text-xs text-gray-600 mt-1">Across all selected interviews</p>
            </div>

            <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-6">
              <div className="flex items-center gap-3 mb-2">
                <Calendar className="text-purple-600" size={24} />
                <span className="text-sm font-medium text-gray-600">Interviews Compared</span>
              </div>
              <div className="text-3xl font-bold text-purple-600">
                {comparisonData.improvements.totalInterviews}
              </div>
              <p className="text-xs text-gray-600 mt-1">Total sessions analyzed</p>
            </div>
          </div>

          {/* Performance Trend */}
          <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-6 mb-8">
            <h3 className="text-lg font-semibold text-gray-900 mb-4 flex items-center gap-2">
              <BarChart3 size={20} className="text-gray-600" />
              Performance Trend
            </h3>
            <ResponsiveContainer width="100%" height={300}>
              <LineChart data={comparisonData.trendData}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="interview" />
                <YAxis domain={[0, 100]} />
                <Tooltip
                  formatter={(value, name) => [value + '%', 'Score']}
                  labelFormatter={(label) => {
                    const item = comparisonData.trendData.find(d => d.interview === label);
                    return item ? `${item.title} (${item.date})` : label;
                  }}
                />
                <Legend />
                <Line
                  type="monotone"
                  dataKey="score"
                  stroke="#6366f1"
                  strokeWidth={3}
                  name="Average Score"
                />
              </LineChart>
            </ResponsiveContainer>
          </div>

          {/* Skill Progression */}
          {Object.keys(comparisonData.skillProgression).length > 0 && (
            <div className="grid md:grid-cols-2 gap-6">
              {Object.entries(comparisonData.skillProgression).map(([skill, data], index) => (
                <div key={skill} className="bg-white rounded-xl shadow-sm border border-gray-100 p-6">
                  <h4 className="text-md font-semibold text-gray-900 mb-4 capitalize">
                    {skill.replace(/([A-Z])/g, ' $1').trim()} Progress
                  </h4>
                  <ResponsiveContainer width="100%" height={250}>
                    <BarChart data={data}>
                      <CartesianGrid strokeDasharray="3 3" />
                      <XAxis dataKey="interview" />
                      <YAxis domain={[0, 10]} />
                      <Tooltip />
                      <Bar dataKey="score" fill={COLORS[index % COLORS.length]} />
                    </BarChart>
                  </ResponsiveContainer>
                </div>
              ))}
            </div>
          )}
        </>
      )}

      {selectedInterviews.length < 2 && interviews.length >= 2 && (
        <div className="bg-gray-50 border border-gray-200 rounded-lg p-8 text-center">
          <BarChart3 size={48} className="mx-auto text-gray-400 mb-4" />
          <h3 className="text-lg font-semibold text-gray-900 mb-2">Select More Interviews</h3>
          <p className="text-gray-600">
            Choose at least 2 completed interviews to see your progress comparison and improvement trends.
          </p>
        </div>
      )}

      {interviews.length < 2 && (
        <div className="bg-gray-50 border border-gray-200 rounded-lg p-8 text-center">
          <Target size={48} className="mx-auto text-gray-400 mb-4" />
          <h3 className="text-lg font-semibold text-gray-900 mb-2">Not Enough Interviews</h3>
          <p className="text-gray-600">
            Complete at least 2 interviews to start tracking your progress and improvement.
          </p>
        </div>
      )}
    </div>
  );
};

export default ProgressDashboard;
