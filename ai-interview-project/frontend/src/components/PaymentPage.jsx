import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { CreditCard, CheckCircle, XCircle, Clock, ArrowLeft } from 'lucide-react';
import LoadingSpinner from './common/LoadingSpinner';
import { useToast } from './common/useToast';
import ToastContainer from './common/ToastContainer';

const PaymentPage = () => {
  const navigate = useNavigate();
  const [plans, setPlans] = useState([]);
  const [subscriptions, setSubscriptions] = useState([]);
  const [paymentHistory, setPaymentHistory] = useState([]);
  const [loading, setLoading] = useState(true);
  const [selectedPlan, setSelectedPlan] = useState(null);
  const [paymentMethod, setPaymentMethod] = useState('stripe');
  const [error, setError] = useState('');
  const { toasts, removeToast, success, error: showError } = useToast();

  useEffect(() => {
    loadData();
  }, []);

  const loadData = async () => {
    try {
      const accessToken = localStorage.getItem('accessToken');
      const headers = {
        'Content-Type': 'application/json',
      };
      if (accessToken) {
        headers['Authorization'] = `Bearer ${accessToken}`;
      }

      // Load plans
      const plansResponse = await fetch('http://localhost:8080/api/payment/plans', { headers });
      if (plansResponse.ok) {
        const plansData = await plansResponse.json();
        setPlans(plansData);
        if (plansData.length > 0) {
          setSelectedPlan(plansData[0]);
        }
      }

      // Load user subscriptions
      const user = JSON.parse(localStorage.getItem('user') || '{}');
      if (user.id) {
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
      }

      setLoading(false);
    } catch (error) {
      console.error('Error loading payment data:', error);
      setLoading(false);
    }
  };

  const handleCheckout = async () => {
    if (!selectedPlan) return;

    setError('');
    try {
      const accessToken = localStorage.getItem('accessToken');
      const user = JSON.parse(localStorage.getItem('user') || '{}');
      
      const headers = {
        'Content-Type': 'application/json',
      };
      if (accessToken) {
        headers['Authorization'] = `Bearer ${accessToken}`;
      }

      const response = await fetch('http://localhost:8080/api/payment/checkout', {
        method: 'POST',
        headers,
        body: JSON.stringify({
          userId: user.id,
          planId: selectedPlan.id,
          paymentMethod: paymentMethod,
          successUrl: window.location.origin + '/payment/success',
          cancelUrl: window.location.origin + '/payment'
        })
      });

      const data = await response.json();
      
      if (data.url || data.paymentUrl) {
        // Redirect to payment page
        success('Redirecting to payment page...');
        window.location.href = data.url || data.paymentUrl;
      } else if (data.error) {
        setError(data.error);
        showError(data.error);
      } else {
        const errorMsg = data.message || 'Payment service not configured';
        setError(errorMsg);
        showError(errorMsg);
      }
    } catch (error) {
      console.error('Checkout error:', error);
      setError('Failed to initiate checkout');
    }
  };

  const handleCancelSubscription = async (subscriptionId) => {
    if (!window.confirm('Are you sure you want to cancel this subscription?')) {
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
        loadData(); // Reload subscriptions
      } else {
        showError('Failed to cancel subscription');
      }
    } catch (error) {
      console.error('Cancel subscription error:', error);
    }
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center">
        <div className="text-center">
          <LoadingSpinner size="lg" className="mx-auto mb-4" />
          <div className="text-gray-600">Loading...</div>
        </div>
      </div>
    );
  }

  const activeSubscription = subscriptions.find(sub => sub.status === 'active' || sub.status === 'trial');

  return (
    <div className="min-h-screen bg-gray-50 py-8 px-4 ml-64">
      <ToastContainer toasts={toasts} removeToast={removeToast} />
      <div className="max-w-6xl mx-auto">
        <div className="mb-8">
          <button
            onClick={() => navigate('/')}
            className="flex items-center gap-2 text-gray-600 hover:text-gray-900 mb-4"
          >
            <ArrowLeft size={20} />
            Back to Dashboard
          </button>
        </div>
        <h1 className="text-3xl font-bold text-gray-900 mb-8">Subscription & Payment</h1>

        {activeSubscription && (
          <div className="bg-green-50 border border-green-200 rounded-lg p-4 mb-6">
            <div className="flex items-center gap-2 text-green-800">
              <CheckCircle size={20} />
              <span className="font-semibold">Active Subscription</span>
            </div>
            <p className="text-green-700 mt-2">
              Status: {activeSubscription.status} | 
              {activeSubscription.trialEndDate && ` Trial ends: ${new Date(activeSubscription.trialEndDate).toLocaleDateString()}`}
            </p>
          </div>
        )}

        {error && (
          <div className="bg-red-50 border border-red-200 rounded-lg p-4 mb-6">
            <div className="flex items-center gap-2 text-red-800">
              <XCircle size={20} />
              <span>{error}</span>
            </div>
          </div>
        )}

        <div className="grid md:grid-cols-2 gap-6 mb-8">
          {/* Subscription Plans */}
          <div className="bg-white rounded-lg shadow p-6">
            <h2 className="text-xl font-semibold mb-4">Available Plans</h2>
            {plans.length === 0 ? (
              <p className="text-gray-500">No plans available. Please configure subscription plans.</p>
            ) : (
              <div className="space-y-4">
                {plans.map(plan => (
                  <div
                    key={plan.id}
                    className={`border-2 rounded-lg p-4 cursor-pointer transition ${
                      selectedPlan?.id === plan.id
                        ? 'border-purple-600 bg-purple-50'
                        : 'border-gray-200 hover:border-gray-300'
                    }`}
                    onClick={() => setSelectedPlan(plan)}
                  >
                    <div className="flex justify-between items-start">
                      <div>
                        <h3 className="font-semibold text-lg">{plan.name}</h3>
                        <p className="text-gray-600 text-sm mt-1">{plan.description}</p>
                        <p className="text-2xl font-bold text-purple-600 mt-2">
                          ${plan.price}/{plan.billingCycle === 'monthly' ? 'mo' : 'yr'}
                        </p>
                      </div>
                      {selectedPlan?.id === plan.id && (
                        <CheckCircle className="text-purple-600" size={24} />
                      )}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>

          {/* Payment Method Selection */}
          <div className="bg-white rounded-lg shadow p-6">
            <h2 className="text-xl font-semibold mb-4">Payment Method</h2>
            <div className="space-y-3">
              <label className="flex items-center gap-3 p-3 border-2 rounded-lg cursor-pointer hover:bg-gray-50">
                <input
                  type="radio"
                  name="paymentMethod"
                  value="stripe"
                  checked={paymentMethod === 'stripe'}
                  onChange={(e) => setPaymentMethod(e.target.value)}
                  className="w-4 h-4 text-purple-600"
                />
                <CreditCard size={20} />
                <span>Stripe (Credit Card)</span>
              </label>
              <label className="flex items-center gap-3 p-3 border-2 rounded-lg cursor-pointer hover:bg-gray-50">
                <input
                  type="radio"
                  name="paymentMethod"
                  value="alipay"
                  checked={paymentMethod === 'alipay'}
                  onChange={(e) => setPaymentMethod(e.target.value)}
                  className="w-4 h-4 text-purple-600"
                />
                <CreditCard size={20} />
                <span>Alipay (支付宝)</span>
              </label>
            </div>

            {selectedPlan && (
              <button
                onClick={handleCheckout}
                className="w-full mt-6 bg-purple-600 text-white py-3 rounded-lg font-semibold hover:bg-purple-700 transition"
              >
                Subscribe Now - ${selectedPlan.price}/{selectedPlan.billingCycle === 'monthly' ? 'month' : 'year'}
              </button>
            )}
          </div>
        </div>

        {/* Subscription History */}
        <div className="bg-white rounded-lg shadow p-6 mb-6">
          <h2 className="text-xl font-semibold mb-4">Subscription History</h2>
          {subscriptions.length === 0 ? (
            <p className="text-gray-500">No subscription history</p>
          ) : (
            <div className="space-y-3">
              {subscriptions.map(sub => (
                <div key={sub.id} className="flex justify-between items-center p-3 border rounded-lg">
                  <div>
                    <p className="font-medium">Plan ID: {sub.planId}</p>
                    <p className="text-sm text-gray-600">
                      Status: <span className={`px-2 py-1 rounded text-xs ${
                        sub.status === 'active' ? 'bg-green-100 text-green-700' :
                        sub.status === 'trial' ? 'bg-blue-100 text-blue-700' :
                        'bg-gray-100 text-gray-700'
                      }`}>{sub.status}</span>
                    </p>
                    <p className="text-sm text-gray-600 mt-1">
                      Started: {new Date(sub.startDate).toLocaleDateString()}
                      {sub.endDate && ` | Ends: ${new Date(sub.endDate).toLocaleDateString()}`}
                    </p>
                  </div>
                  {sub.status === 'active' && (
                    <button
                      onClick={() => handleCancelSubscription(sub.id)}
                      className="px-4 py-2 bg-red-600 text-white rounded hover:bg-red-700 text-sm"
                    >
                      Cancel
                    </button>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Payment History */}
        <div className="bg-white rounded-lg shadow p-6">
          <h2 className="text-xl font-semibold mb-4">Payment History</h2>
          {paymentHistory.length === 0 ? (
            <p className="text-gray-500">No payment history</p>
          ) : (
            <div className="space-y-3">
              {paymentHistory.map(transaction => (
                <div key={transaction.id} className="flex justify-between items-center p-3 border rounded-lg">
                  <div>
                    <p className="font-medium">
                      {transaction.amount} {transaction.currency}
                    </p>
                    <p className="text-sm text-gray-600">
                      Method: {transaction.paymentMethod} | 
                      Status: <span className={`px-2 py-1 rounded text-xs ${
                        transaction.status === 'success' ? 'bg-green-100 text-green-700' :
                        transaction.status === 'pending' ? 'bg-yellow-100 text-yellow-700' :
                        'bg-red-100 text-red-700'
                      }`}>{transaction.status}</span>
                    </p>
                    <p className="text-xs text-gray-500 mt-1">
                      {new Date(transaction.createdAt).toLocaleString()}
                    </p>
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

export default PaymentPage;

