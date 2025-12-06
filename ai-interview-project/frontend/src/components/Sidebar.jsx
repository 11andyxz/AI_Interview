import React, { useState, useEffect } from 'react';
import { MessageSquare, FileText, PenTool, Book, Plus, LayoutDashboard, User, LogOut } from 'lucide-react';
import { Link, useLocation, useNavigate } from 'react-router-dom';

const Sidebar = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const [currentUser, setCurrentUser] = useState(null);

  useEffect(() => {
    const userStr = localStorage.getItem('user');
    if (userStr) {
      setCurrentUser(JSON.parse(userStr));
    }
  }, []);

  const isActive = (path) => location.pathname === path;

  const handleNewInterview = () => {
    // For demo purposes, directly generate a random ID and navigate
    // In a real app, this would likely open a modal to select options first
    const mockId = Math.floor(Math.random() * 10000);
    navigate(`/interview/${mockId}`);
  };

  const handleLogout = () => {
    localStorage.removeItem('user');
    localStorage.removeItem('isAuthenticated');
    navigate('/login');
  };

  return (
    <div className="w-64 bg-white h-screen border-r border-gray-200 flex flex-col fixed left-0 top-0 z-10">
      {/* Logo Area */}
      <div className="p-6 flex items-center gap-2">
        <div className="w-8 h-8 bg-purple-600 rounded-lg flex items-center justify-center text-white font-bold">
          AI
        </div>
        <div>
          <h1 className="font-bold text-gray-800">Interview Master</h1>
          <p className="text-xs text-gray-500">Pass with confidence</p>
        </div>
      </div>

      {/* Create Button */}
      <div className="px-4 mb-6">
        <button 
          onClick={handleNewInterview}
          className="w-full bg-purple-600 hover:bg-purple-700 text-white py-2 px-4 rounded-lg flex items-center justify-center gap-2 font-medium transition-colors shadow-sm hover:shadow"
        >
          <Plus size={18} />
          New Interview
        </button>
      </div>

      {/* Navigation */}
      <nav className="flex-1 px-2 space-y-1">
        <Link to="/">
            <MenuItem icon={<LayoutDashboard size={20} />} label="My Interviews" active={isActive('/')} />
        </Link>
        <MenuItem icon={<PenTool size={20} />} label="My Notes" />
        <MenuItem icon={<MessageSquare size={20} />} label="Mock Interview" />
        <MenuItem icon={<FileText size={20} />} label="My Resume" />
        <MenuItem icon={<Book size={20} />} label="Knowledge Base" badge="New" />
      </nav>

      {/* User Profile at Bottom */}
      <div className="p-4 border-t border-gray-200">
        <div className="bg-yellow-50 p-3 rounded-lg mb-3 border border-yellow-100">
          <div className="text-xs text-yellow-800 font-medium mb-1">Points Balance</div>
          <div className="text-2xl font-bold text-yellow-900">1644</div>
          <div className="text-xs text-gray-500">Usage Rules</div>
        </div>
        
        <div className="flex items-center gap-3 mt-2">
          <div className="w-10 h-10 bg-gray-200 rounded-full flex items-center justify-center overflow-hidden">
            <User size={24} className="text-gray-500" />
          </div>
          <div className="flex-1 min-w-0">
            <p className="text-sm font-medium text-gray-900 truncate">
              {currentUser ? currentUser.username : 'User'}
            </p>
          </div>
        </div>
        
        <button
          onClick={handleLogout}
          className="mt-3 w-full flex items-center justify-center gap-2 px-4 py-2 text-sm text-gray-700 hover:bg-gray-100 rounded-lg transition-colors"
        >
          <LogOut size={16} />
          Logout
        </button>
      </div>
    </div>
  );
};

const MenuItem = ({ icon, label, active, badge }) => {
  return (
    <div className={`flex items-center px-4 py-3 text-sm font-medium rounded-lg group cursor-pointer transition-colors ${
      active ? 'bg-purple-50 text-purple-700' : 'text-gray-700 hover:bg-gray-50'
    }`}>
      <span className={`mr-3 ${active ? 'text-purple-700' : 'text-gray-400 group-hover:text-gray-500'}`}>
        {icon}
      </span>
      <span className="flex-1">{label}</span>
      {badge && (
        <span className="bg-purple-100 text-purple-600 py-0.5 px-2 rounded-full text-xs font-medium">
          {badge}
        </span>
      )}
    </div>
  );
};

export default Sidebar;
