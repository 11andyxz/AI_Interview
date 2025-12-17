Resume Analysis: POST /api/resume/analyze takes {candidateId, positionType, programmingLanguages, language} and returns knowledge base with questions, skills, and rubrics.

Interview Chat: POST /api/interviews/{id}/chat accepts message/answer and returns AI follow-up question or response text.

Answer Scoring: POST /api/llm/eval takes {question, answer, rubric, roleId, level} and returns detailed scores (0–1), rubric level (excellent/average/poor), strengths/improvements, and follow-up suggestions.

Question Generation: POST /api/llm/question-generate accepts {sessionId, roleId, level, candidateInfo} and returns LLM-generated question; GET /api/llm/question-generate/stream with query params streams same via SSE.

Session Management: POST /api/sessions creates session; GET /api/interviews/{id}/session returns Q&A history; GET /api/interviews/{id}/history returns all QA pairs.

All AI endpoints call OpenAI gpt-3.5-turbo except resume analysis, which currently returns template data (mocked).