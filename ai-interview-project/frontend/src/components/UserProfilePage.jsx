import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { User, CreditCard, TrendingUp, ArrowLeft } from 'lucide-react';
import LoadingSpinner from './common/LoadingSpinner';
import { useToast } from './common/useToast';
import ToastContainer from './common/ToastContainer';

const UserProfilePage = () => {
  const navigate = useNavigate();
  const [profile, setProfile] = useState(null);
  const [loading, setLoading] = useState(true);
  const [editing, setEditing] = useState(false);
  const [username, setUsername] = useState('');
  const { toasts, removeToast, success, error } = useToast();

  useEffect(() => {
    loadProfile();
  }, []);

  const loadProfile = async () => {
    try {
      setLoading(true);
      const accessToken = localStorage.getItem('accessToken');
      const headers = {
        'Content-Type': 'application/json',
      };
      if (accessToken) {
        headers['Authorization'] = `Bearer ${accessToken}`;
      }

      const response = await fetch('http://localhost:8080/api/user/profile', { headers });
      if (response.ok) {
        const data = await response.json();
        setProfile(data);
        setUsername(data.username);
      } else {
        error('Failed to load profile');
      }
    } catch (err) {
      error('Error loading profile');
    } finally {
      setLoading(false);
    }
  };

  const handleUpdate = async () => {
    try {
      const accessToken = localStorage.getItem('accessToken');
      const headers = {
        'Content-Type': 'application/json',
      };
      if (accessToken) {
        headers['Authorization'] = `Bearer ${accessToken}`;
      }

      const response = await fetch('http://localhost:8080/api/user/profile', {
        method: 'PUT',
        headers,
        body: JSON.stringify({ username })
      });

      if (response.ok) {
        success('Profile updated successfully');
        setEditing(false);
        loadProfile();
      } else {
        error('Failed to update profile');
      }
    } catch (err) {
      error('Error updating profile');
    }
  };

  if (loading) {
    return (
      <div className="p-8 ml-64 flex justify-center py-12">
        <LoadingSpinner size="lg" />
      </div>
    );
  }

  if (!profile) {
    return (
      <div className="p-8 ml-64">
        <p className="text-gray-600">Failed to load profile</p>
      </div>
    );
  }

  return (
    <div className="p-8 ml-64">
      <ToastContainer toasts={toasts} removeToast={removeToast} />
      
      <div className="mb-8">
        <button
          onClick={() => navigate('/')}
          className="flex items-center gap-2 text-gray-600 hover:text-gray-900 mb-4"
        >
          <ArrowLeft size={20} />
          Back to Dashboard
        </button>
      </div>
      
      <h2 className="text-2xl font-bold text-gray-800 mb-8">User Profile</h2>

      <div className="max-w-2xl space-y-6">
        <div className="bg-white rounded-lg shadow p-6">
          <div className="flex items-center gap-4 mb-6">
            <div className="w-16 h-16 bg-purple-600 rounded-full flex items-center justify-center text-white text-2xl font-bold">
              <User size={32} />
            </div>
            <div>
              <h3 className="text-xl font-bold text-gray-900">Profile Information</h3>
              <p className="text-gray-600">Manage your account settings</p>
            </div>
          </div>

          <div className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">Username</label>
              {editing ? (
                <input
                  type="text"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  className="w-full px-4 py-2 border border-gray-300 rounded-lg"
                />
              ) : (
                <p className="text-gray-900">{profile.username}</p>
              )}
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">User ID</label>
              <p className="text-gray-900">{profile.id}</p>
            </div>

            {editing ? (
              <div className="flex gap-3">
                <button
                  onClick={handleUpdate}
                  className="px-4 py-2 bg-purple-600 text-white rounded-lg hover:bg-purple-700"
                >
                  Save
                </button>
                <button
                  onClick={() => {
                    setEditing(false);
                    setUsername(profile.username);
                  }}
                  className="px-4 py-2 bg-gray-200 text-gray-700 rounded-lg hover:bg-gray-300"
                >
                  Cancel
                </button>
              </div>
            ) : (
              <button
                onClick={() => setEditing(true)}
                className="px-4 py-2 bg-purple-600 text-white rounded-lg hover:bg-purple-700"
              >
                Edit Profile
              </button>
            )}
          </div>
        </div>

        <div className="bg-white rounded-lg shadow p-6">
          <div className="flex items-center gap-3 mb-4">
            <TrendingUp size={24} className="text-yellow-600" />
            <h3 className="text-xl font-bold text-gray-900">Points & Subscription</h3>
          </div>

          <div className="space-y-4">
            <div className="bg-yellow-50 border border-yellow-200 rounded-lg p-4">
              <p className="text-sm text-yellow-800 mb-1">Points Balance</p>
              <p className="text-3xl font-bold text-yellow-900">
                {profile.points === 2147483647 || profile.points === 'unlimited' ? 'Unlimited' : (profile.points || 0).toLocaleString()}
              </p>
            </div>

            <div>
              <p className="text-sm font-medium text-gray-700 mb-2">Subscription Status</p>
              <div className="flex items-center gap-2">
                {profile.hasActiveSubscription ? (
                  <span className="px-3 py-1 bg-green-100 text-green-700 rounded-full text-sm font-medium">
                    Active (Pro)
                  </span>
                ) : profile.isInTrial ? (
                  <span className="px-3 py-1 bg-blue-100 text-blue-700 rounded-full text-sm font-medium">
                    Trial Period
                  </span>
                ) : (
                  <span className="px-3 py-1 bg-gray-100 text-gray-700 rounded-full text-sm font-medium">
                    Free Plan
                  </span>
                )}
              </div>
            </div>

            {profile.subscription && (
              <div className="mt-4 p-4 bg-gray-50 rounded-lg">
                <p className="text-sm text-gray-600">
                  Started: {new Date(profile.subscription.startDate).toLocaleDateString()}
                </p>
                {profile.subscription.endDate && (
                  <p className="text-sm text-gray-600">
                    Ends: {new Date(profile.subscription.endDate).toLocaleDateString()}
                  </p>
                )}
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

export default UserProfilePage;

