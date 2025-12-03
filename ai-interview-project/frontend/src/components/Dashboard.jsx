import React, { useEffect, useState } from 'react';
import InterviewCard from './InterviewCard';

const Dashboard = () => {
  const [interviews, setInterviews] = useState([]);

  useEffect(() => {
    // TO DO: Fetch data from Backend API
    // fetch('http://localhost:8080/api/interviews')
    //   .then(res => res.json())
    //   .then(data => setInterviews(data));
    
    // Mock Data for now to match screenshot
    const mockData = [
      { id: 1, title: "Internet / AI / Artificial Intelligence", language: "English", techStack: "JavaScript,Python,Java,Kotlin", date: "2025/11/25" },
      { id: 2, title: "Internet / AI / Artificial Intelligence", language: "English", techStack: "JavaScript,Python,Java,Kotlin", date: "2025/11/25" },
      { id: 3, title: "Internet / AI / Artificial Intelligence", language: "English", techStack: "JavaScript,Python,Java,Kotlin", date: "2025/11/24" },
      { id: 4, title: "Internet / AI / Artificial Intelligence", language: "English", techStack: "JavaScript,Python,Java,Kotlin", date: "2025/11/24" },
      { id: 5, title: "Internet / AI / Artificial Intelligence", language: "English", techStack: "JavaScript,Python,Java,Kotlin", date: "2025/11/23" },
      { id: 6, title: "Internet / AI / Artificial Intelligence", language: "English", techStack: "JavaScript,Python,Java,Kotlin", date: "2025/11/23" },
      { id: 7, title: "Internet / AI / Artificial Intelligence", language: "English", techStack: "JavaScript,Python,Java,Kotlin", date: "2025/06/14" },
      { id: 8, title: "Internet / AI / Artificial Intelligence", language: "English", techStack: "JavaScript,Python,Java,Kotlin", date: "2025/06/14" },
      { id: 9, title: "Internet / AI / Artificial Intelligence", language: "Chinese", techStack: "JavaScript,Python,Java,Kotlin", date: "2025/06/14" },
      { id: 10, title: "Internet / AI / Backend Development", language: "English", techStack: "JavaScript,Python,Java,Kotlin", date: "2025/05/08" },
      { id: 11, title: "Internet / AI / Backend Development", language: "English", techStack: "JavaScript,Python,Java,Kotlin", date: "2025/05/08" },
    ];
    setInterviews(mockData);
  }, []);

  return (
    <div className="p-8 ml-64">
      <div className="flex items-center justify-between mb-8">
        <h2 className="text-2xl font-bold text-gray-800">My Interviews</h2>
        {/* TO DO: Filter controls can go here */}
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
        {interviews.map((interview) => (
          <InterviewCard key={interview.id} interview={interview} />
        ))}
      </div>
    </div>
  );
};

export default Dashboard;

