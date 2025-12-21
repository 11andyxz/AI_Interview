# Model Evaluation Prompt Set (Baseline)

## Overview

This document defines a fixed set of representative prompts used to evaluate
LLM performance for resume analysis and interview assistance.

The purpose of this prompt set is to establish a stable internal baseline for
comparing:
- Current OpenAI-based model performance
- Future in-house or alternative model implementations

This file defines **what is tested** and **what good looks like**.
It does NOT contain metrics, logs, or evaluation results.

---

## Prompt Categories

1. Resume Summarization
2. Resume Improvement & Weakness Analysis
3. Behavioral Interview Q&A
4. Technical Interview Q&A

---

## Shared Evaluation Criteria

Each response should be evaluated against the following dimensions:

- **Relevance**: Directly addresses the prompt without hallucination
- **Completeness**: Covers the key aspects implied by the prompt
- **Clarity**: Well-structured, readable, and logically organized
- **Usefulness**: Provides actionable or insightful content
- **Tone & Professionalism**: Appropriate for interview or hiring context

---

## Prompt Set

### A. Resume Summary & Analysis Prompts (10)

**RS-01**  
Prompt:  
Summarize the following resume for a backend software engineer role.

Input:  
[Resume text: 3-5 years Java, Spring Boot, REST APIs, limited system design]

Expected Qualities:
- Accurately reflects experience level
- Highlights core backend technologies
- Avoids overstating architecture ownership

---

**RS-02**  
Prompt:  
Provide a concise professional summary suitable for a hiring manager review.

Input:  
[Resume text: mixed frontend/backend experience, unclear role focus]

Expected Qualities:
- Clear role positioning
- Balanced skill representation
- Professional tone

---

**RS-03**  
Prompt:  
Identify the key strengths in this resume.

Input:  
[Resume text: strong internships, weak full-time experience]

Expected Qualities:
- Emphasizes transferable skills
- Avoids dismissive language
- Honest but constructive framing

---

**RS-04**  
Prompt:  
What are the main weaknesses or gaps in this resume?

Input:  
[Resume text: good coding skills, little leadership or ownership]

Expected Qualities:
- Specific and concrete feedback
- Actionable improvement suggestions
- Non-judgmental tone

---

**RS-05**  
Prompt:  
Rewrite this resume summary to better match a senior engineer role.

Input:  
[Resume text: mid-level engineer overstating impact]

Expected Qualities:
- Aligns wording with realistic senior expectations
- Reduces exaggeration
- Maintains confidence

---

**RS-06**  
Prompt:  
Extract the top 5 technical skills from this resume.

Input:  
[Resume text: long skills section with mixed relevance]

Expected Qualities:
- Filters to role-relevant skills
- Avoids listing trivial tools
- Clear prioritization

---

**RS-07**  
Prompt:  
Summarize this resume for a non-technical recruiter.

Input:  
[Resume text: highly technical, jargon-heavy]

Expected Qualities:
- Simplifies technical language
- Focuses on impact and responsibilities
- Maintains accuracy

---

**RS-08**  
Prompt:  
Does this resume indicate readiness for system design interviews? Why or why not?

Input:  
[Resume text: mostly CRUD work]

Expected Qualities:
- Clear reasoning
- Evidence-based assessment
- Avoids absolute judgments

---

**RS-09**  
Prompt:  
Suggest 3 resume improvements to increase interview callbacks.

Input:  
[Resume text: solid content, weak presentation]

Expected Qualities:
- Practical suggestions
- Focus on structure and clarity
- No unnecessary verbosity

---

**RS-10**  
Prompt:  
Create a 3-4 sentence elevator pitch based on this resume.

Input:  
[Resume text: early-career engineer]

Expected Qualities:
- Concise and confident
- Clear value proposition
- Appropriate seniority framing

---

### B. Interview Q&A Prompts (10)

**IQ-01 (Behavioral)**  
Prompt:  
Tell me about a time you faced a conflict within a team.

Expected Qualities:
- STAR-style structure
- Demonstrates accountability
- Clear resolution

---

**IQ-02 (Behavioral)**  
Prompt:  
Describe a project that did not go as planned. What did you learn?

Expected Qualities:
- Honest reflection
- Learning-focused response
- Avoids blaming others

---

**IQ-03 (Behavioral)**  
Prompt:  
How do you handle ambiguous requirements?

Expected Qualities:
- Structured reasoning
- Communication emphasis
- Real-world examples

---

**IQ-04 (Technical)**  
Prompt:  
Explain how REST APIs work to a junior developer.

Expected Qualities:
- Clear, simple explanation
- Correct fundamentals
- No unnecessary complexity

---

**IQ-05 (Technical)**  
Prompt:  
How would you design a scalable interview scheduling system?

Expected Qualities:
- High-level architecture thinking
- Identifies key components
- Avoids deep implementation detail

---

**IQ-06 (Technical)**  
Prompt:  
What are the trade-offs between SQL and NoSQL databases?

Expected Qualities:
- Balanced comparison
- Appropriate use cases
- Avoids absolutist answers

---

**IQ-07 (Technical)**  
Prompt:  
How do you approach debugging a production issue?

Expected Qualities:
- Systematic approach
- Emphasis on impact and safety
- Clear prioritization

---

**IQ-08 (Behavioral)**  
Prompt:  
How do you handle feedback you disagree with?

Expected Qualities:
- Professional maturity
- Open-mindedness
- Constructive response

---

**IQ-09 (Technical)**  
Prompt:  
What causes performance issues in web applications?

Expected Qualities:
- Covers common bottlenecks
- Clear categorization
- Practical perspective

---

**IQ-10 (Behavioral)**  
Prompt:  
Why are you interested in this role?

Expected Qualities:
- Role-specific motivation
- Avoids generic answers
- Aligns skills with role needs

---

### MS-01 (Technical Interview Flow)

Conversation Start Prompt:

Hello Andy Zhang! I'm your AI interviewer today.  
We'll be focusing on your background in backend engineering.  
Ready to begin?

Conversation Characteristics:
- Interviewer greeting and readiness confirmation
- Sequenced technical questions (e.g. Spring, distributed systems, databases)
- Follow-up questions that reference prior answers
- User may answer freely between turns
- Session ends when the user explicitly asks to stop

Expected Qualities:
- Maintains interviewer role throughout the session
- Asks logically ordered and coherent questions
- References prior answers appropriately
- Avoids repetition or abrupt topic shifts
- Natural and professional opening and closing

---

### MS-02 (Behavioral Interview Flow)

Conversation Start Prompt:

Hi Andy, let's begin with a few behavioral questions about your past experience.
Are you ready?

Expected Qualities:
- Consistent interviewer tone
- STAR-style probing follow-up questions
- Encourages reflection without leading or judging
- Maintains conversational continuity

---

### MS-03 (Early Termination Handling)

Conversation Start Prompt:

Hello Andy, I'm your AI interviewer today. Ready to start?

User Interrupt:
Thanks, let's stop here.

Expected Qualities:
- Stops asking further questions immediately
- Responds politely and professionally
- Does not attempt to continue the interview
- Graceful and natural termination

---


## Qualitative Scoring Guidance

Responses may be rated on a 1-5 scale:

5 - Excellent: Clear, complete, and production-ready  
4 - Good: Minor gaps but acceptable  
3 - Fair: Adequate but generic or shallow  
2 - Poor: Significant omissions or confusion  
1 - Unacceptable: Irrelevant, incorrect, or failed response

---

## Notes

- Prompt wording should remain stable across evaluations.
- Prompt IDs should not be changed once used in benchmarking.
- This prompt set is model-agnostic and reusable across architectures.
