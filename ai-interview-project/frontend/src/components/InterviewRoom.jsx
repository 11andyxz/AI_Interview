import React, { useState, useEffect } from 'react';
import { Mic, MicOff, Video, VideoOff, PhoneOff, MessageSquare, Send } from 'lucide-react';
import { useParams, useNavigate } from 'react-router-dom';

const InterviewRoom = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const [isMicOn, setIsMicOn] = useState(true);
  const [isVideoOn, setIsVideoOn] = useState(true);
  const [messages, setMessages] = useState([
    { sender: 'ai', text: "Hello! I'm your AI interviewer today. We'll be focusing on your background in Internet / AI / Artificial Intelligence. Ready to begin?" }
  ]);
  const [inputText, setInputText] = useState("");

  // TO DO: Initialize WebSocket or WebRTC connection here
  // useEffect(() => {
  //   connectToSignalingServer();
  // }, []);

  const handleSendMessage = () => {
    if (!inputText.trim()) return;

    // Add user message
    const newMessages = [...messages, { sender: 'user', text: inputText }];
    setMessages(newMessages);
    setInputText("");

    // TO DO: Send message to Backend/AI Service
    // aiService.sendMessage(inputText);

    // Mock AI thinking and response
    setTimeout(() => {
      setMessages(prev => [...prev, { 
        sender: 'ai', 
        text: "That's an interesting point. Can you elaborate more on how you handled the scalability issues in that project?" 
      }]);
    }, 1500);
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
          <div className="absolute bottom-4 right-4 w-48 h-36 bg-black rounded-lg border-2 border-gray-700 flex items-center justify-center overflow-hidden">
             {isVideoOn ? (
               <div className="w-full h-full bg-gray-700 flex items-center justify-center">
                 <span className="text-xs text-gray-400">Your Camera</span>
               </div>
             ) : (
               <VideoOff size={24} className="text-red-500" />
             )}
          </div>
        </div>

        {/* Controls Bar */}
        <div className="h-20 bg-gray-900 border-t border-gray-800 flex items-center justify-center gap-6">
          <button 
            onClick={() => setIsMicOn(!isMicOn)}
            className={`p-4 rounded-full ${isMicOn ? 'bg-gray-700 hover:bg-gray-600' : 'bg-red-500 hover:bg-red-600'} transition-colors`}
          >
            {isMicOn ? <Mic size={24} /> : <MicOff size={24} />}
          </button>
          
          <button 
            onClick={() => setIsVideoOn(!isVideoOn)}
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
          <div className="text-xs text-center text-gray-400 mt-2">
            Voice input is active (Simulated)
          </div>
        </div>
      </div>
    </div>
  );
};

export default InterviewRoom;

