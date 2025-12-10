# Database Schema Design (Initial Proposal)

This document outlines an initial proposal for database tables that can support the AI Interview system.  
The focus is on core entities needed for managing interview sessions, storing conversation history, and saving AI feedback and résumé summaries.

> Note: This is a design document only. It does not change the current database yet.

---

## 1. Design goals and assumptions

- Support multiple interview sessions per user.
- Keep a full history of questions and answers for each session.
- Store AI-generated feedback and résumé summaries so they can be reviewed later.
- Stay compatible with the existing `user` table (already present in the current MySQL database).

---

## 2. Core tables for interview flow

### 2.1 `interview_session`

Represents one complete interview session between the AI and a candidate.

**Suggested fields:**

- `id` (BIGINT, PK)  
  Unique identifier for the session.

- `user_id` (BIGINT, nullable, FK → `user.id`)  
  Links to the logged-in user if available. Can be null for anonymous sessions.

- `role` (VARCHAR(100))  
  Target role for this interview, e.g. `"Backend Java"`, `"Frontend React"`, `"Data Scientist"`.

- `level` (VARCHAR(50), nullable)  
  Optional level such as `"Junior"`, `"Mid"`, `"Senior"`.

- `status` (VARCHAR(20))  
  Session status, e.g. `"active"`, `"completed"`, `"aborted"`.

- `started_at` (DATETIME)  
  When the interview session started.

- `ended_at` (DATETIME, nullable)  
  When the interview session ended (if completed or aborted).

- `total_score` (DECIMAL(5,2), nullable)  
  Optional overall score assigned to the session (if we support scoring later).

**Relationships:**

- One `interview_session` **has many** `session_message` records.
- One `interview_session` **may have one** `session_feedback` record.
- One `interview_session` **may be linked to one** `resume_record`.

---

### 2.2 `session_message` (or `interview_message`)

Stores the detailed conversation history (questions and answers) for each session.

**Suggested fields:**

- `id` (BIGINT, PK)

- `session_id` (BIGINT, FK → `interview_session.id`)  
  The interview session this message belongs to.

- `sequence_number` (INT)  
  The order of this message within the session (1, 2, 3, …).

- `speaker` (VARCHAR(20))  
  `"AI"` or `"CANDIDATE"` (can be extended later if needed).

- `message_type` (VARCHAR(20))  
  For example: `"question"`, `"answer"`, `"system"`.

- `content` (TEXT)  
  The actual text of the question or answer.

- `created_at` (DATETIME)  
  Timestamp when this message was created.

- `score` (DECIMAL(4,2), nullable)  
  Optional score for this answer (e.g., if the AI evaluates each answer).

**Relationships:**

- Many `session_message` records **belong to** one `interview_session`.

---

### 2.3 `session_feedback`

Stores the AI’s final feedback for a completed session.

**Suggested fields:**

- `id` (BIGINT, PK)

- `session_id` (BIGINT, FK → `interview_session.id`, unique)  
  One-to-one relationship: one feedback per session.

- `overall_rating` (INT, nullable)  
  Overall numeric rating (e.g., 1–5).

- `strengths` (TEXT)  
  Summary of the candidate’s strengths.

- `weaknesses` (TEXT)  
  Summary of the candidate’s weaknesses or risk areas.

- `recommendations` (TEXT)  
  Suggested next steps, improvements, or learning paths.

- `created_at` (DATETIME)

**Relationships:**

- One `session_feedback` **belongs to** one `interview_session`.

---

## 3. Supporting tables

### 3.1 `resume_record`

Stores the raw résumé text, optional job description, and AI-generated summary.

**Suggested fields:**

- `id` (BIGINT, PK)

- `user_id` (BIGINT, nullable, FK → `user.id`)  
  Optional link to the user who uploaded the résumé.

- `session_id` (BIGINT, nullable, FK → `interview_session.id`)  
  Optional link if the résumé was used for a specific interview session.

- `resume_text` (LONGTEXT)  
  Raw résumé content.

- `job_description` (LONGTEXT, nullable)  
  Optional job description used for context.

- `summary_text` (TEXT, nullable)  
  AI-generated résumé summary.

- `created_at` (DATETIME)

**Relationships:**

- One `resume_record` can be linked to zero or one `interview_session`.
- It can also exist independently if the user only uses the résumé summarization feature.

---

## 4. Future extension: knowledge base tables (optional)

Currently, the knowledge base (roles, questions, rubrics) is handled via JSON files and `KnowledgeBaseService`.  
If we decide to move this into the database later, we can consider tables like:

### 4.1 `kb_role`

- `id` (BIGINT, PK)
- `name` (VARCHAR(100)) — e.g., `"Backend Java"`, `"Frontend React"`.
- `level` (VARCHAR(50), nullable)
- `tags` (VARCHAR(255), nullable)

### 4.2 `kb_skill`

- `id` (BIGINT, PK)
- `name` (VARCHAR(100)) — e.g., `"Java Basics"`, `"REST APIs"`, `"System Design"`.
- `description` (TEXT, nullable)

### 4.3 `kb_question`

- `id` (BIGINT, PK)
- `role_id` (BIGINT, FK → `kb_role.id`)
- `text` (TEXT)
- `type` (VARCHAR(50)) — `"technical"`, `"behavioral"`, `"system_design"`, etc.
- `difficulty` (INT)
- `skill_tags` (VARCHAR(255), nullable) — simple comma-separated list for now.

### 4.4 `kb_rubric`

- `id` (BIGINT, PK)
- `question_id` (BIGINT, FK → `kb_question.id`)
- `level` (VARCHAR(20)) — `"excellent"`, `"average"`, `"poor"`.
- `description` (TEXT) — what a response at this level typically looks like.

> These tables are **optional** for later phases. For now, JSON-based knowledge is enough, but this schema gives us a clear path if we decide to persist the knowledge base in MySQL.

---

## 5. Summary

- The **core flow** is centered around `interview_session` and `session_message`, with `session_feedback` providing a structured summary.
- `resume_record` allows us to store résumé-related data and AI-generated summaries.
- Future **knowledge base tables** (`kb_role`, `kb_skill`, `kb_question`, `kb_rubric`) can replace or complement the current JSON-based approach if needed.

This design is intended as a starting point and can be refined once we have more clarity on the product requirements and usage patterns.
