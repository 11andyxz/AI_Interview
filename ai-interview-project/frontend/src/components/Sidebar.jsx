import React, { useState, useEffect } from 'react';
import { MessageSquare, FileText, PenTool, Book, Plus, LayoutDashboard, User, LogOut, CreditCard, TrendingUp, Target, Settings, BookOpen } from 'lucide-react';
import { Link, useLocation, useNavigate } from 'react-router-dom';

const Sidebar = ({ onNewInterview }) => {
  const location = useLocation();
  const navigate = useNavigate();
  const [currentUser, setCurrentUser] = useState(null);
  const [points, setPoints] = useState(0);
  const [isUnlimited, setIsUnlimited] = useState(false);
  const [loadingPoints, setLoadingPoints] = useState(true);

  useEffect(() => {
    const userStr = localStorage.getItem('user');
    if (userStr) {
      setCurrentUser(JSON.parse(userStr));
    }
    loadPoints();
  }, []);

  const loadPoints = async () => {
    try {
      setLoadingPoints(true);
      const accessToken = localStorage.getItem('accessToken');
      const headers = {
        'Content-Type': 'application/json',
      };
      if (accessToken) {
        headers['Authorization'] = `Bearer ${accessToken}`;
      }

      const response = await fetch('http://localhost:8080/api/user/points', { headers });
      if (response.ok) {
        const data = await response.json();
        setPoints(data.points);
        setIsUnlimited(data.isUnlimited || false);
      }
    } catch (err) {
      console.error('Error loading points:', err);
    } finally {
      setLoadingPoints(false);
    }
  };

  const isActive = (path) => location.pathname === path;

  const handleLogout = () => {
    localStorage.removeItem('user');
    localStorage.removeItem('isAuthenticated');
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    navigate('/login');
  };

  return (
    <div className="sidebar w-64 bg-white h-screen border-r border-gray-200 flex flex-col fixed left-0 top-0 z-10">
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
          onClick={onNewInterview}
          data-tour="new-interview"
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
        <Link to="/progress">
          <MenuItem icon={<TrendingUp size={20} />} label="My Progress" active={isActive('/progress')} />
        </Link>
        <Link to="/skills">
          <MenuItem icon={<Target size={20} />} label="Skill Tracking" active={isActive('/skills')} />
        </Link>
        <Link to="/settings">
          <MenuItem icon={<Settings size={20} />} label="Settings" active={isActive('/settings')} />
        </Link>
        <Link to="/question-sets">
          <MenuItem icon={<BookOpen size={20} />} label="Question Sets" active={isActive('/question-sets')} />
        </Link>
        <Link to="/notes">
          <MenuItem icon={<PenTool size={20} />} label="My Notes" active={isActive('/notes')} />
        </Link>
        <Link to="/mock-interview">
          <MenuItem icon={<MessageSquare size={20} />} label="Mock Interview" active={isActive('/mock-interview')} />
        </Link>
        <Link to="/resume">
          <MenuItem icon={<FileText size={20} />} label="My Resume" active={isActive('/resume')} />
        </Link>
        <Link to="/knowledge-base">
          <MenuItem icon={<Book size={20} />} label="Knowledge Base" active={isActive('/knowledge-base')} badge="New" />
        </Link>
        <Link to="/payment">
          <MenuItem icon={<CreditCard size={20} />} label="Subscription" active={isActive('/payment')} />
        </Link>
        <Link to="/profile">
          <MenuItem icon={<User size={20} />} label="Profile" active={isActive('/profile')} />
        </Link>
      </nav>

      {/* User Profile at Bottom */}
      <div className="p-4 border-t border-gray-200">
        <div className="bg-yellow-50 p-3 rounded-lg mb-3 border border-yellow-100">
          <div className="text-xs text-yellow-800 font-medium mb-1">Points Balance</div>
          {loadingPoints ? (
            <div className="text-2xl font-bold text-yellow-900">Loading...</div>
          ) : (
            <div className="text-2xl font-bold text-yellow-900">
              {isUnlimited ? 'Unlimited' : points.toLocaleString()}
            </div>
          )}
          <div className="text-xs text-gray-500">Usage Rules</div>
        </div>
        
        <Link to="/profile">
          <div className="flex items-center gap-3 mt-2 cursor-pointer hover:bg-gray-50 p-2 rounded-lg transition-colors">
            <div className="w-10 h-10 bg-gray-200 rounded-full flex items-center justify-center overflow-hidden">
              <User size={24} className="text-gray-500" />
            </div>
            <div className="flex-1 min-w-0">
              <p className="text-sm font-medium text-gray-900 truncate">
                {currentUser ? currentUser.username : 'User'}
              </p>
            </div>
          </div>
        </Link>
        
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

const MenuItem = ({ icon, label, active, badge, onClick }) => {
  return (
    <div 
      onClick={onClick}
      className={`flex items-center px-4 py-3 text-sm font-medium rounded-lg group cursor-pointer transition-colors ${
        active ? 'bg-purple-50 text-purple-700' : 'text-gray-700 hover:bg-gray-50'
      }`}
    >
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
