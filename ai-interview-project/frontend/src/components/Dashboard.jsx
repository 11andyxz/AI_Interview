import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import InterviewCard from './InterviewCard';
import LoadingSpinner from './common/LoadingSpinner';
import EmptyState from './common/EmptyState';
import { useToast } from './common/useToast';
import ToastContainer from './common/ToastContainer';
import ConfirmDialog from './common/ConfirmDialog';

const Dashboard = () => {
  const [interviews, setInterviews] = useState([]);
  const [loading, setLoading] = useState(true);
  const [filter, setFilter] = useState('all'); // all, In Progress, Completed
  const [sortBy, setSortBy] = useState('date'); // date, status
  const [deleteConfirm, setDeleteConfirm] = useState(null);
  const navigate = useNavigate();
  const { toasts, removeToast, success, error } = useToast();

  {/*useEffect(() => {
    // TO DO: Fetch data from Backend API
    // fetch('http://localhost:8080/api/interviews')
    //   .then(res => res.json())
    //   .then(data => setInterviews(data));
    
    // Mock Data for now to match screenshot
    const mockData = [
      { id: 1, title: "Internet / AI / Artificial Intelligence", language: "English", techStack: "JavaScript,Python,Java,Kotlin", date: "2025/11/25" },
      { id: 2, title: "Internet / AI / Artificial Intelligence", language: "English", techStack: "JavaScript,Python,Java,Kotlin", date: "2025/11/25" },
      { id: 3, title: "Internet / AI / Artificial Intelligence", language: "English", techStack: "JavaScript,Python,Java,Kotlin", date: "2025/11/24" },
      { id: 4, title: "Internet / AI / Artificial Intelligence", language: "English", techStack: "JavaScript,Python,Java,Kotlin", date: "2025/11/24" },
      { id: 5, title: "Internet / AI / Artificial Intelligence", language: "English", techStack: "JavaScript,Python,Java,Kotlin", date: "2025/11/23" },
      { id: 6, title: "Internet / AI / Artificial Intelligence", language: "English", techStack: "JavaScript,Python,Java,Kotlin", date: "2025/11/23" },
      { id: 7, title: "Internet / AI / Artificial Intelligence", language: "English", techStack: "JavaScript,Python,Java,Kotlin", date: "2025/06/14" },
      { id: 8, title: "Internet / AI / Artificial Intelligence", language: "English", techStack: "JavaScript,Python,Java,Kotlin", date: "2025/06/14" },
      { id: 9, title: "Internet / AI / Artificial Intelligence", language: "Chinese", techStack: "JavaScript,Python,Java,Kotlin", date: "2025/06/14" },
      { id: 10, title: "Internet / AI / Backend Development", language: "English", techStack: "JavaScript,Python,Java,Kotlin", date: "2025/05/08" },
      { id: 11, title: "Internet / AI / Backend Development", language: "English", techStack: "JavaScript,Python,Java,Kotlin", date: "2025/05/08" },
    ];
    // setInterviews(mockData);
  }, []);*/}
  useEffect(() => {
    loadInterviews();
  }, [filter, sortBy]);

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
      if (!response.ok) {
        if (response.status === 401) {
          error('Unauthorized: Please login again');
          navigate('/login');
          return;
        }
        throw new Error(`HTTP ${response.status}`);
      }
      
      const data = await response.json();
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
    } catch (err) {
      console.error("API error:", err);
      error('Failed to load interviews');
      setInterviews([]);
    } finally {
      setLoading(false);
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

      const response = await fetch(`http://localhost:8080/api/interviews/${id}`, {
        method: 'DELETE',
        headers
      });

      if (response.ok) {
        success('Interview deleted successfully');
        setDeleteConfirm(null);
        loadInterviews();
      } else {
        error('Failed to delete interview');
      }
    } catch (err) {
      error('Error deleting interview');
    }
  };

  return (
    <div className="p-8 ml-64">
      <ToastContainer toasts={toasts} removeToast={removeToast} />
      
      <div className="flex items-center justify-between mb-8">
        <h2 className="text-2xl font-bold text-gray-800">My Interviews</h2>
        <div className="flex gap-3">
          <select
            value={filter}
            onChange={(e) => setFilter(e.target.value)}
            className="px-4 py-2 border border-gray-300 rounded-lg text-sm"
          >
            <option value="all">All Status</option>
            <option value="In Progress">In Progress</option>
            <option value="Completed">Completed</option>
          </select>
          <select
            value={sortBy}
            onChange={(e) => setSortBy(e.target.value)}
            className="px-4 py-2 border border-gray-300 rounded-lg text-sm"
          >
            <option value="date">Sort by Date</option>
            <option value="status">Sort by Status</option>
          </select>
        </div>
      </div>

      {loading ? (
        <div className="flex justify-center py-12">
          <LoadingSpinner size="lg" />
        </div>
      ) : interviews.length === 0 ? (
        <EmptyState
          icon="inbox"
          title="No interviews yet"
          message="Click 'New Interview' to create your first interview"
        />
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-8 auto-rows-fr">
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
    </div>
  );
};

export default Dashboard;

