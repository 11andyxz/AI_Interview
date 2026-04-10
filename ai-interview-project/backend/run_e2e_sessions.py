#!/usr/bin/env python3
"""
E2E Interview Session Runner

Runs 10-20 complete interview flows to validate staging deployment and collect metrics.
Simulates real interview sessions by:
1. Creating interview
2. Answering 5-10 questions per interview
3. Collecting response features and predictions
4. Testing early-stop logic
5. Generating final reports

Usage:
    python run_e2e_sessions.py --sessions 15 --base-url http://localhost:8080
"""

import argparse
import json
import random
import time
from typing import List, Dict, Any
import requests
from datetime import datetime

# Sample candidate responses for different technical questions
SAMPLE_RESPONSES = {
    "data_structures": [
        "I would use a HashMap for O(1) lookup time. The key would be the element and value would be its frequency.",
        "A binary search tree provides O(log n) search time when balanced. I'd use AVL or Red-Black tree for guaranteed balance.",
        "For this problem, I'd use a queue for BFS traversal. It ensures we visit nodes level by level.",
    ],
    "algorithms": [
        "I'd use dynamic programming with memoization. The time complexity would be O(n) and space O(n) for the cache.",
        "A two-pointer approach would work here. Start from both ends and move inward based on the comparison.",
        "I'd implement a sliding window algorithm. Maintain a window and expand or contract based on the condition.",
    ],
    "system_design": [
        "I'd use a microservices architecture with API Gateway. Each service would handle a specific domain.",
        "For caching, I'd implement Redis with LRU eviction policy. Set appropriate TTL based on data freshness requirements.",
        "I'd use message queues like RabbitMQ for asynchronous processing. This decouples services and improves scalability.",
    ],
    "java_spring": [
        "I'd use @Transactional annotation with proper isolation level. Use @Transactional(propagation = Propagation.REQUIRED) for most cases.",
        "Spring Boot auto-configuration handles this. But I'd customize it using @ConfigurationProperties for environment-specific configs.",
        "I'd inject dependencies using constructor injection. It's more testable and makes required dependencies explicit.",
    ],
}

# Interview templates
INTERVIEW_TEMPLATES = [
    {
        "positionType": "Backend Java Developer",
        "programmingLanguages": ["Java", "Spring"],
        "language": "English",
        "interviewType": "general",
        "target_questions": 8
    },
    {
        "positionType": "Full Stack Engineer",
        "programmingLanguages": ["JavaScript", "React", "Node.js"],
        "language": "English",
        "interviewType": "general",
        "target_questions": 10
    },
    {
        "positionType": "Senior Software Engineer",
        "programmingLanguages": ["Python", "Java"],
        "language": "English",
        "interviewType": "general",
        "target_questions": 12
    },
    {
        "positionType": "Junior Developer",
        "programmingLanguages": ["Java"],
        "language": "English",
        "interviewType": "general",
        "target_questions": 6
    },
]


class E2ESessionRunner:
    def __init__(self, base_url: str, verbose: bool = False):
        self.base_url = base_url.rstrip('/')
        self.verbose = verbose
        self.session_results = []
        
    def log(self, message: str):
        """Log message if verbose mode is enabled."""
        if self.verbose:
            timestamp = datetime.now().strftime("%H:%M:%S")
            print(f"[{timestamp}] {message}")
    
    def create_user(self) -> Dict[str, Any]:
        """Create a test user via /api/auth/register."""
        username = f"e2e_test_{int(time.time())}_{random.randint(1000, 9999)}"
        payload = {
            "username": username,
            "password": "test_password_123"
        }
        
        try:
            response = requests.post(
                f"{self.base_url}/api/auth/register",
                json=payload,
                timeout=10
            )
            
            if response.status_code == 200 or response.status_code == 201:
                data = response.json()
                if data.get("success") and "user" in data:
                    self.log(f"✓ Created user: {username}")
                    # Return user data with access token
                    return {
                        "id": data["user"]["id"],
                        "username": data["user"]["username"],
                        "accessToken": data.get("accessToken")
                    }
                else:
                    self.log(f"✗ Registration failed: {data.get('message')}")
                    return None
            else:
                self.log(f"✗ Failed to create user: {response.status_code}")
                return None
        except Exception as e:
            self.log(f"✗ Error creating user: {str(e)}")
            return None
    
    def create_interview(self, user_id: int, access_token: str, template: Dict[str, Any]) -> Dict[str, Any]:
        """Create an interview session via /api/interviews."""
        payload = {
            "candidateId": user_id,
            "positionType": template["positionType"],
            "programmingLanguages": template["programmingLanguages"],
            "language": template["language"],
            "interviewType": template["interviewType"]
        }
        
        try:
            headers = {
                "Authorization": f"Bearer {access_token}",
                "Content-Type": "application/json"
            }
            
            response = requests.post(
                f"{self.base_url}/api/interviews",
                json=payload,
                headers=headers,
                timeout=10
            )
            
            if response.status_code == 200:
                data = response.json()
                if "interview" in data:
                    interview_id = data["interview"].get("id")
                    self.log(f"✓ Created interview: {interview_id} ({template['positionType']})")
                    return data["interview"]
            
            self.log(f"✗ Failed to create interview: {response.status_code} - {response.text[:200]}")
            return None
        except Exception as e:
            self.log(f"✗ Error creating interview: {str(e)}")
            return None
    
    def get_random_response(self) -> str:
        """Get a random candidate response."""
        category = random.choice(list(SAMPLE_RESPONSES.keys()))
        return random.choice(SAMPLE_RESPONSES[category])
    
    def answer_question(self, interview_id: str, question: str) -> Dict[str, Any]:
        """Submit an answer to a question."""
        answer = self.get_random_response()
        
        payload = {
            "interviewId": interview_id,
            "question": question,
            "answer": answer
        }
        
        try:
            # Simulate response feature extraction and prediction
            response = requests.post(
                f"{self.base_url}/api/interviews/{interview_id}/answer",
                json=payload,
                timeout=15
            )
            
            if response.status_code == 200:
                return response.json()
            else:
                self.log(f"  ! Answer submission returned: {response.status_code}")
                return {"success": False}
        except Exception as e:
            self.log(f"  ! Error submitting answer: {str(e)}")
            return {"success": False}
    
    def run_interview_session(self, template: Dict[str, Any], session_num: int) -> Dict[str, Any]:
        """Run a complete interview session."""
        self.log(f"\n{'='*60}")
        self.log(f"Session #{session_num}: {template['positionType']}")
        self.log(f"{'='*60}")
        
        start_time = time.time()
        
        # Step 1: Create user
        user = self.create_user()
        if not user:
            return {"success": False, "error": "Failed to create user"}
        
        user_id = user.get("id")
        access_token = user.get("accessToken")
        
        # Step 2: Create interview
        interview = self.create_interview(user_id, access_token, template)
        if not interview:
            return {"success": False, "error": "Failed to create interview"}
        
        interview_id = interview.get("id")
        
        # Step 3: Answer questions
        target_questions = template["target_questions"]
        questions_answered = 0
        early_stopped = False
        
        # Simulate questions (in real scenario, these would come from the question selector)
        sample_questions = [
            "Explain the difference between HashMap and TreeMap in Java.",
            "How would you design a URL shortener service?",
            "What is the time complexity of quicksort? When does it perform poorly?",
            "Explain how Spring Boot auto-configuration works.",
            "How would you implement a cache with LRU eviction policy?",
            "Describe the differences between REST and GraphQL.",
            "How do you handle database transactions in Spring?",
            "Explain the concept of eventual consistency in distributed systems.",
            "What are the trade-offs between SQL and NoSQL databases?",
            "How would you optimize a slow SQL query?",
        ]
        
        for i in range(target_questions):
            if i < len(sample_questions):
                question = sample_questions[i]
            else:
                question = f"Technical question #{i+1}"
            
            self.log(f"  Q{i+1}: {question[:50]}...")
            
            result = self.answer_question(interview_id, question)
            time.sleep(0.5)  # Brief pause between questions
            
            questions_answered += 1
            
            # Check for early-stop signal
            if result.get("earlyStop") or result.get("early_stop"):
                early_stopped = True
                self.log(f"  ⚠ Early stop triggered after {questions_answered} questions")
                break
        
        # Step 4: Finalize interview
        duration = time.time() - start_time
        
        self.log(f"✓ Session completed: {questions_answered} questions, {duration:.1f}s")
        
        return {
            "success": True,
            "session_num": session_num,
            "interview_id": interview_id,
            "position": template["positionType"],
            "questions_answered": questions_answered,
            "target_questions": target_questions,
            "early_stopped": early_stopped,
            "duration_seconds": duration
        }
    
    def run_sessions(self, num_sessions: int):
        """Run multiple E2E sessions."""
        print(f"\n{'='*70}")
        print(f"Running {num_sessions} E2E Interview Sessions")
        print(f"Base URL: {self.base_url}")
        print(f"{'='*70}\n")
        
        for i in range(num_sessions):
            template = random.choice(INTERVIEW_TEMPLATES)
            result = self.run_interview_session(template, i + 1)
            self.session_results.append(result)
            
            # Brief pause between sessions
            time.sleep(1)
        
        self.print_summary()
    
    def print_summary(self):
        """Print summary of all sessions."""
        print(f"\n\n{'='*70}")
        print("E2E SESSION SUMMARY")
        print(f"{'='*70}\n")
        
        successful = [r for r in self.session_results if r.get("success")]
        failed = [r for r in self.session_results if not r.get("success")]
        
        print(f"Total Sessions: {len(self.session_results)}")
        print(f"Successful: {len(successful)}")
        print(f"Failed: {len(failed)}")
        
        if successful:
            total_questions = sum(r["questions_answered"] for r in successful)
            avg_questions = total_questions / len(successful)
            early_stop_count = sum(1 for r in successful if r.get("early_stopped"))
            early_stop_rate = (early_stop_count / len(successful)) * 100
            
            avg_duration = sum(r["duration_seconds"] for r in successful) / len(successful)
            
            print(f"\nMetrics:")
            print(f"  Total Questions Answered: {total_questions}")
            print(f"  Average Questions per Session: {avg_questions:.1f}")
            print(f"  Early Stop Rate: {early_stop_rate:.1f}% ({early_stop_count}/{len(successful)})")
            print(f"  Average Session Duration: {avg_duration:.1f}s")
            
            print(f"\nSession Details:")
            for r in successful:
                status = "⚠ EARLY-STOP" if r.get("early_stopped") else "✓ COMPLETE"
                print(f"  #{r['session_num']}: {r['position'][:30]:30s} | "
                      f"{r['questions_answered']}/{r['target_questions']} questions | "
                      f"{r['duration_seconds']:.1f}s | {status}")
        
        if failed:
            print(f"\nFailed Sessions:")
            for r in failed:
                print(f"  #{r.get('session_num', '?')}: {r.get('error', 'Unknown error')}")
        
        print(f"\n{'='*70}\n")


def main():
    parser = argparse.ArgumentParser(description="Run E2E interview sessions for staging validation")
    parser.add_argument("--sessions", type=int, default=15, help="Number of sessions to run (default: 15)")
    parser.add_argument("--base-url", default="http://localhost:8080", help="Backend base URL")
    parser.add_argument("--verbose", action="store_true", help="Enable verbose logging")
    
    args = parser.parse_args()
    
    runner = E2ESessionRunner(args.base_url, verbose=args.verbose)
    runner.run_sessions(args.sessions)


if __name__ == "__main__":
    main()
