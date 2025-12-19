import React from 'react';
import { FileText, Inbox, Search } from 'lucide-react';

const EmptyState = ({ 
  icon = 'inbox', 
  title = 'No data available', 
  message = 'There is no data to display at the moment.',
  actionLabel,
  onAction
}) => {
  const icons = {
    inbox: <Inbox size={48} className="text-gray-400" />,
    file: <FileText size={48} className="text-gray-400" />,
    search: <Search size={48} className="text-gray-400" />
  };

  return (
    <div className="flex flex-col items-center justify-center py-12 px-4">
      <div className="mb-4">
        {icons[icon]}
      </div>
      <h3 className="text-lg font-semibold text-gray-900 mb-2">{title}</h3>
      <p className="text-gray-600 text-center mb-6 max-w-sm">{message}</p>
      {actionLabel && onAction && (
        <button
          onClick={onAction}
          className="px-4 py-2 bg-purple-600 text-white rounded-lg hover:bg-purple-700 transition-colors"
        >
          {actionLabel}
        </button>
      )}
    </div>
  );
};

export default EmptyState;

