import React from 'react';
import { useNavigate } from 'react-router-dom';
import { Calendar, Code2, Languages, Eye, Trash2, FileText } from 'lucide-react';

const InterviewCard = ({ interview, onDelete }) => {
  const navigate = useNavigate();

  const handleClick = () => {
    if (interview.status === 'Completed') {
      navigate(`/report/${interview.id}`);
    } else {
      navigate(`/interview/${interview.id}`);
    }
  };

  const handleDeleteClick = (e) => {
    e.stopPropagation();
    onDelete?.(interview.id);
  };

  const getStatusColor = (status) => {
    switch (status) {
      case 'Completed':
        return 'bg-green-100 text-green-700';
      case 'In Progress':
        return 'bg-blue-100 text-blue-700';
      default:
        return 'bg-gray-100 text-gray-700';
    }
  };

  return (
    <div 
      className="bg-white p-6 rounded-xl shadow-sm border border-gray-100 hover:shadow-md transition-shadow cursor-pointer"
      onClick={handleClick}
    >
      <div className="mb-4 flex items-start justify-between">
        <h3 className="font-bold text-gray-800 text-lg mb-1 flex-1">{interview.title}</h3>
        {interview.status && (
          <span className={`text-xs px-2 py-1 rounded-full ${getStatusColor(interview.status)}`}>
            {interview.status}
          </span>
        )}
      </div>
      
      <div className="space-y-3 mb-6">
        <div className="flex items-start gap-3">
          <Languages size={16} className="text-gray-400 mt-1" />
          <div>
            <span className="text-xs text-gray-500 block">Language</span>
            <span className="text-sm text-gray-700 font-medium">{interview.language}</span>
          </div>
        </div>
        
        <div className="flex items-start gap-3">
          <Code2 size={16} className="text-gray-400 mt-1" />
          <div>
            <span className="text-xs text-gray-500 block">Tech Stack</span>
            <span className="text-sm text-gray-700 font-medium">{interview.techStack}</span>
          </div>
        </div>
      </div>

      <div className="pt-4 border-t border-gray-100 flex items-center justify-between">
        <div className="flex items-center gap-1 text-xs text-gray-500">
          <Calendar size={14} />
          {interview.date || (interview.createdAt ? new Date(interview.createdAt).toLocaleDateString() : 'N/A')}
        </div>
        <div className="flex gap-2" onClick={(e) => e.stopPropagation()}>
          {interview.status === 'Completed' && (
            <button
              onClick={() => navigate(`/report/${interview.id}`)}
              className="p-2 text-blue-600 hover:bg-blue-50 rounded-lg transition-colors"
              title="View Report"
            >
              <FileText size={16} />
            </button>
          )}
          <button
            onClick={handleDeleteClick}
            className="p-2 text-red-600 hover:bg-red-50 rounded-lg transition-colors"
            title="Delete"
          >
            <Trash2 size={16} />
          </button>
        </div>
      </div>
    </div>
  );
};

export default InterviewCard;

