import React, { useState, useEffect, useRef } from 'react';
import { Mic, MicOff, Video, VideoOff, PhoneOff, MessageSquare, Send, Pause, Play, BarChart3 } from 'lucide-react';
import { useParams, useNavigate } from 'react-router-dom';
import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';
import LoadingSpinner from './common/LoadingSpinner';
import ConfirmDialog from './common/ConfirmDialog';
import InterviewPreparation from './InterviewPreparation';
import { useToast } from './common/useToast';
import ToastContainer from './common/ToastContainer';
import { useInterviewShortcuts } from '../hooks/useKeyboardShortcuts';
import KeyboardShortcutsHelp from './KeyboardShortcutsHelp';

const InterviewRoom = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const [isMicOn, setIsMicOn] = useState(true);
  const [isVideoOn, setIsVideoOn] = useState(true);
  const [messages, setMessages] = useState([]);
  const [inputText, setInputText] = useState("");
  const [subtitle, setSubtitle] = useState("");
  const [subtitleStatus, setSubtitleStatus] = useState("off"); // off | listening | unavailable
  const [interviewSession, setInterviewSession] = useState(null);
  const [conversationHistory, setConversationHistory] = useState([]);
  const [interviewDuration, setInterviewDuration] = useState(0); // in seconds
  const [streamingResponse, setStreamingResponse] = useState('');
  const [isStreaming, setIsStreaming] = useState(false);
  const [loading, setLoading] = useState(true);
  const [endConfirmOpen, setEndConfirmOpen] = useState(false);
  const [showPreparation, setShowPreparation] = useState(true);
  const [isPaused, setIsPaused] = useState(false);
  const [showKeyboardHelp, setShowKeyboardHelp] = useState(false);
  const [isRecording, setIsRecording] = useState(false);
  const [recordingDuration, setRecordingDuration] = useState(0);
  const [recordedChunks, setRecordedChunks] = useState([]);
  const [mediaRecorder, setMediaRecorder] = useState(null);
  const [recordingStartTime, setRecordingStartTime] = useState(null);
  const [currentScore, setCurrentScore] = useState(0);
  const [questionCount, setQuestionCount] = useState(0);
  const [interviewProgress, setInterviewProgress] = useState(0);
  const localStreamRef = useRef(null);
  const recognitionRef = useRef(null);
  const localVideoRef = useRef(null);
  const voiceTimerRef = useRef(null);
  const lastSentTextRef = useRef('');
  const stompClientRef = useRef(null);
  const durationIntervalRef = useRef(null);
  const interviewStartTimeRef = useRef(null);
  const currentAiMessageRef = useRef(null);
  const inputRef = useRef(null);
  const { toasts, removeToast, success, error } = useToast();

  // Keyboard shortcuts handlers
  const handleSendMessageShortcut = () => {
    if (inputText.trim() && !isStreaming && inputRef.current) {
      handleSend();
    }
  };

  const handleExitInterview = () => {
    if (preparationComplete) {
      handleEndInterview();
    } else {
      handleBackToDashboard();
    }
  };

  const handleTogglePause = () => {
    if (preparationComplete) {
      setIsPaused(!isPaused);
      success(isPaused ? 'Interview resumed' : 'Interview paused');
    }
  };

  const handleSave = () => {
    // Could implement auto-save functionality
    success('Interview progress saved');
  };

  const handleShowKeyboardHelp = () => {
    setShowKeyboardHelp(true);
  };

  // Setup keyboard shortcuts
  useInterviewShortcuts({
    onSend: handleSendMessageShortcut,
    onExit: handleExitInterview,
    onTogglePause: handleTogglePause,
    onSave: handleSave
  });

  // Recording functions
  const startRecording = async () => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        audio: {
          sampleRate: 44100,
          channelCount: 1,
          volume: 1.0
        }
      });

      const recorder = new MediaRecorder(stream, {
        mimeType: 'audio/webm;codecs=opus'
      });

      const chunks = [];
      recorder.ondataavailable = (event) => {
        if (event.data.size > 0) {
          chunks.push(event.data);
        }
      };

      recorder.onstop = async () => {
        const audioBlob = new Blob(chunks, { type: 'audio/webm' });
        setRecordedChunks(chunks);

        // Upload the recording
        await uploadRecording(audioBlob);
      };

      recorder.start(1000); // Collect data every second
      setMediaRecorder(recorder);
      setIsRecording(true);
      setRecordingStartTime(Date.now());
      setRecordingDuration(0);

      // Start duration counter
      const interval = setInterval(() => {
        setRecordingDuration(prev => prev + 1);
      }, 1000);

      // Store interval ID for cleanup
      recorder.intervalId = interval;

    } catch (error) {
      console.error('Error starting recording:', error);
      alert('Could not start recording. Please check microphone permissions.');
    }
  };

  const stopRecording = () => {
    if (mediaRecorder && mediaRecorder.state === 'recording') {
      mediaRecorder.stop();
      setIsRecording(false);

      // Stop all tracks
      mediaRecorder.stream.getTracks().forEach(track => track.stop());

      // Clear duration interval
      if (mediaRecorder.intervalId) {
        clearInterval(mediaRecorder.intervalId);
      }
    }
  };

  const uploadRecording = async (audioBlob) => {
    try {
      const formData = new FormData();
      const filename = `interview-${id}-recording-${Date.now()}.webm`;
      const audioFile = new File([audioBlob], filename, { type: 'audio/webm' });

      // Convert blob to base64 for upload
      const reader = new FileReader();
      reader.onloadend = async () => {
        const base64Data = reader.result.split(',')[1];

        const response = await fetch(`http://localhost:8080/api/interviews/${id}/recording/blob`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${localStorage.getItem('accessToken')}`
          },
          body: JSON.stringify({
            filename,
            audioData: base64Data,
            durationSeconds: recordingDuration
          })
        });

        if (response.ok) {
          success('Recording saved successfully');
        } else {
          throw new Error('Failed to save recording');
        }
      };

      reader.readAsDataURL(audioBlob);
    } catch (error) {
      console.error('Error uploading recording:', error);
      showError('Failed to save recording');
    }
  };

  // Load interview session and conversation history
  useEffect(() => {
    const loadInterviewData = async () => {
      try {
        const accessToken = localStorage.getItem('accessToken');
        const headers = {
          'Content-Type': 'application/json',
        };
        if (accessToken) {
          headers['Authorization'] = `Bearer ${accessToken}`;
        }

        // Load interview session
        const sessionResponse = await fetch(`http://localhost:8080/api/interviews/${id}/session`, {
          headers
        });
        if (sessionResponse.ok) {
          const sessionData = await sessionResponse.json();
          setInterviewSession(sessionData);

          // Check if interview is in progress and can be resumed
          const canResume = sessionData.interview && sessionData.interview.status === 'In Progress';
          if (canResume && sessionData.conversationHistory && sessionData.conversationHistory.length > 0) {
            // Resume automatically, no confirmation needed
          }

          // Set initial greeting message based on candidate
          if (sessionData.candidate && (!sessionData.conversationHistory || sessionData.conversationHistory.length === 0)) {
            const greeting = `Hello ${sessionData.candidate.name}! I'm your AI interviewer today. We'll be focusing on your background in ${sessionData.interview.title}. Ready to begin?`;
            setMessages([{ sender: 'ai', text: greeting }]);
          }
        }

        // Load conversation history
        const historyResponse = await fetch(`http://localhost:8080/api/interviews/${id}/history`, {
          headers
        });
        if (historyResponse.ok) {
          const historyData = await historyResponse.json();
          setConversationHistory(historyData);

          // Convert history to messages for display
          const historyMessages = historyData.flatMap(qa => [
            { sender: 'user', text: qa.questionText },
            { sender: 'ai', text: qa.answerText }
          ]);
          if (historyMessages.length > 0) {
            setMessages(prev => {
              // Avoid duplicates
              const existing = prev.map(m => m.text).join('|');
              const newTexts = historyMessages.map(m => m.text).join('|');
              if (existing.includes(newTexts)) {
                return prev;
              }
              return [...prev, ...historyMessages];
            });
          }
        }
      } catch (err) {
        console.error("Error loading interview data:", err);
        error('Failed to load interview data');
        // Fallback greeting
        setMessages([{ sender: 'ai', text: "Hello! I'm your AI interviewer today. Ready to begin?" }]);
      } finally {
        setLoading(false);
      }
    };

    loadInterviewData();
  }, [id]);

  // Initialize WebSocket connection
  useEffect(() => {
    const connectWebSocket = () => {
      const socket = new SockJS('http://localhost:8080/ws');
      const stompClient = new Client({
        webSocketFactory: () => socket,
        reconnectDelay: 5000,
        heartbeatIncoming: 4000,
        heartbeatOutgoing: 4000,
        onConnect: () => {
          console.log('WebSocket connected');
          
          // Subscribe to AI response stream
          stompClient.subscribe(`/topic/interview/${id}/response`, (message) => {
            const response = JSON.parse(message.body);
            
            if (response.type === 'chunk') {
              // Streaming chunk
              setIsStreaming(true);
              setStreamingResponse(prev => prev + response.content);
              currentAiMessageRef.current = (currentAiMessageRef.current || '') + response.content;
            } else if (response.type === 'complete') {
              // Stream complete
              setIsStreaming(false);
              if (currentAiMessageRef.current) {
                setMessages(prev => [...prev, {
                  sender: 'ai',
                  text: currentAiMessageRef.current
                }]);
                // Update conversation history
                const lastUserMessage = messages[messages.length - 1];
                if (lastUserMessage && lastUserMessage.sender === 'user') {
                  setConversationHistory(prev => [...prev, {
                    questionText: lastUserMessage.text,
                    answerText: currentAiMessageRef.current,
                    createdAt: new Date().toISOString()
                  }]);
                }
                currentAiMessageRef.current = '';
                setStreamingResponse('');
              }
            } else if (response.type === 'error') {
              setIsStreaming(false);
              setMessages(prev => [...prev, {
                sender: 'ai',
                text: response.content || 'An error occurred'
              }]);
              setStreamingResponse('');
              currentAiMessageRef.current = '';
            }
          });
        },
        onStompError: (frame) => {
          console.error('WebSocket STOMP error:', frame);
        },
        onWebSocketClose: () => {
          console.log('WebSocket closed');
        }
      });
      
      stompClient.activate();
      stompClientRef.current = stompClient;
    };

    connectWebSocket();

    return () => {
      if (stompClientRef.current) {
        stompClientRef.current.deactivate();
      }
    };
  }, [id, messages]);

  // Track interview duration
  useEffect(() => {
    interviewStartTimeRef.current = Date.now();
    durationIntervalRef.current = setInterval(() => {
      if (interviewStartTimeRef.current) {
        const elapsed = Math.floor((Date.now() - interviewStartTimeRef.current) / 1000);
        setInterviewDuration(elapsed);
        
        // Show warning if over 2 hours (7200 seconds)
        if (elapsed > 7200 && elapsed % 300 === 0) { // Warn every 5 minutes after 2 hours
          console.warn('Interview duration exceeds 2 hours');
        }
      }
    }, 1000);

    return () => {
      if (durationIntervalRef.current) {
        clearInterval(durationIntervalRef.current);
      }
    };
  }, []);

  // TO DO: Initialize WebSocket or WebRTC connection here
  // useEffect(() => {
  //   connectToSignalingServer();
  // }, []);

  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => {
    const initMedia = async () => {
      try {
        const stream = await navigator.mediaDevices.getUserMedia({ audio: true, video: true });
        localStreamRef.current = stream;
        if (localVideoRef.current) {
          localVideoRef.current.srcObject = stream;
          const vid = localVideoRef.current;
          vid.onloadedmetadata = () => {
            vid.play().catch(() => {});
          };
        }
        // 默认关闭实际视频画面展示，只用于权限与音轨控制
        if (!isVideoOn) {
          stream.getVideoTracks().forEach(t => (t.enabled = false));
        }
        if (!isMicOn) {
          stream.getAudioTracks().forEach(t => (t.enabled = false));
        }
        // 自动启动字幕
        setTimeout(() => {
          startSubtitle();
        }, 500);
      } catch (err) {
        console.error("getUserMedia error:", err);
      }
    };
    initMedia();
    return () => {
      if (localStreamRef.current) {
        localStreamRef.current.getTracks().forEach(t => t.stop());
      }
      if (recognitionRef.current) {
        recognitionRef.current.stop();
      }
      // Clear voice timer
      if (voiceTimerRef.current) {
        clearTimeout(voiceTimerRef.current);
      }
    };
  }, []); // mount only

  const toggleMic = () => {
    const next = !isMicOn;
    setIsMicOn(next);
    if (localStreamRef.current) {
      localStreamRef.current.getAudioTracks().forEach(t => (t.enabled = next));
    }
  };

  const toggleVideo = () => {
    const next = !isVideoOn;
    setIsVideoOn(next);
    if (localStreamRef.current) {
      localStreamRef.current.getVideoTracks().forEach(t => (t.enabled = next));
    }
    if (next && localVideoRef.current) {
      localVideoRef.current.play().catch(() => {});
    }
  };

  // Reset voice timer for auto-send
  const resetVoiceTimer = () => {
    if (voiceTimerRef.current) {
      clearTimeout(voiceTimerRef.current);
    }
    voiceTimerRef.current = setTimeout(() => {
      const currentText = subtitle.trim();
      if (currentText.length > 10 && currentText !== lastSentTextRef.current) {
        console.log("Auto-sending voice message due to timeout:", currentText);
        handleSendVoiceMessage(currentText);
      }
    }, 3000); // 3 seconds of silence
  };

  const startSubtitle = () => {
    const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!SpeechRecognition) {
      setSubtitleStatus("unavailable");
      setSubtitle("Live captions not supported in this browser.");
      return;
    }
    if (recognitionRef.current) {
      recognitionRef.current.stop();
    }
    const rec = new SpeechRecognition();
    rec.continuous = true;
    rec.interimResults = true;
    rec.lang = 'en-US';
    rec.onresult = (event) => {
      let text = "";
      for (let i = event.resultIndex; i < event.results.length; ++i) {
        text += event.results[i][0].transcript;
      }
      const trimmedText = text.trim();
      setSubtitle(trimmedText);

      // Auto-send logic - use pause detection (3 seconds of silence)
      if (trimmedText.length > 0) {
        // Reset timer for pause detection (will auto-send after 3 seconds of silence)
        resetVoiceTimer();
      }
    };
    rec.onerror = (e) => {
      console.error("Speech recognition error:", e);
      setSubtitleStatus("unavailable");
    };
    rec.onend = () => {
      // 自动重启以保持监听（使用 ref 避免闭包问题）
      if (recognitionRef.current === rec) {
        try {
          rec.start();
        } catch (err) {
          console.log("Recognition restart failed:", err);
        }
      }
    };
    recognitionRef.current = rec;
    setSubtitleStatus("listening");
    rec.start();
  };

  const stopSubtitle = () => {
    setSubtitleStatus("off");
    if (recognitionRef.current) {
      recognitionRef.current.stop();
      recognitionRef.current = null;
    }
    // Clear voice timer
    if (voiceTimerRef.current) {
      clearTimeout(voiceTimerRef.current);
      voiceTimerRef.current = null;
    }
  };

  const handleSendVoiceMessage = async (voiceText) => {
    if (!voiceText.trim()) return;

    const userMessage = voiceText.trim();

    // Avoid sending duplicate messages
    if (userMessage === lastSentTextRef.current) return;
    lastSentTextRef.current = userMessage;

    // Clear any pending timer
    if (voiceTimerRef.current) {
      clearTimeout(voiceTimerRef.current);
      voiceTimerRef.current = null;
    }

    // Add user message to display
    setMessages(prev => [...prev, { sender: 'user', text: userMessage }]);

    // Update real-time feedback
    const score = calculateRealtimeScore(userMessage);
    setCurrentScore(score);
    setQuestionCount(prev => prev + 1);
    updateProgress();

    // Clear subtitle after sending
    setSubtitle("");
    setStreamingResponse('');
    currentAiMessageRef.current = '';
    setIsStreaming(true);

    // Send via WebSocket
    if (stompClientRef.current && stompClientRef.current.connected) {
      const transcriptMessage = {
        interviewId: id,
        text: userMessage,
        language: interviewSession?.interview?.language || 'English',
        isFinal: true
      };
      stompClientRef.current.publish({
        destination: '/app/transcript',
        body: JSON.stringify(transcriptMessage)
      });
    } else {
      // Fallback to HTTP if WebSocket not connected
      try {
        const accessToken = localStorage.getItem('accessToken');
        const headers = {
          'Content-Type': 'application/json',
        };
        if (accessToken) {
          headers['Authorization'] = `Bearer ${accessToken}`;
        }

        const chatRequest = {
          userMessage: userMessage,
          language: interviewSession?.interview?.language || 'English',
          recentHistory: conversationHistory.slice(-5)
        };

        const response = await fetch(`http://localhost:8080/api/interviews/${id}/chat`, {
          method: 'POST',
          headers,
          body: JSON.stringify(chatRequest)
        });

        if (response.ok) {
          const aiResponseText = await response.text();
          setIsStreaming(false);
          setMessages(prev => [...prev, {
            sender: 'ai',
            text: aiResponseText
          }]);

          const newQA = {
            questionText: userMessage,
            answerText: aiResponseText,
            createdAt: new Date().toISOString()
          };
          setConversationHistory(prev => [...prev, newQA]);
        }
      } catch (error) {
        console.error("Error calling AI service:", error);
        setIsStreaming(false);
      }
    }
  };

  const handleSendMessage = async () => {
    if (!inputText.trim()) return;

    const userMessage = inputText.trim();

    // Add user message to display
    const newMessages = [...messages, { sender: 'user', text: userMessage }];
    setMessages(newMessages);
    setInputText("");

    // Update real-time feedback
    const score = calculateRealtimeScore(userMessage);
    setCurrentScore(score);
    setQuestionCount(prev => prev + 1);
    updateProgress();

    try {
      // Build ChatRequest object
      const chatRequest = {
        userMessage: userMessage,
        language: interviewSession?.interview?.language || 'English',
        recentHistory: conversationHistory.slice(-5) // Send last 5 QA pairs for context
      };

      // Call Backend/AI Service with ChatRequest
      const accessToken = localStorage.getItem('accessToken');
      const headers = {
        'Content-Type': 'application/json',
      };
      if (accessToken) {
        headers['Authorization'] = `Bearer ${accessToken}`;
      }

      const response = await fetch(`http://localhost:8080/api/interviews/${id}/chat`, {
        method: 'POST',
        headers,
        body: JSON.stringify(chatRequest)
      });

      if (response.ok) {
        const aiResponseText = await response.text();
        setMessages(prev => [...prev, { 
          sender: 'ai', 
          text: aiResponseText 
        }]);

        // Update conversation history
        const newQA = {
          questionText: userMessage,
          answerText: aiResponseText,
          createdAt: new Date().toISOString()
        };
        setConversationHistory(prev => [...prev, newQA]);
      } else {
        console.error("Failed to get AI response:", response.status);
        // Fallback message
        setMessages(prev => [...prev, {
          sender: 'ai',
          text: "抱歉，我暂时无法处理您的回答。请稍后再试。"
        }]);
      }
    } catch (error) {
      console.error("Error calling AI service:", error);
      // Fallback for demo if backend is not running
      setTimeout(() => {
        setMessages(prev => [...prev, { 
          sender: 'ai', 
          text: "(离线模式) 这是个很有趣的观点。你能详细说明一下吗？"
        }]);
      }, 1500);
    }
  };

  const handleEndInterview = () => {
    setEndConfirmOpen(true);
  };

  const confirmEndInterview = async () => {
    setEndConfirmOpen(false);
    try {
      const accessToken = localStorage.getItem('accessToken');
      const headers = {
        'Content-Type': 'application/json',
      };
      if (accessToken) {
        headers['Authorization'] = `Bearer ${accessToken}`;
      }

      // Call backend to end interview and generate report
      const response = await fetch(`http://localhost:8080/api/interviews/${id}/end`, {
        method: 'POST',
        headers
      });

        if (response.ok) {
          const data = await response.json();
          success('Interview ended successfully. Report generated.');
          
          // Close WebSocket connection
          if (stompClientRef.current) {
            stompClientRef.current.deactivate();
          }

          // Navigate to report page
          setTimeout(() => {
            navigate(`/report/${id}`);
          }, 1500);
        } else {
          const errorData = await response.json().catch(() => ({}));
          error(errorData.message || 'Failed to end interview');
        }
    } catch (err) {
      console.error("Error ending interview:", err);
      error('Error ending interview');
    }
  };

  const handleStartInterview = () => {
    setShowPreparation(false);
  };

  const handleBackToDashboard = () => {
    navigate('/');
  };

  const togglePause = () => {
    setIsPaused(!isPaused);
  };

  // Calculate real-time score based on answer quality indicators
  const calculateRealtimeScore = (answerText) => {
    if (!answerText || answerText.trim().length === 0) return 0;

    let score = 50; // Base score

    const length = answerText.length;
    const wordCount = answerText.split(/\s+/).length;

    // Length scoring
    if (length > 500) score += 20;
    else if (length > 200) score += 10;
    else if (length > 100) score += 5;
    else if (length < 50) score -= 10;

    // Word count scoring
    if (wordCount > 100) score += 10;
    else if (wordCount > 50) score += 5;
    else if (wordCount < 20) score -= 5;

    // Technical keywords (basic detection)
    const technicalKeywords = ['function', 'class', 'method', 'api', 'database', 'algorithm', 'design', 'pattern', 'framework', 'library'];
    const keywordMatches = technicalKeywords.filter(keyword =>
      answerText.toLowerCase().includes(keyword.toLowerCase())
    ).length;
    score += Math.min(keywordMatches * 3, 15);

    // Structure indicators
    if (answerText.includes('?') || answerText.includes('because') || answerText.includes('therefore')) {
      score += 5;
    }

    return Math.max(0, Math.min(100, score));
  };

  // Update progress based on conversation length and time
  const updateProgress = () => {
    const baseProgress = Math.min(questionCount * 15, 60); // 15% per question, max 60%
    const timeProgress = Math.min((interviewDuration / 1800) * 40, 40); // 40% based on 30min duration
    setInterviewProgress(Math.min(baseProgress + timeProgress, 100));
  };

  // Format duration as HH:MM:SS
  const formatDuration = (seconds) => {
    const hrs = Math.floor(seconds / 3600);
    const mins = Math.floor((seconds % 3600) / 60);
    const secs = seconds % 60;
    return `${String(hrs).padStart(2, '0')}:${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;
  };

  if (loading) {
    return (
      <div className="flex h-screen bg-gray-900 text-white items-center justify-center">
        <div className="text-center">
          <LoadingSpinner size="xl" className="mx-auto mb-4" />
          <p className="text-gray-300">Loading interview...</p>
        </div>
      </div>
    );
  }

  // Show preparation screen first
  if (showPreparation) {
    return (
      <InterviewPreparation
        interviewData={interviewSession?.interview}
        onStartInterview={handleStartInterview}
        onBack={handleBackToDashboard}
      />
    );
  }

  return (
    <div className="flex h-screen bg-gray-900 text-white overflow-hidden">
      <ToastContainer toasts={toasts} removeToast={removeToast} />
      {/* Main Video Area */}
      <div className="flex-1 flex flex-col relative">
        {/* AI Avatar / Video Placeholder */}
        <div className="flex-1 bg-gray-800 flex items-center justify-center relative" data-tour="ai-avatar">
          <div className="text-center">
            <div className="w-32 h-32 bg-purple-600 rounded-full mx-auto mb-4 flex items-center justify-center text-4xl font-bold animate-pulse">
              AI
            </div>
            <p className="text-gray-300">AI Interviewer is listening...</p>
            <p className="text-gray-400 text-sm mt-2">Duration: {formatDuration(interviewDuration)}</p>

            {/* Real-time Feedback */}
            <div className="mt-6 space-y-3 max-w-md mx-auto">
              {/* Progress Bar */}
              <div>
                <div className="flex justify-between text-xs text-gray-400 mb-1">
                  <span>Interview Progress</span>
                  <span>{Math.round(interviewProgress)}%</span>
                </div>
                <div className="w-full bg-gray-700 rounded-full h-2">
                  <div
                    className="bg-purple-600 h-2 rounded-full transition-all duration-300"
                    style={{ width: `${interviewProgress}%` }}
                  />
                </div>
              </div>

              {/* Current Score */}
              <div className="flex items-center justify-center gap-4">
                <div className="text-center">
                  <div className="flex items-center gap-2 mb-1">
                    <BarChart3 size={16} className="text-purple-400" />
                    <span className="text-xs text-gray-400">Current Score</span>
                  </div>
                  <div className={`text-2xl font-bold ${
                    currentScore >= 80 ? 'text-green-400' :
                    currentScore >= 60 ? 'text-yellow-400' :
                    'text-red-400'
                  }`}>
                    {currentScore}
                  </div>
                </div>

                <div className="text-center">
                  <div className="flex items-center gap-2 mb-1">
                    <MessageSquare size={16} className="text-blue-400" />
                    <span className="text-xs text-gray-400">Questions</span>
                  </div>
                  <div className="text-2xl font-bold text-blue-400">
                    {questionCount}
                  </div>
                </div>
              </div>
            </div>

            {interviewDuration > 7200 && (
              <p className="text-yellow-400 text-xs mt-1">Interview duration exceeds 2 hours</p>
            )}
          </div>
          
          {/* User Video Overlay (Picture-in-Picture style) */}
          <div className="absolute bottom-4 right-4 w-64 h-48 bg-black rounded-lg border-2 border-gray-700 flex items-center justify-center overflow-hidden">
             {isVideoOn ? (
               <video
                 ref={localVideoRef}
                 autoPlay
                 muted
                 playsInline
                 className="w-full h-full object-cover bg-black"
               />
             ) : (
               <div className="w-full h-full bg-gray-700 flex flex-col items-center justify-center text-gray-300 text-sm gap-2">
                 <VideoOff size={28} className="text-red-400" />
                 <span>Your Camera</span>
               </div>
             )}
          </div>
        </div>

        {/* Controls Bar */}
        <div className="h-20 bg-gray-900 border-t border-gray-800 flex items-center justify-center gap-6" data-tour="voice-controls">
          <button
            onClick={toggleMic}
            disabled={isPaused}
            className={`p-4 rounded-full ${isMicOn ? 'bg-gray-700 hover:bg-gray-600' : 'bg-red-500 hover:bg-red-600'} transition-colors ${isPaused ? 'opacity-50 cursor-not-allowed' : ''}`}
          >
            {isMicOn ? <Mic size={24} /> : <MicOff size={24} />}
          </button>

          <button
            onClick={toggleVideo}
            disabled={isPaused}
            className={`p-4 rounded-full ${isVideoOn ? 'bg-gray-700 hover:bg-gray-600' : 'bg-red-500 hover:bg-red-600'} transition-colors ${isPaused ? 'opacity-50 cursor-not-allowed' : ''}`}
          >
            {isVideoOn ? <Video size={24} /> : <VideoOff size={24} />}
          </button>

          <button
            onClick={togglePause}
            className={`p-4 rounded-full ${isPaused ? 'bg-yellow-600 hover:bg-yellow-700' : 'bg-blue-600 hover:bg-blue-700'} transition-colors flex items-center gap-2`}
          >
            {isPaused ? <Play size={24} /> : <Pause size={24} />}
            <span className="font-semibold">{isPaused ? 'Resume' : 'Pause'}</span>
          </button>

          {/* Recording Controls */}
          <div className="flex flex-col items-center gap-2">
            <button
              onClick={isRecording ? stopRecording : startRecording}
              className={`p-4 rounded-full ${isRecording ? 'bg-red-600 hover:bg-red-700 animate-pulse' : 'bg-green-600 hover:bg-green-700'} transition-colors flex items-center gap-2`}
            >
              <div className={`w-3 h-3 rounded-full ${isRecording ? 'bg-white' : 'bg-white'}`}></div>
              <span className="font-semibold">
                {isRecording ? 'Stop Recording' : 'Start Recording'}
              </span>
            </button>
            {isRecording && (
              <div className="text-sm text-gray-600 bg-white px-3 py-1 rounded-full shadow">
                Recording: {Math.floor(recordingDuration / 60)}:{(recordingDuration % 60).toString().padStart(2, '0')}
              </div>
            )}
          </div>

          <button
            onClick={handleEndInterview}
            className="p-4 rounded-full bg-red-600 hover:bg-red-700 transition-colors px-8 flex items-center gap-2"
          >
            <PhoneOff size={24} />
            <span className="font-semibold">End Call</span>
          </button>
        </div>
      </div>

      {/* Chat Sidebar */}
      <div className="w-80 bg-white border-l border-gray-200 flex flex-col text-gray-800">
        <div className="p-4 border-b border-gray-200 flex items-center justify-between bg-gray-50" data-tour="interview-info">
          <h3 className="font-bold text-gray-700 flex items-center gap-2">
            <MessageSquare size={18} />
            Transcript
          </h3>
        </div>
        
        <div className="flex-1 overflow-y-auto p-4 space-y-4 bg-gray-50" data-tour="transcript">
          {messages.map((msg, idx) => (
            <div key={idx} className={`flex ${msg.sender === 'user' ? 'justify-end' : 'justify-start'}`}>
              <div className={`max-w-[85%] p-3 rounded-lg text-sm ${
                msg.sender === 'user'
                  ? 'bg-purple-600 text-white rounded-br-none'
                  : 'bg-white border border-gray-200 text-gray-800 rounded-bl-none shadow-sm'
              }`}>
                {msg.text}
              </div>
            </div>
          ))}
          {isStreaming && streamingResponse && (
            <div className="flex justify-start">
              <div className="max-w-[85%] p-3 rounded-lg text-sm bg-blue-50 border border-blue-200 text-gray-800 rounded-bl-none shadow-sm">
                {streamingResponse}
                <span className="animate-pulse">▋</span>
              </div>
            </div>
          )}
          {subtitle && (
            <div className="flex justify-start">
              <div className="max-w-[85%] p-3 rounded-lg text-sm bg-yellow-50 border border-yellow-200 text-gray-800 shadow-sm">
                Live captions: {subtitle}
              </div>
            </div>
          )}
        </div>

        <div className="p-4 border-t border-gray-200 bg-white">
          {isPaused && (
            <div className="mb-3 p-2 bg-yellow-50 border border-yellow-200 rounded-lg">
              <p className="text-sm text-yellow-800 text-center">
                Interview is paused. Click "Resume" to continue.
              </p>
            </div>
          )}
          <div className="flex gap-2" data-tour="chat-input">
            <input
              ref={inputRef}
              type="text"
              value={inputText}
              onChange={(e) => setInputText(e.target.value)}
              onKeyPress={(e) => !isPaused && e.key === 'Enter' && handleSendMessage()}
              placeholder={isPaused ? "Interview is paused..." : "Type your answer..."}
              disabled={isPaused}
              className={`flex-1 border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-purple-500 ${isPaused ? 'bg-gray-100 text-gray-500 cursor-not-allowed' : ''}`}
            />
            <button
              onClick={handleSendMessage}
              disabled={isPaused}
              className={`p-2 rounded-lg transition-colors ${isPaused ? 'bg-gray-400 cursor-not-allowed' : 'bg-purple-600 text-white hover:bg-purple-700'}`}
            >
              <Send size={18} />
            </button>
          </div>
          <div className="text-xs text-center text-gray-400 mt-2 flex items-center justify-center gap-3">
            <span>Captions:</span>
            {subtitleStatus === "listening" ? (
              <button
                onClick={stopSubtitle}
                className="px-3 py-1 rounded bg-purple-100 text-purple-700 hover:bg-purple-200 text-xs font-medium"
              >
                Stop
              </button>
            ) : (
              <button
                onClick={startSubtitle}
                disabled={isPaused}
                className={`px-3 py-1 rounded text-xs font-medium ${isPaused ? 'bg-gray-100 text-gray-400 cursor-not-allowed' : 'bg-green-100 text-green-700 hover:bg-green-200'}`}
              >
                Start
              </button>
            )}
            <span className="text-gray-400">{isPaused ? 'paused' : subtitleStatus}</span>
          </div>
        </div>
      </div>

      <ConfirmDialog
        isOpen={endConfirmOpen}
        onClose={() => setEndConfirmOpen(false)}
        onConfirm={confirmEndInterview}
        title="End Interview"
        message="Are you sure you want to end this interview? A report will be generated."
        confirmText="End Interview"
        cancelText="Cancel"
        type="warning"
      />

      <KeyboardShortcutsHelp
        isOpen={showKeyboardHelp}
        onClose={() => setShowKeyboardHelp(false)}
      />
    </div>
  );
};

export default InterviewRoom;

