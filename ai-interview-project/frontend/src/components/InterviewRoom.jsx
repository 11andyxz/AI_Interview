import React, { useState, useEffect, useRef } from 'react';
import { Mic, MicOff, Video, VideoOff, PhoneOff, MessageSquare, Send } from 'lucide-react';
import { useParams, useNavigate } from 'react-router-dom';

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
  const localStreamRef = useRef(null);
  const recognitionRef = useRef(null);
  const localVideoRef = useRef(null);
  const voiceTimerRef = useRef(null);
  const lastSentTextRef = useRef('');

  // Load interview session and conversation history
  useEffect(() => {
    const loadInterviewData = async () => {
      try {
        // Load interview session
        const sessionResponse = await fetch(`http://localhost:8080/api/interviews/${id}/session`);
        if (sessionResponse.ok) {
          const sessionData = await sessionResponse.json();
          setInterviewSession(sessionData);

          // Set initial greeting message based on candidate
          if (sessionData.candidate) {
            const greeting = `Hello ${sessionData.candidate.name}! I'm your AI interviewer today. We'll be focusing on your background in ${sessionData.interview.title}. Ready to begin?`;
            setMessages([{ sender: 'ai', text: greeting }]);
          }
        }

        // Load conversation history
        const historyResponse = await fetch(`http://localhost:8080/api/interviews/${id}/history`);
        if (historyResponse.ok) {
          const historyData = await historyResponse.json();
          setConversationHistory(historyData);

          // Convert history to messages for display
          const historyMessages = historyData.flatMap(qa => [
            { sender: 'user', text: qa.questionText },
            { sender: 'ai', text: qa.answerText }
          ]);
          setMessages(prev => [...prev, ...historyMessages]);
        }
      } catch (error) {
        console.error("Error loading interview data:", error);
        // Fallback greeting
        setMessages([{ sender: 'ai', text: "Hello! I'm your AI interviewer today. Ready to begin?" }]);
      }
    };

    loadInterviewData();
  }, [id]);

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

      // Auto-send logic
      if (trimmedText.length > 0) {
        // Check for sentence endings (., !, ?)
        const hasSentenceEnd = trimmedText.includes('.') ||
                              trimmedText.includes('!') ||
                              trimmedText.includes('?');

        if (hasSentenceEnd && trimmedText.length > 5) {
          // Auto-send when sentence ends and has minimum length
          console.log("Auto-sending voice message due to sentence end:", trimmedText);
          handleSendVoiceMessage(trimmedText);
          return;
        }

        // Reset timer for pause detection
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
    const newMessages = [...messages, { sender: 'user', text: userMessage }];
    setMessages(newMessages);

    // Clear subtitle after sending
    setSubtitle("");

    try {
      // Build ChatRequest object
      const chatRequest = {
        userMessage: userMessage,
        language: interviewSession?.interview?.language || 'English',
        recentHistory: conversationHistory.slice(-5) // Send last 5 QA pairs for context
      };

      // Call Backend/AI Service with ChatRequest
      const response = await fetch(`http://localhost:8080/api/interviews/${id}/chat`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
        },
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

  const handleSendMessage = async () => {
    if (!inputText.trim()) return;

    const userMessage = inputText.trim();

    // Add user message to display
    const newMessages = [...messages, { sender: 'user', text: userMessage }];
    setMessages(newMessages);
    setInputText("");

    try {
      // Build ChatRequest object
      const chatRequest = {
        userMessage: userMessage,
        language: interviewSession?.interview?.language || 'English',
        recentHistory: conversationHistory.slice(-5) // Send last 5 QA pairs for context
      };

      // Call Backend/AI Service with ChatRequest
      const response = await fetch(`http://localhost:8080/api/interviews/${id}/chat`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
        },
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
    // TO DO: Call backend to finalize interview session
    if (window.confirm("Are you sure you want to end the interview?")) {
      navigate('/');
    }
  };

  return (
    <div className="flex h-screen bg-gray-900 text-white overflow-hidden">
      {/* Main Video Area */}
      <div className="flex-1 flex flex-col relative">
        {/* AI Avatar / Video Placeholder */}
        <div className="flex-1 bg-gray-800 flex items-center justify-center relative">
          <div className="text-center">
            <div className="w-32 h-32 bg-purple-600 rounded-full mx-auto mb-4 flex items-center justify-center text-4xl font-bold animate-pulse">
              AI
            </div>
            <p className="text-gray-300">AI Interviewer is listening...</p>
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
        <div className="h-20 bg-gray-900 border-t border-gray-800 flex items-center justify-center gap-6">
          <button 
            onClick={toggleMic}
            className={`p-4 rounded-full ${isMicOn ? 'bg-gray-700 hover:bg-gray-600' : 'bg-red-500 hover:bg-red-600'} transition-colors`}
          >
            {isMicOn ? <Mic size={24} /> : <MicOff size={24} />}
          </button>
          
          <button 
            onClick={toggleVideo}
            className={`p-4 rounded-full ${isVideoOn ? 'bg-gray-700 hover:bg-gray-600' : 'bg-red-500 hover:bg-red-600'} transition-colors`}
          >
            {isVideoOn ? <Video size={24} /> : <VideoOff size={24} />}
          </button>

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
        <div className="p-4 border-b border-gray-200 flex items-center justify-between bg-gray-50">
          <h3 className="font-bold text-gray-700 flex items-center gap-2">
            <MessageSquare size={18} />
            Transcript
          </h3>
        </div>
        
        <div className="flex-1 overflow-y-auto p-4 space-y-4 bg-gray-50">
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
          {subtitle && (
            <div className="flex justify-start">
              <div className="max-w-[85%] p-3 rounded-lg text-sm bg-yellow-50 border border-yellow-200 text-gray-800 shadow-sm">
                Live captions: {subtitle}
              </div>
            </div>
          )}
        </div>

        <div className="p-4 border-t border-gray-200 bg-white">
          <div className="flex gap-2">
            <input
              type="text"
              value={inputText}
              onChange={(e) => setInputText(e.target.value)}
              onKeyPress={(e) => e.key === 'Enter' && handleSendMessage()}
              placeholder="Type your answer..." // Fallback for voice
              className="flex-1 border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-purple-500"
            />
            <button 
              onClick={handleSendMessage}
              className="p-2 bg-purple-600 text-white rounded-lg hover:bg-purple-700 transition-colors"
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
                className="px-3 py-1 rounded bg-green-100 text-green-700 hover:bg-green-200 text-xs font-medium"
              >
                Start
              </button>
            )}
            <span className="text-gray-400">{subtitleStatus}</span>
          </div>
        </div>
      </div>
    </div>
  );
};

export default InterviewRoom;

