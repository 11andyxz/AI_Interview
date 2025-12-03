# AI Interview Master

A full-stack application for simulating technical interviews with an AI interviewer.

## Tech Stack

- **Frontend**: React, Tailwind CSS, Lucide React
- **Backend**: Java 17, Spring Boot 3.2
- **AI Integration**: Placeholder for LLM (OpenAI/Anthropic) & Speech Services

## Project Structure

```
ai-interview-project/
├── backend/     # Spring Boot Application
└── frontend/    # React Application
```

## Getting Started

### Backend

1. Navigate to `backend` directory:
   ```bash
   cd ai-interview-project/backend
   ```
2. Run the application:
   ```bash
   mvn spring-boot:run
   ```
   Server starts at `http://localhost:8080`.

### Frontend

1. Navigate to `frontend` directory:
   ```bash
   cd ai-interview-project/frontend
   ```
2. Install dependencies:
   ```bash
   npm install
   ```
3. Start the development server:
   ```bash
   npm start
   ```
   App opens at `http://localhost:3000`.

## Features

- **Dashboard**: View interview history and performance stats.
- **Interview Room**: Real-time mock interview interface with simulated video/audio.
- **AI Chat**: Context-aware Q&A (Mocked).

## License

MIT

