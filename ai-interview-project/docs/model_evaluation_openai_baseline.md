# Model Evaluation Report - OpenAI Baseline (Dec 2025)

## Scope
This report evaluates the current OpenAI-based interview assistant using a fixed internal prompt baseline, with a focus on multi-turn technical interview behavior and conversational quality.

The evaluation is qualitative and based on live interaction with the system.

## Test Scenario
- **Scenario**: Multi-turn technical interview session  
- **Prompt types**: MS-01 (Technical Interview Flow), MS-03 (Early Termination)  
- **Conversation characteristics**:
  - Sequential technical questions (Spring -> distributed systems -> databases)
  - User-provided answers between turns
  - Explicit early termination by the user

## Observed Performance (OpenAI - GPT-3.5)

### Strengths
- Maintains interviewer role consistently across turns
- Logical progression of technical topics
- Questions are relevant to backend engineering roles
- Graceful handling of early termination
- Professional and neutral tone throughout the session

### Limitations
- Initial greeting and readiness confirmation is slightly repetitive
- Follow-up questions reference prior topics but not specific user phrasing
- No explicit scoring or structured feedback during the interview

## Metrics Observability (Current vs Future)
- **Response time & failures**: only observable qualitatively via UI behavior and backend logs
- **Token usage**: not exposed at the application level
- **User feedback**: not currently captured

## Acceptance Criteria for an In-House Model
For an in-house model replacement, acceptance criteria include maintaining comparable interview quality while enabling basic quantitative metrics such as **response latency**, **token usage**, **failure rate**, and **simple user ratings** to support objective benchmarking.
