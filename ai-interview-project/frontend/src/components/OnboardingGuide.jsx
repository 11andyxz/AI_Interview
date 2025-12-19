import React, { useState, useEffect } from 'react';
import Joyride, { STATUS } from 'react-joyride';
import { useLocation } from 'react-router-dom';

const OnboardingGuide = ({ run, onComplete }) => {
  const location = useLocation();
  const [steps, setSteps] = useState([]);
  const [currentRun, setCurrentRun] = useState(run);

  // Check if user has completed onboarding
  const hasCompletedOnboarding = () => {
    return localStorage.getItem('onboardingCompleted') === 'true';
  };

  // Mark onboarding as completed
  const completeOnboarding = () => {
    localStorage.setItem('onboardingCompleted', 'true');
    if (onComplete) {
      onComplete();
    }
  };

  // Generate steps based on current page
  useEffect(() => {
    const generateSteps = () => {
      const baseSteps = [];

      if (location.pathname === '/' || location.pathname === '/dashboard') {
        // Dashboard steps
        baseSteps.push(
          {
            target: '.sidebar',
            content: 'Welcome to AI Interview Master! This is your navigation sidebar. You can access different features from here.',
            placement: 'right',
            disableBeacon: true,
          },
          {
            target: '[data-tour="new-interview"]',
            content: 'Click here to create a new interview. This is where you start your AI interview practice.',
            placement: 'bottom',
          },
          {
            target: '[data-tour="interview-card"]',
            content: 'Your interview history appears here. Click on any interview to view details or continue where you left off.',
            placement: 'top',
          },
          {
            target: '[data-tour="filter-section"]',
            content: 'Use these filters to organize your interviews by status or sort them by date.',
            placement: 'bottom',
          }
        );
      } else if (location.pathname.startsWith('/interview/')) {
        // Interview room steps
        baseSteps.push(
          {
            target: '[data-tour="interview-info"]',
            content: 'Here you can see the interview details: candidate information, role, and tech stack.',
            placement: 'bottom',
          },
          {
            target: '[data-tour="ai-avatar"]',
            content: 'This is your AI interviewer. It will ask you questions and evaluate your responses.',
            placement: 'top',
          },
          {
            target: '[data-tour="chat-input"]',
            content: 'Type your answers here or use voice input. The AI will respond to your messages.',
            placement: 'top',
          },
          {
            target: '[data-tour="voice-controls"]',
            content: 'Use these controls to manage your microphone and camera during the interview.',
            placement: 'left',
          },
          {
            target: '[data-tour="transcript"]',
            content: 'Your conversation history appears here. You can review all questions and answers.',
            placement: 'left',
          }
        );
      } else if (location.pathname.startsWith('/report/')) {
        // Report page steps
        baseSteps.push(
          {
            target: '[data-tour="report-summary"]',
            content: 'This section shows your overall interview performance and statistics.',
            placement: 'bottom',
          },
          {
            target: '[data-tour="conversation-history"]',
            content: 'Review all your questions and answers with AI feedback.',
            placement: 'top',
          },
          {
            target: '[data-tour="download-buttons"]',
            content: 'Download your interview report as JSON or PDF for your records.',
            placement: 'bottom',
          }
        );
      }

      // Common final steps
      if (baseSteps.length > 0) {
        baseSteps.push({
          target: 'body',
          content: 'You\'re all set! Remember to practice regularly and review your feedback. You can always access help from the menu.',
          placement: 'center',
        });
      }

      setSteps(baseSteps);
    };

    generateSteps();
  }, [location.pathname]);

  const handleJoyrideCallback = (data) => {
    const { status } = data;

    if (status === STATUS.FINISHED || status === STATUS.SKIPPED) {
      completeOnboarding();
      setCurrentRun(false);
    }
  };

  // Don't show onboarding if user has completed it or if not requested to run
  if (!currentRun || hasCompletedOnboarding() || steps.length === 0) {
    return null;
  }

  return (
    <Joyride
      steps={steps}
      run={currentRun}
      continuous={true}
      showProgress={true}
      showSkipButton={true}
      callback={handleJoyrideCallback}
      styles={{
        options: {
          primaryColor: '#6366f1', // Indigo color to match the app theme
          textColor: '#1f2937',
          backgroundColor: '#ffffff',
          overlayColor: 'rgba(0, 0, 0, 0.5)',
          spotlightShadow: '0 0 15px rgba(0, 0, 0, 0.5)',
        },
        tooltip: {
          borderRadius: 8,
          fontSize: 14,
        },
        tooltipContainer: {
          textAlign: 'left',
        },
        buttonNext: {
          backgroundColor: '#6366f1',
          fontSize: 14,
          borderRadius: 6,
          padding: '8px 16px',
        },
        buttonBack: {
          color: '#6b7280',
          marginLeft: 'auto',
          marginRight: 8,
          fontSize: 14,
        },
        buttonSkip: {
          color: '#6b7280',
          fontSize: 14,
        },
        buttonClose: {
          height: 14,
          width: 14,
          right: 15,
          top: 15,
        },
      }}
      locale={{
        back: 'Previous',
        close: 'Close',
        last: 'Finish',
        next: 'Next',
        open: 'Open the dialog',
        skip: 'Skip tour',
      }}
    />
  );
};

export default OnboardingGuide;
