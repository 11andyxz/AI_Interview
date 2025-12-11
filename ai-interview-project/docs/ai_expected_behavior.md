# AI Test Cases for Interview Master

This document describes expected AI behaviors for resume analysis, interview question generation, and conversational follow-up.  
These tests do not call the real API — they define **expected behaviors** for backend & frontend validation.

---

## 1. Resume Summary Test

### Purpose
Verify that the system can summarize a resume and extract key skills, weaknesses, and recommended interview questions.

### Input (Resume Text)
```
Experienced software engineer skilled in Java, Python, and cloud infrastructure.
Worked on backend systems and scalable microservices.
```

### Expected Output

#### Summary
The candidate is a backend-focused software engineer experienced in Java, Python, and distributed systems.

#### Skills
- Java
- Python
- Cloud infrastructure
- Microservices
- Backend architecture

#### Weaknesses
- Limited mention of frontend technologies
- No mention of ML/AI experience

#### Recommended Questions
1. Describe a microservices architecture you built.
2. How did you ensure scalability under high load?
3. What cloud platforms have you used and why?

---

## 2. Follow-Up Question Test

### Purpose
Verify that the system generates a follow-up question based on the user's answer.

### User Answer
```
I designed a machine learning pipeline that automated feature extraction and improved model accuracy by 12%.
```

### Expected Follow-Up Question
```
Can you explain how you optimized the model performance and what techniques contributed most to the improvement?
```

---

## 3. Behavioral Question Test

### System Prompt
```
Tell me about a time you handled a difficult challenge at work.
```

### Expected Output
```
What was the situation, what actions did you take, and what was the outcome?
```

---

## 4. Final Interview Summary Test

### Purpose
Verify that the system can generate a structured summary at the end of a mock interview.

### Expected Output

#### Overall
Strong technical knowledge, clear communication, and solid backend experience.

#### Strengths
- Clear explanations
- Strong systems design knowledge
- Practical cloud experience

#### Weaknesses
- Needs improvement in behavioral depth
- Limited cross-functional collaboration examples

#### Recommendations
- Practice the STAR framework
- Prepare more system-failure scenarios
- Strengthen ML familiarity if applying to AI-adjacent roles
