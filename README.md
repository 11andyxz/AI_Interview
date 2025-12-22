# AI Interview Master

A full-stack application for simulating technical interviews with an AI-powered interviewer. The system provides intelligent interview question generation, real-time conversation, resume analysis, and comprehensive evaluation reports.

## Tech Stack

- **Frontend**: React 18, Tailwind CSS, Lucide React, React Router
- **Backend**: Java 17, Spring Boot 3.2, Spring WebFlux
- **Database**: MySQL 8.0
- **AI Integration**: OpenAI GPT-3.5-turbo (with support for custom models)
- **Authentication**: JWT-based authentication
- **Real-time**: Server-Sent Events (SSE) for streaming responses

## Project Structure

```
ai-interview-project/
├── backend/          # Spring Boot Application
│   ├── src/main/java/com/aiinterview/
│   │   ├── controller/    # REST API endpoints
│   │   ├── service/       # Business logic
│   │   ├── model/         # Data models
│   │   ├── repository/    # Data access layer
│   │   └── config/        # Configuration
│   └── src/main/resources/
│       ├── application.properties
│       ├── schema.sql      # Database schema
│       └── data.sql        # Seed data
├── frontend/         # React Application
│   ├── src/
│   │   ├── components/    # React components
│   │   ├── hooks/         # Custom hooks
│   │   └── utils/         # Utilities
│   └── e2e/               # End-to-end tests (Playwright)
└── docs/             # Documentation
    ├── system_flow.md
    ├── ai_endpoints_io.md
    └── model_evaluation_*.md
```

## Prerequisites

- Java 17 or higher
- Maven 3.6+
- Node.js 16+ and npm
- MySQL 8.0+ (or access to remote MySQL database)
- OpenAI API key (stored in database)

## Getting Started

### 1. Database Setup

The application uses a remote MySQL database. Database connection is configured in `backend/src/main/resources/application.properties`.

The database schema and seed data are automatically loaded from:
- `backend/src/main/resources/schema.sql`
- `backend/src/main/resources/data.sql`

### 2. Backend Setup

1. Navigate to `backend` directory:
   ```bash
   cd ai-interview-project/backend
   ```

2. Configure OpenAI API key (optional - can be set via database):
   - The API key can be configured in the database `api_key_config` table
   - Or set environment variable: `OPENAI_API_KEY=your-key-here`

3. Run the application:
   ```bash
   mvn spring-boot:run
   ```
   Server starts at `http://localhost:8080`.

### 3. Frontend Setup

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

## Default Test Account

- **Username**: `test`
- **Password**: `123456`

## Features

### Core Features

- **Dashboard**: View interview history, statistics, and performance metrics
- **Interview Room**: Real-time AI-powered interview interface with:
  - Streaming question generation (SSE)
  - Context-aware follow-up questions
  - Multi-turn conversation support
  - Audio recording capabilities
- **Resume Analysis**: Upload and analyze resumes with AI-powered insights
- **Mock Interviews**: Practice interviews with hints and retry options
- **Progress Tracking**: Track skill improvement over time
- **Knowledge Base**: Customizable interview knowledge base
- **Custom Question Sets**: Create and manage custom interview questions
- **Interview Reports**: Comprehensive evaluation reports with scoring

### Interview Types

1. **General Interview**: Based on candidate profiles
   - Select from predefined candidates (Backend, Frontend, Full Stack, AI, ML Engineer)
   - Customize position type and programming languages
   - AI generates questions based on candidate background

2. **Resume-based Interview**: Based on uploaded resumes
   - Upload and analyze resume
   - AI extracts skills, experience, and tech stack
   - Auto-fills interview parameters from resume analysis

### API Endpoints

Key endpoints:
- `POST /api/auth/login` - User authentication
- `GET /api/resume/candidates` - Get candidate list
- `POST /api/interviews` - Create new interview
- `POST /api/interviews/{id}/chat` - Chat with AI interviewer
- `GET /api/interviews/{id}/report` - Get interview report
- `POST /api/user/resume` - Upload resume
- `POST /api/user/resume/{id}/analyze` - Analyze resume

See `docs/ai_endpoints_io.md` for complete API documentation.

## Development

### Running Tests

**Backend Tests:**
```bash
cd ai-interview-project/backend
mvn test
```

**Frontend E2E Tests:**
```bash
cd ai-interview-project/frontend
npm run test:e2e
```

### Project Documentation

- System Architecture: `docs/system_flow.md`
- API Documentation: `docs/ai_endpoints_io.md`
- Database Schema: `docs/db_schema_design.md`
- Model Evaluation: `docs/model_evaluation_*.md`

## Configuration

### Backend Configuration

Edit `backend/src/main/resources/application.properties`:

```properties
# Database
spring.datasource.url=jdbc:mysql://your-host:3306/ai_interview
spring.datasource.username=your-username
spring.datasource.password=your-password

# OpenAI
openai.model=gpt-3.5-turbo
openai.temperature=0.7
openai.max-tokens=1000
```

### Frontend Configuration

The frontend connects to `http://localhost:8080` by default. Update API endpoints in `frontend/src/utils/api.js` if needed.

## License

MIT

