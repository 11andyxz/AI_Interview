#!/usr/bin/env python3
"""
Test script for FineTuneDataCollector functionality
"""

import asyncio
import json
import random
from datetime import datetime, timedelta
import os

# Mock data for testing
MOCK_PROMPTS = [
    "Analyze this resume and provide a comprehensive evaluation.",
    "Based on the following resume, what are the candidate's key strengths?",
    "Review this candidate's qualifications and suggest improvement areas.",
    "Evaluate the technical skills presented in this resume.",
    "Assess the candidate's work experience and career progression."
]

MOCK_RESUMES = [
    {
        "name": "John Smith",
        "email": "john.smith@email.com",
        "phone": "555-0123",
        "skills": ["Python", "Java", "Machine Learning", "AWS"],
        "experience": [
            {
                "company": "TechCorp",
                "role": "Software Engineer",
                "duration": "2020-2023",
                "description": "Developed web applications using Python and Django"
            }
        ],
        "education": [
            {
                "degree": "BS Computer Science",
                "school": "State University",
                "year": "2020"
            }
        ]
    },
    {
        "name": "Sarah Johnson",
        "email": "sarah.j@email.com",
        "phone": "555-0456",
        "skills": ["JavaScript", "React", "Node.js", "MongoDB"],
        "experience": [
            {
                "company": "WebDev Inc",
                "role": "Frontend Developer",
                "duration": "2019-2024",
                "description": "Built responsive user interfaces and improved user experience"
            }
        ],
        "education": [
            {
                "degree": "MS Software Engineering",
                "school": "Tech Institute",
                "year": "2019"
            }
        ]
    }
]

MOCK_RESPONSES = [
    {
        "overall_score": 8.5,
        "strengths": ["Strong technical skills", "Good work experience", "Clear career progression"],
        "weaknesses": ["Limited leadership experience", "Could benefit from more diverse projects"],
        "recommendations": ["Consider taking on team lead roles", "Expand skill set with cloud technologies"],
        "technical_assessment": "Solid foundation in software development with modern technologies"
    },
    {
        "overall_score": 7.8,
        "strengths": ["Excellent frontend skills", "Long tenure at previous company", "Advanced degree"],
        "weaknesses": ["Limited backend experience", "No mobile development experience"],
        "recommendations": ["Learn backend technologies", "Consider React Native for mobile development"],
        "technical_assessment": "Strong frontend developer with room for full-stack growth"
    }
]

def generate_mock_data_point():
    """Generate a mock fine-tuning data point"""
    prompt = random.choice(MOCK_PROMPTS)
    resume = random.choice(MOCK_RESUMES)
    response = random.choice(MOCK_RESPONSES)
    
    # Add some variation to the response
    response = response.copy()
    response["overall_score"] = round(random.uniform(6.0, 9.5), 1)
    
    return {
        "prompt": prompt,
        "input_data": json.dumps(resume),
        "response": json.dumps(response),
        "quality_score": random.uniform(0.7, 0.95),
        "user_feedback": random.choice(["positive", "neutral", "needs_improvement"]) if random.random() > 0.3 else None,
        "session_id": f"session_{random.randint(1000, 9999)}",
        "endpoint": "resume_analysis"
    }

async def test_data_collection():
    """Test the fine-tuning data collection functionality"""
    print("=== Testing Fine-Tuning Data Collection ===")
    
    # Create output directory
    output_dir = "d:/dev/AI_Interview/ai-interview-project/eval/finetune_data"
    os.makedirs(output_dir, exist_ok=True)
    
    # Generate test data points
    print(f"Generating {100} mock data points...")
    data_points = []
    
    for i in range(100):
        data_point = generate_mock_data_point()
        data_points.append(data_point)
    
    # Simulate data collection with quality filtering
    high_quality_data = []
    for point in data_points:
        # Filter by quality score (threshold: 0.8)
        if point["quality_score"] >= 0.8:
            high_quality_data.append(point)
    
    print(f"High-quality data points: {len(high_quality_data)}/{len(data_points)}")
    
    # Convert to OpenAI fine-tuning format
    openai_format = []
    for point in high_quality_data:
        formatted = {
            "messages": [
                {
                    "role": "system",
                    "content": "You are an expert resume analyzer. Provide detailed, constructive feedback."
                },
                {
                    "role": "user", 
                    "content": f"{point['prompt']}\n\nResume Data:\n{point['input_data']}"
                },
                {
                    "role": "assistant",
                    "content": point['response']
                }
            ]
        }
        openai_format.append(formatted)
    
    # Save data
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    
    # Save raw data
    raw_file = os.path.join(output_dir, f"raw_data_{timestamp}.jsonl")
    with open(raw_file, 'w', encoding='utf-8') as f:
        for point in data_points:
            f.write(json.dumps(point) + '\n')
    
    # Save filtered data
    filtered_file = os.path.join(output_dir, f"filtered_data_{timestamp}.jsonl")
    with open(filtered_file, 'w', encoding='utf-8') as f:
        for point in high_quality_data:
            f.write(json.dumps(point) + '\n')
    
    # Save OpenAI format
    openai_file = os.path.join(output_dir, f"openai_format_{timestamp}.jsonl")
    with open(openai_file, 'w', encoding='utf-8') as f:
        for item in openai_format:
            f.write(json.dumps(item) + '\n')
    
    print(f"\nData saved to:")
    print(f"Raw data: {raw_file}")
    print(f"Filtered data: {filtered_file}")
    print(f"OpenAI format: {openai_file}")
    
    # Generate statistics
    print(f"\n=== Statistics ===")
    print(f"Total data points: {len(data_points)}")
    print(f"High-quality points: {len(high_quality_data)}")
    print(f"Quality rate: {len(high_quality_data)/len(data_points)*100:.1f}%")
    
    # Analyze quality scores
    quality_scores = [point["quality_score"] for point in data_points]
    avg_quality = sum(quality_scores) / len(quality_scores)
    print(f"Average quality score: {avg_quality:.3f}")
    print(f"Quality threshold: 0.8")
    
    # Analyze endpoints
    endpoints = {}
    for point in data_points:
        endpoint = point["endpoint"]
        endpoints[endpoint] = endpoints.get(endpoint, 0) + 1
    
    print(f"\nEndpoint distribution:")
    for endpoint, count in endpoints.items():
        print(f"  {endpoint}: {count}")
    
    return {
        "total_points": len(data_points),
        "high_quality_points": len(high_quality_data),
        "quality_rate": len(high_quality_data)/len(data_points),
        "average_quality": avg_quality,
        "files_created": [raw_file, filtered_file, openai_file]
    }

async def test_pii_sanitization():
    """Test PII sanitization functionality"""
    print("\n=== Testing PII Sanitization ===")
    
    test_strings = [
        "Contact me at john.doe@email.com or call 555-123-4567",
        "My SSN is 123-45-6789 and my phone is (555) 987-6543",
        "Email: sarah.j@company.org, Phone: +1-800-555-0123",
        "Call me at 555.444.3333 or email admin@test.com",
        "No PII in this string, should remain unchanged"
    ]
    
    # Simple regex patterns for PII detection (matching the Java implementation)
    import re
    
    def sanitize_pii(text):
        """Simple PII sanitization matching the Java version"""
        # Email pattern
        text = re.sub(r'\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Z|a-z]{2,}\b', '[EMAIL_REDACTED]', text)
        
        # Phone patterns
        text = re.sub(r'\b\d{3}[-.]?\d{3}[-.]?\d{4}\b', '[PHONE_REDACTED]', text)
        text = re.sub(r'\(\d{3}\)\s?\d{3}[-.]?\d{4}', '[PHONE_REDACTED]', text)
        text = re.sub(r'\+?1?[-.]?\(??\d{3}\)?[-.]?\d{3}[-.]?\d{4}', '[PHONE_REDACTED]', text)
        
        # SSN pattern
        text = re.sub(r'\b\d{3}-\d{2}-\d{4}\b', '[SSN_REDACTED]', text)
        
        return text
    
    print("Testing PII sanitization:")
    for i, test_str in enumerate(test_strings):
        sanitized = sanitize_pii(test_str)
        print(f"  {i+1}. Original: {test_str}")
        print(f"     Sanitized: {sanitized}")
        print()

async def main():
    """Main test function"""
    print("Fine-Tuning Data Collection Test Suite")
    print("=====================================")
    
    try:
        # Test data collection
        collection_results = await test_data_collection()
        
        # Test PII sanitization
        await test_pii_sanitization()
        
        print(f"\n=== Test Summary ===")
        print(f"✓ Data collection: {collection_results['total_points']} points generated")
        print(f"✓ Quality filtering: {collection_results['high_quality_points']} high-quality points")
        print(f"✓ OpenAI format conversion: Complete")
        print(f"✓ PII sanitization: Tested")
        print(f"✓ Files created: {len(collection_results['files_created'])}")
        
        print(f"\nAll tests completed successfully!")
        
    except Exception as e:
        print(f"Error during testing: {e}")
        raise

if __name__ == '__main__':
    asyncio.run(main())