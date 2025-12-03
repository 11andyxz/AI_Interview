import React from 'react';
import { Calendar, Code2, Languages } from 'lucide-react';

const InterviewCard = ({ interview }) => {
  return (
    <div className="bg-white p-6 rounded-xl shadow-sm border border-gray-100 hover:shadow-md transition-shadow">
      <div className="mb-4">
        <h3 className="font-bold text-gray-800 text-lg mb-1">{interview.title}</h3>
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

      <div className="pt-4 border-t border-gray-100 flex items-center justify-between text-xs text-gray-500">
        <div className="flex items-center gap-1">
          <Calendar size={14} />
          {interview.date}
        </div>
      </div>
    </div>
  );
};

export default InterviewCard;

