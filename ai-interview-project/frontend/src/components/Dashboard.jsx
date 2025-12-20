import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import InterviewCard from './InterviewCard';
import LoadingSpinner from './common/LoadingSpinner';
import { DashboardSkeleton } from './common/LoadingSkeleton';
import ErrorRetry from './common/ErrorRetry';
import EmptyState from './common/EmptyState';
import { apiCall, buildUrl, getAuthHeaders } from '../utils/api';
import { useDashboardShortcuts } from '../hooks/useKeyboardShortcuts';
import KeyboardShortcutsHelp from './KeyboardShortcutsHelp';
import { useToast } from './common/useToast';
import ToastContainer from './common/ToastContainer';
import ConfirmDialog from './common/ConfirmDialog';
import {
  BarChart,
  Bar,
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  PieChart,
  Pie,
  Cell
} from 'recharts';
import { TrendingUp, Award, Clock, Target, Calendar, Activity } from 'lucide-react';

const Dashboard = () => {
  const [interviews, setInterviews] = useState([]);
  const [statistics, setStatistics] = useState(null);
  const [loading, setLoading] = useState(true);
  const [apiError, setApiError] = useState(null);
  const [filter, setFilter] = useState('all'); // all, In Progress, Completed
  const [sortBy, setSortBy] = useState('date'); // date, status
  const [deleteConfirm, setDeleteConfirm] = useState(null);
  const [showKeyboardHelp, setShowKeyboardHelp] = useState(false);
  const [searchInputRef, setSearchInputRef] = useState(null);
  const navigate = useNavigate();
  const { toasts, removeToast, success, error } = useToast();

  // Keyboard shortcuts handlers
  const handleFocusSearch = () => {
    if (searchInputRef) {
      searchInputRef.focus();
    }
  };

  const handleNewInterview = () => {
    // Could trigger new interview modal
    success('Use the "New Interview" button to create an interview');
  };

  const handleShowHelp = () => {
    setShowKeyboardHelp(true);
  };

  // Setup keyboard shortcuts
  useDashboardShortcuts({
    onSearch: handleFocusSearch,
    onNewInterview: handleNewInterview,
    onHelp: handleShowHelp
  });

  const loadInterviews = async () => {
    try {
      setLoading(true);
      setApiError(null);

      const data = await apiCall('/api/interviews', {
        timeout: 10000, // 10 second timeout
        retryOptions: {
          maxRetries: 2
        }
      });
      let filtered = data;
      
      // Apply filter
      if (filter !== 'all') {
        filtered = filtered.filter(i => i.status === filter);
      }
      
      // Apply sort
      filtered.sort((a, b) => {
        if (sortBy === 'date') {
          const dateA = new Date(a.createdAt || a.date || 0);
          const dateB = new Date(b.createdAt || b.date || 0);
          return dateB - dateA; // Newest first
        } else if (sortBy === 'status') {
          return (a.status || '').localeCompare(b.status || '');
        }
        return 0;
      });
      
      setInterviews(filtered);

      // Load user statistics
      try {
        const statsData = await apiCall('/api/user/statistics', {
          retry: false
        });
        setStatistics(statsData);
      } catch (statsErr) {
        console.error("Error loading statistics:", statsErr);
      }

    } catch (err) {
      console.error("API error:", err);
      setApiError(err);
      setInterviews([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadInterviews();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filter, sortBy]);

  const handleDelete = async (id) => {
    try {
      await apiCall(`/api/interviews/${id}`, {
        method: 'DELETE',
        retry: false
      });
      success('Interview deleted successfully');
      setDeleteConfirm(null);
      loadInterviews();
    } catch (err) {
      error('Error deleting interview');
    }
  };

  // Prepare chart data
  const getSkillChartData = () => {
    if (!statistics?.topSkills) return [];
    return Object.entries(statistics.topSkills).map(([skill, count]) => ({
      name: skill,
      value: count
    }));
  };

  const getFrequencyChartData = () => {
    if (!statistics?.interviewFrequency) return [];
    return Object.entries(statistics.interviewFrequency).map(([month, count]) => ({
      month,
      interviews: count
    }));
  };

  const COLORS = ['#6366f1', '#8b5cf6', '#06b6d4', '#10b981', '#f59e0b'];

  return (
    <div className="p-8 ml-64">
      <ToastContainer toasts={toasts} removeToast={removeToast} />

      <div className="mb-8">
        <h2 className="text-2xl font-bold text-gray-800 mb-6">Dashboard</h2>

        {/* Statistics Cards */}
        {statistics && (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-8">
            <div className="bg-white p-6 rounded-xl shadow-sm border border-gray-100">
              <div className="flex items-center gap-3 mb-2">
                <Target className="text-blue-600" size={24} />
                <span className="text-sm font-medium text-gray-600">Total Interviews</span>
              </div>
              <div className="text-3xl font-bold text-gray-900">{statistics.totalInterviews || 0}</div>
            </div>

            <div className="bg-white p-6 rounded-xl shadow-sm border border-gray-100">
              <div className="flex items-center gap-3 mb-2">
                <Award className="text-green-600" size={24} />
                <span className="text-sm font-medium text-gray-600">Completed</span>
              </div>
              <div className="text-3xl font-bold text-gray-900">{statistics.completedInterviews || 0}</div>
            </div>

            <div className="bg-white p-6 rounded-xl shadow-sm border border-gray-100">
              <div className="flex items-center gap-3 mb-2">
                <Clock className="text-yellow-600" size={24} />
                <span className="text-sm font-medium text-gray-600">Avg Duration</span>
              </div>
              <div className="text-3xl font-bold text-gray-900">
                {statistics.averageDuration ? `${statistics.averageDuration}min` : 'N/A'}
              </div>
            </div>

            <div className="bg-white p-6 rounded-xl shadow-sm border border-gray-100">
              <div className="flex items-center gap-3 mb-2">
                <Activity className="text-purple-600" size={24} />
                <span className="text-sm font-medium text-gray-600">Best Score</span>
              </div>
              <div className="text-3xl font-bold text-gray-900">{statistics.bestScore || 0}%</div>
            </div>
          </div>
        )}

        {/* Charts Row */}
        {statistics && (getSkillChartData().length > 0 || getFrequencyChartData().length > 0) && (
          <div className="grid md:grid-cols-2 gap-6 mb-8">
            {/* Skills Distribution */}
            {getSkillChartData().length > 0 && (
              <div className="bg-white p-6 rounded-xl shadow-sm border border-gray-100">
                <h3 className="text-lg font-semibold text-gray-900 mb-4">Skills Practiced</h3>
                <ResponsiveContainer width="100%" height={250}>
                  <PieChart>
                    <Pie
                      data={getSkillChartData()}
                      cx="50%"
                      cy="50%"
                      innerRadius={40}
                      outerRadius={80}
                      paddingAngle={5}
                      dataKey="value"
                    >
                      {getSkillChartData().map((entry, index) => (
                        <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                      ))}
                    </Pie>
                    <Tooltip />
                  </PieChart>
                </ResponsiveContainer>
              </div>
            )}

            {/* Interview Frequency */}
            {getFrequencyChartData().length > 0 && (
              <div className="bg-white p-6 rounded-xl shadow-sm border border-gray-100">
                <h3 className="text-lg font-semibold text-gray-900 mb-4">Interview Frequency</h3>
                <ResponsiveContainer width="100%" height={250}>
                  <BarChart data={getFrequencyChartData()}>
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis dataKey="month" />
                    <YAxis />
                    <Tooltip />
                    <Bar dataKey="interviews" fill="#6366f1" radius={[4, 4, 0, 0]} />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            )}
          </div>
        )}

        {/* Recent Activity */}
        {statistics?.recentActivity && statistics.recentActivity.length > 0 && (
          <div className="bg-white p-6 rounded-xl shadow-sm border border-gray-100 mb-8">
            <h3 className="text-lg font-semibold text-gray-900 mb-4 flex items-center gap-2">
              <Calendar size={20} className="text-gray-600" />
              Recent Activity
            </h3>
            <div className="space-y-3">
              {statistics.recentActivity.map((activity, index) => (
                <div key={index} className="flex items-center justify-between py-2 border-b border-gray-100 last:border-b-0">
                  <div>
                    <p className="font-medium text-gray-900">{activity.title}</p>
                    <p className="text-sm text-gray-600">
                      {new Date(activity.date).toLocaleDateString()}
                    </p>
                  </div>
                  <span className={`px-3 py-1 rounded-full text-xs font-medium ${
                    activity.status === 'Completed' ? 'bg-green-100 text-green-800' :
                    activity.status === 'In Progress' ? 'bg-blue-100 text-blue-800' :
                    'bg-gray-100 text-gray-800'
                  }`}>
                    {activity.status}
                  </span>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>

      <div className="flex items-center justify-between mb-8">
        <h2 className="text-2xl font-bold text-gray-800">My Interviews</h2>

        {/* Search and Filters */}
        <div className="flex flex-wrap gap-3 mb-4" data-tour="filter-section">
          <div className="relative">
            <input
              ref={(el) => setSearchInputRef(el)}
              type="text"
              placeholder="Search interviews..."
              data-testid="interview-search"
              className="pl-3 pr-4 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-purple-500 focus:border-transparent"
            />
          </div>
          <select
            value={filter}
            onChange={(e) => setFilter(e.target.value)}
            data-testid="interview-filter"
            className="px-4 py-2 border border-gray-300 rounded-lg text-sm"
          >
            <option value="all">All Status</option>
            <option value="In Progress">In Progress</option>
            <option value="Completed">Completed</option>
          </select>
          <select
            value={sortBy}
            onChange={(e) => setSortBy(e.target.value)}
            data-testid="interview-sort"
            className="px-4 py-2 border border-gray-300 rounded-lg text-sm"
          >
            <option value="date">Sort by Date</option>
            <option value="status">Sort by Status</option>
          </select>
        </div>
      </div>

      {loading ? (
        <DashboardSkeleton />
      ) : apiError ? (
        <ErrorRetry
          error={apiError}
          onRetry={loadInterviews}
          compact={false}
        />
      ) : interviews.length === 0 ? (
        <EmptyState
          icon="inbox"
          title="No interviews yet"
          message="Click 'New Interview' to create your first interview"
        />
      ) : (
        <div data-testid="interview-list" className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-8 auto-rows-fr">
          {interviews.map((interview) => (
            <InterviewCard
              key={interview.id}
              interview={interview}
              onDelete={(id) => setDeleteConfirm(id)}
            />
          ))}
        </div>
      )}

      <ConfirmDialog
        isOpen={!!deleteConfirm}
        onClose={() => setDeleteConfirm(null)}
        onConfirm={() => handleDelete(deleteConfirm)}
        title="Delete Interview"
        message="Are you sure you want to delete this interview? This action cannot be undone."
        confirmText="Delete"
        cancelText="Cancel"
        type="danger"
      />

      <KeyboardShortcutsHelp
        isOpen={showKeyboardHelp}
        onClose={() => setShowKeyboardHelp(false)}
      />
    </div>
  );
};

export default Dashboard;

