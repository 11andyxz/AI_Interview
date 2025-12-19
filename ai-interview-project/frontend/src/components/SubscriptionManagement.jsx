import React, { useState, useEffect } from 'react';
import { CreditCard, Calendar, AlertTriangle, CheckCircle } from 'lucide-react';
import LoadingSpinner from './common/LoadingSpinner';
import { useToast } from './common/useToast';
import ToastContainer from './common/ToastContainer';

const SubscriptionManagement = () => {
  const [subscriptions, setSubscriptions] = useState([]);
  const [paymentHistory, setPaymentHistory] = useState([]);
  const [loading, setLoading] = useState(true);
  const { toasts, removeToast, success, error: showError } = useToast();

  useEffect(() => {
    loadData();
  }, []);

  const loadData = async () => {
    try {
      setLoading(true);
      const accessToken = localStorage.getItem('accessToken');
      const user = JSON.parse(localStorage.getItem('user') || '{}');
      const headers = {
        'Content-Type': 'application/json',
      };
      if (accessToken) {
        headers['Authorization'] = `Bearer ${accessToken}`;
      }

      // Load subscriptions
      const subsResponse = await fetch(`http://localhost:8080/api/payment/subscriptions?userId=${user.id}`, { headers });
      if (subsResponse.ok) {
        const subsData = await subsResponse.json();
        setSubscriptions(subsData);
      }

      // Load payment history
      const historyResponse = await fetch(`http://localhost:8080/api/payment/history?userId=${user.id}`, { headers });
      if (historyResponse.ok) {
        const historyData = await historyResponse.json();
        setPaymentHistory(historyData);
      }

    } catch (err) {
      console.error('Error loading subscription data:', err);
      showError('Failed to load subscription data');
    } finally {
      setLoading(false);
    }
  };

  const handleCancelSubscription = async (subscriptionId) => {
    if (!window.confirm('Are you sure you want to cancel this subscription? This action cannot be undone.')) {
      return;
    }

    try {
      const accessToken = localStorage.getItem('accessToken');
      const headers = {
        'Content-Type': 'application/json',
      };
      if (accessToken) {
        headers['Authorization'] = `Bearer ${accessToken}`;
      }

      const response = await fetch(`http://localhost:8080/api/payment/subscriptions/${subscriptionId}/cancel`, {
        method: 'POST',
        headers
      });

      if (response.ok) {
        success('Subscription cancelled successfully');
        loadData(); // Reload data
      } else {
        showError('Failed to cancel subscription');
      }
    } catch (err) {
      console.error('Error cancelling subscription:', err);
      showError('Error cancelling subscription');
    }
  };

  const getStatusColor = (status) => {
    switch (status) {
      case 'active': return 'bg-green-100 text-green-800';
      case 'trial': return 'bg-blue-100 text-blue-800';
      case 'cancelled': return 'bg-red-100 text-red-800';
      case 'expired': return 'bg-gray-100 text-gray-800';
      default: return 'bg-yellow-100 text-yellow-800';
    }
  };

  const getStatusIcon = (status) => {
    switch (status) {
      case 'active': return <CheckCircle size={16} className="text-green-600" />;
      case 'trial': return <Calendar size={16} className="text-blue-600" />;
      case 'cancelled': return <AlertTriangle size={16} className="text-red-600" />;
      default: return <AlertTriangle size={16} className="text-yellow-600" />;
    }
  };

  if (loading) {
    return (
      <div className="p-8 ml-64 flex justify-center py-12">
        <LoadingSpinner size="lg" />
      </div>
    );
  }

  return (
    <div className="p-8 ml-64">
      <ToastContainer toasts={toasts} removeToast={removeToast} />

      <h2 className="text-2xl font-bold text-gray-800 mb-8">Subscription Management</h2>

      {/* Active Subscriptions */}
      <div className="bg-white rounded-lg shadow mb-6">
        <div className="p-6 border-b border-gray-200">
          <h3 className="text-lg font-semibold text-gray-900">Active Subscriptions</h3>
          <p className="text-gray-600 text-sm mt-1">Manage your current subscriptions</p>
        </div>

        <div className="p-6">
          {subscriptions.length === 0 ? (
            <div className="text-center py-8">
              <CreditCard size={48} className="mx-auto text-gray-400 mb-4" />
              <p className="text-gray-500">No active subscriptions</p>
              <p className="text-gray-400 text-sm mt-2">Subscribe to a plan to get started</p>
            </div>
          ) : (
            <div className="space-y-4">
              {subscriptions.map(subscription => (
                <div key={subscription.id} className="border border-gray-200 rounded-lg p-4">
                  <div className="flex justify-between items-start">
                    <div className="flex-1">
                      <div className="flex items-center gap-2 mb-2">
                        {getStatusIcon(subscription.status)}
                        <span className={`px-2 py-1 rounded-full text-xs font-medium ${getStatusColor(subscription.status)}`}>
                          {subscription.status.charAt(0).toUpperCase() + subscription.status.slice(1)}
                        </span>
                        <span className="text-gray-500 text-sm">
                          Plan #{subscription.planId}
                        </span>
                      </div>

                      <div className="grid grid-cols-2 gap-4 mt-3">
                        <div>
                          <p className="text-sm text-gray-600">Start Date</p>
                          <p className="font-medium">
                            {new Date(subscription.startDate).toLocaleDateString()}
                          </p>
                        </div>
                        {subscription.endDate && (
                          <div>
                            <p className="text-sm text-gray-600">End Date</p>
                            <p className="font-medium">
                              {new Date(subscription.endDate).toLocaleDateString()}
                            </p>
                          </div>
                        )}
                        {subscription.trialEndDate && (
                          <div>
                            <p className="text-sm text-gray-600">Trial Ends</p>
                            <p className="font-medium">
                              {new Date(subscription.trialEndDate).toLocaleDateString()}
                            </p>
                          </div>
                        )}
                      </div>

                      <div className="mt-3">
                        <p className="text-sm text-gray-600">Payment Method</p>
                        <p className="font-medium capitalize">{subscription.paymentMethod || 'N/A'}</p>
                      </div>
                    </div>

                    <div className="ml-4">
                      {subscription.status === 'active' && (
                        <button
                          onClick={() => handleCancelSubscription(subscription.id)}
                          className="px-4 py-2 bg-red-600 text-white text-sm rounded-lg hover:bg-red-700 transition-colors"
                        >
                          Cancel Subscription
                        </button>
                      )}
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* Payment History */}
      <div className="bg-white rounded-lg shadow">
        <div className="p-6 border-b border-gray-200">
          <h3 className="text-lg font-semibold text-gray-900">Payment History</h3>
          <p className="text-gray-600 text-sm mt-1">View your payment transactions</p>
        </div>

        <div className="p-6">
          {paymentHistory.length === 0 ? (
            <div className="text-center py-8">
              <CreditCard size={48} className="mx-auto text-gray-400 mb-4" />
              <p className="text-gray-500">No payment history</p>
            </div>
          ) : (
            <div className="space-y-3">
              {paymentHistory.map(transaction => (
                <div key={transaction.id} className="flex justify-between items-center p-4 border border-gray-200 rounded-lg">
                  <div className="flex items-center gap-4">
                    <div className="w-10 h-10 bg-gray-100 rounded-full flex items-center justify-center">
                      <CreditCard size={20} className="text-gray-600" />
                    </div>
                    <div>
                      <p className="font-medium">
                        ${transaction.amount} {transaction.currency.toUpperCase()}
                      </p>
                      <p className="text-sm text-gray-600">
                        {transaction.paymentMethod} • {transaction.transactionId}
                      </p>
                      <p className="text-xs text-gray-500">
                        {new Date(transaction.createdAt).toLocaleString()}
                      </p>
                    </div>
                  </div>

                  <div className="text-right">
                    <span className={`px-3 py-1 rounded-full text-xs font-medium ${
                      transaction.status === 'completed' ? 'bg-green-100 text-green-800' :
                      transaction.status === 'pending' ? 'bg-yellow-100 text-yellow-800' :
                      'bg-red-100 text-red-800'
                    }`}>
                      {transaction.status.charAt(0).toUpperCase() + transaction.status.slice(1)}
                    </span>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default SubscriptionManagement;
