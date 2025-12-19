import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Target, TrendingUp, Award, BookOpen, Lightbulb, ArrowLeft, Star } from 'lucide-react';
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
  Bar,
  PieChart,
  Pie,
  Cell
} from 'recharts';
import LoadingSpinner from './common/LoadingSpinner';
import { SkillProgressSkeleton } from './common/LoadingSkeleton';
import { useToast } from './common/useToast';
import ToastContainer from './common/ToastContainer';

const SkillProgressPage = ({ onBack }) => {
  const navigate = useNavigate();
  const [skillData, setSkillData] = useState(null);
  const [recommendations, setRecommendations] = useState([]);
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
    loadSkillProgress();
  }, []);

  const loadSkillProgress = async () => {
    try {
      setLoading(true);
      const accessToken = localStorage.getItem('accessToken');
      const headers = {
        'Content-Type': 'application/json',
      };
      if (accessToken) {
        headers['Authorization'] = `Bearer ${accessToken}`;
      }

      // Load skill tracking data
      const response = await fetch('http://localhost:8080/api/skills/progress', { headers });
      if (response.ok) {
        const data = await response.json();
        setSkillData(data);

        // Generate recommendations based on skill data
        const recs = generateRecommendations(data);
        setRecommendations(recs);
      } else {
        showError('Failed to load skill progress');
      }
    } catch (err) {
      console.error('Error loading skill progress:', err);
      showError('Error loading skill progress');
    } finally {
      setLoading(false);
    }
  };

  const generateRecommendations = (data) => {
    const recommendations = [];

    if (data && data.skillAverages) {
      const skillAverages = data.skillAverages;

      // Find weakest skills
      const skillEntries = Object.entries(skillAverages);
      const weakestSkills = skillEntries
        .sort(([,a], [,b]) => a - b)
        .slice(0, 2);

      weakestSkills.forEach(([skill, score]) => {
        if (score < 6) { // Below average
          recommendations.push({
            type: 'skill_improvement',
            priority: 'high',
            title: `Improve ${formatSkillName(skill)}`,
            description: `Your ${formatSkillName(skill)} score is ${score}/10. Focus on this area to improve overall performance.`,
            action: `Practice ${skill}-related questions`,
            skill: skill
          });
        }
      });

      // Check for consistent improvement
      if (data.skillTrends) {
        Object.entries(data.skillTrends).forEach(([skill, trend]) => {
          if (trend > 0.5) { // Improving
            recommendations.push({
              type: 'positive_feedback',
              priority: 'medium',
              title: `${formatSkillName(skill)} is improving!`,
              description: `You've shown consistent improvement in ${formatSkillName(skill)}. Keep up the great work!`,
              action: 'Continue practicing',
              skill: skill
            });
          }
        });
      }

      // General recommendations based on interview count
      if (data.totalInterviews < 3) {
        recommendations.push({
          type: 'general',
          priority: 'medium',
          title: 'Complete more interviews',
          description: 'Practice makes perfect! Try completing 2-3 more interviews to get better insights.',
          action: 'Start a new interview',
          skill: null
        });
      }

      // Study recommendations
      const studyTopics = getStudyRecommendations(skillAverages);
      studyTopics.forEach(topic => {
        recommendations.push({
          type: 'study',
          priority: 'low',
          title: `Study: ${topic.title}`,
          description: topic.description,
          action: topic.action,
          skill: topic.skill
        });
      });
    }

    return recommendations.sort((a, b) => {
      const priorityOrder = { high: 3, medium: 2, low: 1 };
      return priorityOrder[b.priority] - priorityOrder[a.priority];
    });
  };

  const getStudyRecommendations = (skillAverages) => {
    const recommendations = [];

    if (skillAverages.technicalAccuracy < 7) {
      recommendations.push({
        title: 'Technical Fundamentals',
        description: 'Review core technical concepts and algorithms',
        action: 'Study data structures and algorithms',
        skill: 'technicalAccuracy'
      });
    }

    if (skillAverages.depth < 7) {
      recommendations.push({
        title: 'Deep Technical Knowledge',
        description: 'Explore advanced topics in your tech stack',
        action: 'Dive deeper into frameworks and tools',
        skill: 'depth'
      });
    }

    if (skillAverages.experience < 7) {
      recommendations.push({
        title: 'Project Experience',
        description: 'Document and articulate your project experiences better',
        action: 'Prepare detailed project stories',
        skill: 'experience'
      });
    }

    if (skillAverages.communication < 7) {
      recommendations.push({
        title: 'Communication Skills',
        description: 'Practice clear and structured responses',
        action: 'Use STAR method for answers',
        skill: 'communication'
      });
    }

    return recommendations;
  };

  const formatSkillName = (skill) => {
    return skill.replace(/([A-Z])/g, ' $1').replace(/^./, str => str.toUpperCase());
  };

  const getSkillColor = (skill) => {
    const colors = {
      technicalAccuracy: '#6366f1',
      depth: '#8b5cf6',
      experience: '#06b6d4',
      communication: '#10b981'
    };
    return colors[skill] || '#6b7280';
  };

  const getPriorityColor = (priority) => {
    switch (priority) {
      case 'high': return 'text-red-600 bg-red-50 border-red-200';
      case 'medium': return 'text-yellow-600 bg-yellow-50 border-yellow-200';
      case 'low': return 'text-blue-600 bg-blue-50 border-blue-200';
      default: return 'text-gray-600 bg-gray-50 border-gray-200';
    }
  };

  const getPriorityIcon = (priority) => {
    switch (priority) {
      case 'high': return <Star size={16} className="text-red-600" />;
      case 'medium': return <TrendingUp size={16} className="text-yellow-600" />;
      case 'low': return <BookOpen size={16} className="text-blue-600" />;
      default: return <Lightbulb size={16} className="text-gray-600" />;
    }
  };

  if (loading) {
    return (
      <div className="p-8 ml-64">
        <SkillProgressSkeleton />
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
        <h2 className="text-2xl font-bold text-gray-800 mb-2">Skill Progress</h2>
        <p className="text-gray-600">Track your improvement across different skills and get personalized recommendations</p>
      </div>

      {skillData ? (
        <>
          {/* Skill Overview Cards */}
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-8">
            {skillData.skillAverages && Object.entries(skillData.skillAverages).map(([skill, score]) => (
              <div key={skill} className="bg-white rounded-xl shadow-sm border border-gray-100 p-6">
                <div className="flex items-center justify-between mb-4">
                  <h3 className="text-sm font-semibold text-gray-600 capitalize">
                    {formatSkillName(skill)}
                  </h3>
                  <div className="w-3 h-3 rounded-full" style={{ backgroundColor: getSkillColor(skill) }} />
                </div>
                <div className="text-3xl font-bold text-gray-900 mb-2">
                  {typeof score === 'number' ? score.toFixed(1) : score}/10
                </div>
                <div className="w-full bg-gray-200 rounded-full h-2">
                  <div
                    className="h-2 rounded-full transition-all duration-300"
                    style={{
                      width: `${Math.min((score / 10) * 100, 100)}%`,
                      backgroundColor: getSkillColor(skill)
                    }}
                  />
                </div>
              </div>
            ))}
          </div>

          {/* Skill Progress Charts */}
          <div className="grid md:grid-cols-2 gap-6 mb-8">
            {/* Current Skills Radar */}
            <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-6">
              <h3 className="text-lg font-semibold text-gray-900 mb-4">Current Skill Level</h3>
              <ResponsiveContainer width="100%" height={300}>
                <RadarChart
                  data={skillData.skillAverages ? Object.entries(skillData.skillAverages).map(([skill, score]) => ({
                    skill: formatSkillName(skill),
                    value: score
                  })) : []}
                >
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

            {/* Skill Distribution */}
            <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-6">
              <h3 className="text-lg font-semibold text-gray-900 mb-4">Skill Distribution</h3>
              <ResponsiveContainer width="100%" height={300}>
                <PieChart>
                  <Pie
                    data={skillData.skillAverages ? Object.entries(skillData.skillAverages).map(([skill, score]) => ({
                      name: formatSkillName(skill),
                      value: score
                    })) : []}
                    cx="50%"
                    cy="50%"
                    innerRadius={40}
                    outerRadius={80}
                    paddingAngle={5}
                    dataKey="value"
                  >
                    {Object.keys(skillData.skillAverages || {}).map((skill, index) => (
                      <Cell key={`cell-${index}`} fill={getSkillColor(skill)} />
                    ))}
                  </Pie>
                  <Tooltip />
                </PieChart>
              </ResponsiveContainer>
            </div>
          </div>

          {/* Skill Trends Over Time */}
          {skillData.skillHistory && skillData.skillHistory.length > 0 && (
            <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-6 mb-8">
              <h3 className="text-lg font-semibold text-gray-900 mb-4">Skill Progress Over Time</h3>
              <ResponsiveContainer width="100%" height={300}>
                <LineChart data={skillData.skillHistory}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis dataKey="date" />
                  <YAxis domain={[0, 10]} />
                  <Tooltip />
                  <Legend />
                  {Object.keys(skillData.skillAverages || {}).map((skill, index) => (
                    <Line
                      key={skill}
                      type="monotone"
                      dataKey={`skills.${skill}`}
                      stroke={getSkillColor(skill)}
                      strokeWidth={2}
                      name={formatSkillName(skill)}
                    />
                  ))}
                </LineChart>
              </ResponsiveContainer>
            </div>
          )}

          {/* Recommendations */}
          <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-6">
            <h3 className="text-lg font-semibold text-gray-900 mb-4 flex items-center gap-2">
              <Lightbulb size={20} className="text-yellow-600" />
              Personalized Recommendations
            </h3>

            {recommendations.length === 0 ? (
              <div className="text-center py-8">
                <Award size={48} className="mx-auto text-green-400 mb-4" />
                <p className="text-gray-600">Great job! You're performing well across all skills.</p>
                <p className="text-gray-500 text-sm mt-2">Keep practicing to maintain your excellent performance.</p>
              </div>
            ) : (
              <div className="space-y-4">
                {recommendations.map((rec, index) => (
                  <div
                    key={index}
                    className={`border rounded-lg p-4 ${getPriorityColor(rec.priority)}`}
                  >
                    <div className="flex items-start gap-3">
                      {getPriorityIcon(rec.priority)}
                      <div className="flex-1">
                        <h4 className="font-semibold text-gray-900 mb-1">{rec.title}</h4>
                        <p className="text-gray-700 text-sm mb-2">{rec.description}</p>
                        <div className="flex items-center gap-2">
                          <span className="text-xs font-medium px-2 py-1 bg-white bg-opacity-50 rounded">
                            {rec.action}
                          </span>
                          {rec.skill && (
                            <span className="text-xs text-gray-600">
                              Focus: {formatSkillName(rec.skill)}
                            </span>
                          )}
                        </div>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </>
      ) : (
        <div className="bg-gray-50 border border-gray-200 rounded-lg p-8 text-center">
          <Target size={48} className="mx-auto text-gray-400 mb-4" />
          <h3 className="text-lg font-semibold text-gray-900 mb-2">No Skill Data Available</h3>
          <p className="text-gray-600">
            Complete some interviews to start tracking your skill progress and get personalized recommendations.
          </p>
        </div>
      )}
    </div>
  );
};

export default SkillProgressPage;
