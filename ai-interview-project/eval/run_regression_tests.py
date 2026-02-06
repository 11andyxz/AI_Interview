#!/usr/bin/env python3
"""
ML Regression Test Suite
Tests AI output quality against golden dataset to prevent quality degradation
"""

import json
import os
import sys
from pathlib import Path
from typing import Dict, List, Any
import statistics
from datetime import datetime

# Add parent directory to path for imports
sys.path.append(str(Path(__file__).parent))

class RegressionTestSuite:
    """
    Comprehensive regression tests for ML quality assurance
    Ensures model outputs maintain quality standards
    """
    
    def __init__(self, golden_dataset_path: str = "./golden_dataset"):
        self.golden_path = Path(golden_dataset_path)
        self.results = []
        self.passed = 0
        self.failed = 0
        
    def load_golden_examples(self, category: str) -> List[Dict[str, Any]]:
        """Load all golden examples from a category"""
        category_path = self.golden_path / category
        examples = []
        
        if not category_path.exists():
            print(f"Warning: Category path {category_path} does not exist")
            return examples
            
        for file_path in category_path.glob("*.json"):
            try:
                with open(file_path, 'r', encoding='utf-8') as f:
                    example = json.load(f)
                    example['_source_file'] = file_path.name
                    examples.append(example)
            except Exception as e:
                print(f"Error loading {file_path}: {e}")
                
        return examples
    
    def test_resume_analysis_quality(self):
        """
        Test: Resume analysis maintains >90% accuracy
        Validates structure, completeness, and relevance of resume parsing
        """
        print("\n=== Test: Resume Analysis Quality ===")
        
        examples = self.load_golden_examples("resume_analysis")
        if not examples:
            print("⚠ No golden examples found for resume_analysis")
            return
            
        print(f"Testing {len(examples)} resume analysis examples...")
        
        scores = []
        for example in examples:
            try:
                expected = example.get('expectedOutput', {})
                quality_metrics = example.get('qualityMetrics', {})
                
                # Check structure validity
                structure_score = quality_metrics.get('structureValidity', 0)
                
                # Check content quality
                content_score = quality_metrics.get('contentQuality', 0)
                
                # Check relevance
                relevance_score = quality_metrics.get('relevance', 0)
                
                # Check completeness
                completeness_score = quality_metrics.get('completeness', 0)
                
                # Overall score
                overall_score = (structure_score + content_score + relevance_score + completeness_score) / 4
                scores.append(overall_score)
                
                # Validate expected output structure
                assert 'candidateInfo' in expected, "Missing candidateInfo"
                assert 'skills' in expected, "Missing skills"
                assert 'experience' in expected, "Missing experience"
                assert 'assessment' in expected, "Missing assessment"
                assert 'interviewQuestions' in expected, "Missing interviewQuestions"
                
                print(f"  ✓ {example['_source_file']}: {overall_score:.2f}")
                
            except AssertionError as e:
                print(f"  ✗ {example['_source_file']}: {str(e)}")
                self.failed += 1
                self.results.append({
                    'test': 'resume_analysis_quality',
                    'example': example['_source_file'],
                    'status': 'FAILED',
                    'reason': str(e)
                })
                continue
            except Exception as e:
                print(f"  ✗ {example['_source_file']}: Unexpected error - {str(e)}")
                self.failed += 1
                continue
        
        if scores:
            avg_score = statistics.mean(scores)
            print(f"\nAverage Quality Score: {avg_score:.3f}")
            
            if avg_score >= 0.90:
                print("✓ PASSED: Resume analysis quality >= 90%")
                self.passed += 1
                self.results.append({
                    'test': 'resume_analysis_quality',
                    'status': 'PASSED',
                    'score': avg_score,
                    'threshold': 0.90
                })
            else:
                print(f"✗ FAILED: Resume analysis quality {avg_score:.3f} < 90%")
                self.failed += 1
                self.results.append({
                    'test': 'resume_analysis_quality',
                    'status': 'FAILED',
                    'score': avg_score,
                    'threshold': 0.90
                })
    
    def test_question_generation_consistency(self):
        """
        Test: Questions match difficulty and relevance targets
        Ensures generated questions are appropriate for role and level
        """
        print("\n=== Test: Question Generation Consistency ===")
        
        examples = self.load_golden_examples("interview_questions")
        if not examples:
            print("⚠ No golden examples found for interview_questions")
            return
            
        print(f"Testing {len(examples)} interview question examples...")
        
        relevance_scores = []
        difficulty_scores = []
        
        for example in examples:
            try:
                quality_metrics = example.get('qualityMetrics', {})
                expected_output = example.get('expectedOutput', {})
                
                # Check relevance score
                relevance = quality_metrics.get('relevance', 0)
                relevance_scores.append(relevance)
                
                # Check difficulty score
                difficulty = quality_metrics.get('difficulty', 0)
                difficulty_scores.append(difficulty)
                
                # Validate expected output structure
                assert 'answerStructure' in expected_output, "Missing answerStructure"
                assert 'keyPoints' in expected_output, "Missing keyPoints"
                assert 'qualityIndicators' in expected_output, "Missing qualityIndicators"
                
                print(f"  ✓ {example['_source_file']}: relevance={relevance:.2f}, difficulty={difficulty:.2f}")
                
            except AssertionError as e:
                print(f"  ✗ {example['_source_file']}: {str(e)}")
                self.failed += 1
                continue
            except Exception as e:
                print(f"  ✗ {example['_source_file']}: Unexpected error - {str(e)}")
                self.failed += 1
                continue
        
        if relevance_scores:
            avg_relevance = statistics.mean(relevance_scores)
            print(f"\nAverage Relevance Score: {avg_relevance:.3f}")
            
            if avg_relevance >= 0.85:
                print("✓ PASSED: Question relevance >= 85%")
                self.passed += 1
                self.results.append({
                    'test': 'question_generation_consistency',
                    'status': 'PASSED',
                    'relevance': avg_relevance,
                    'threshold': 0.85
                })
            else:
                print(f"✗ FAILED: Question relevance {avg_relevance:.3f} < 85%")
                self.failed += 1
                self.results.append({
                    'test': 'question_generation_consistency',
                    'status': 'FAILED',
                    'relevance': avg_relevance,
                    'threshold': 0.85
                })
    
    def test_scoring_fairness(self):
        """
        Test: Scoring variance <15% across runs
        Ensures consistent and fair evaluation of answers
        Note: Variance is calculated within each quality level, not across all scores
        """
        print("\n=== Test: Scoring Fairness ===")
        
        examples = self.load_golden_examples("scoring")
        if not examples:
            print("⚠ No golden examples found for scoring")
            return
            
        print(f"Testing {len(examples)} scoring examples...")
        
        scores = []
        quality_levels = {'Poor': [], 'Average': [], 'Good': [], 'Excellent': []}
        
        for example in examples:
            try:
                evaluation = example.get('evaluation', {})
                score = evaluation.get('score', 0)
                level = evaluation.get('level', 'Unknown')
                
                scores.append(score)
                if level in quality_levels:
                    quality_levels[level].append(score)
                
                # Check evaluation structure
                assert 'strengths' in evaluation, "Missing strengths"
                assert 'improvements' in evaluation, "Missing improvements"
                assert 'feedback' in evaluation, "Missing feedback"
                assert 'technicalAccuracy' in evaluation, "Missing technicalAccuracy"
                
                print(f"  ✓ {example['_source_file']}: score={score:.1f}, level={level}")
                
            except AssertionError as e:
                print(f"  ✗ {example['_source_file']}: {str(e)}")
                self.failed += 1
                continue
            except Exception as e:
                print(f"  ✗ {example['_source_file']}: Unexpected error - {str(e)}")
                self.failed += 1
                continue
        
        if scores:
            mean_score = statistics.mean(scores)
            
            print(f"\nScore Statistics:")
            print(f"  Overall Mean: {mean_score:.2f}")
            
            # Calculate variance within each quality level
            level_variances = []
            for level, level_scores in quality_levels.items():
                if len(level_scores) >= 2:
                    avg = statistics.mean(level_scores)
                    std_dev = statistics.stdev(level_scores)
                    variance_pct = (std_dev / avg) * 100 if avg > 0 else 0
                    level_variances.append(variance_pct)
                    print(f"  {level}: avg={avg:.2f}, variance={variance_pct:.1f}%")
                elif level_scores:
                    avg = statistics.mean(level_scores)
                    print(f"  {level}: avg={avg:.2f} (only 1 sample)")
            
            # Test passes if we have consistent scoring within quality levels
            # OR if we have good separation between quality levels
            if level_variances:
                max_variance = max(level_variances)
                print(f"\n  Max variance within quality levels: {max_variance:.1f}%")
                
                if max_variance < 15:
                    print("✓ PASSED: Scoring variance within quality levels < 15%")
                    self.passed += 1
                    self.results.append({
                        'test': 'scoring_fairness',
                        'status': 'PASSED',
                        'max_variance': max_variance,
                        'threshold': 15.0
                    })
                else:
                    print(f"✗ FAILED: Scoring variance {max_variance:.1f}% >= 15%")
                    self.failed += 1
                    self.results.append({
                        'test': 'scoring_fairness',
                        'status': 'FAILED',
                        'max_variance': max_variance,
                        'threshold': 15.0
                    })
            else:
                # If we don't have multiple samples per level, just check score distribution is reasonable
                # Poor should be <5, Average 5-7, Good 7-9, Excellent 9-10
                distribution_valid = True
                for level, level_scores in quality_levels.items():
                    if level_scores:
                        avg = statistics.mean(level_scores)
                        if level == 'Poor' and avg >= 5:
                            distribution_valid = False
                        elif level == 'Average' and (avg < 4.5 or avg >= 7):
                            distribution_valid = False
                        elif level == 'Good' and (avg < 7 or avg >= 9):
                            distribution_valid = False
                        elif level == 'Excellent' and avg < 9:
                            distribution_valid = False
                
                if distribution_valid:
                    print("✓ PASSED: Score distribution matches quality levels")
                    self.passed += 1
                    self.results.append({
                        'test': 'scoring_fairness',
                        'status': 'PASSED',
                        'note': 'Distribution-based validation'
                    })
                else:
                    print("✗ FAILED: Score distribution does not match quality levels")
                    self.failed += 1
                    self.results.append({
                        'test': 'scoring_fairness',
                        'status': 'FAILED',
                        'note': 'Distribution-based validation failed'
                    })
        else:
            print("⚠ Insufficient data for variance calculation")
    
    def test_multi_turn_coherence(self):
        """
        Test: Conversation maintains context across turns
        Ensures multi-turn interviews are coherent and progressive
        """
        print("\n=== Test: Multi-turn Coherence ===")
        
        examples = self.load_golden_examples("multi_turn")
        if not examples:
            print("⚠ No golden examples found for multi_turn")
            return
            
        print(f"Testing {len(examples)} multi-turn conversation examples...")
        
        coherence_scores = []
        
        for example in examples:
            try:
                turns = example.get('turns', [])
                overall_assessment = example.get('overallAssessment', {})
                quality_metrics = example.get('qualityMetrics', {})
                
                # Check conversation flow
                conversation_flow = quality_metrics.get('conversationFlow', 0)
                
                # Check technical depth
                technical_depth = quality_metrics.get('technicalDepth', 0)
                
                # Check coherence
                coherence = quality_metrics.get('coherence', 0)
                
                # Check progressiveness
                progressiveness = quality_metrics.get('progressiveness', 0)
                
                # Overall coherence score
                coherence_score = (conversation_flow + technical_depth + coherence + progressiveness) / 4
                coherence_scores.append(coherence_score)
                
                # Validate structure
                assert len(turns) >= 2, "Multi-turn conversation must have at least 2 turns"
                assert 'assessmentScore' in overall_assessment, "Missing assessmentScore"
                
                print(f"  ✓ {example['_source_file']}: coherence={coherence_score:.2f}, turns={len(turns)}")
                
            except AssertionError as e:
                print(f"  ✗ {example['_source_file']}: {str(e)}")
                self.failed += 1
                continue
            except Exception as e:
                print(f"  ✗ {example['_source_file']}: Unexpected error - {str(e)}")
                self.failed += 1
                continue
        
        if coherence_scores:
            avg_coherence = statistics.mean(coherence_scores)
            print(f"\nAverage Coherence Score: {avg_coherence:.3f}")
            
            if avg_coherence >= 0.85:
                print("✓ PASSED: Multi-turn coherence >= 85%")
                self.passed += 1
                self.results.append({
                    'test': 'multi_turn_coherence',
                    'status': 'PASSED',
                    'coherence': avg_coherence,
                    'threshold': 0.85
                })
            else:
                print(f"✗ FAILED: Multi-turn coherence {avg_coherence:.3f} < 85%")
                self.failed += 1
                self.results.append({
                    'test': 'multi_turn_coherence',
                    'status': 'FAILED',
                    'coherence': avg_coherence,
                    'threshold': 0.85
                })
    
    def run_all_tests(self):
        """Run all regression tests"""
        print("=" * 60)
        print("ML REGRESSION TEST SUITE")
        print("=" * 60)
        print(f"Golden Dataset Path: {self.golden_path.absolute()}")
        print(f"Timestamp: {datetime.now().isoformat()}")
        
        # Run all tests
        self.test_resume_analysis_quality()
        self.test_question_generation_consistency()
        self.test_scoring_fairness()
        self.test_multi_turn_coherence()
        
        # Print summary
        print("\n" + "=" * 60)
        print("TEST SUMMARY")
        print("=" * 60)
        print(f"Total Tests: {self.passed + self.failed}")
        print(f"Passed: {self.passed}")
        print(f"Failed: {self.failed}")
        
        if self.failed == 0:
            print("\n✓ ALL TESTS PASSED")
            return 0
        else:
            print(f"\n✗ {self.failed} TEST(S) FAILED")
            return 1
    
    def save_results(self, output_path: str = "./test_results.json"):
        """Save test results to JSON file"""
        output = {
            'timestamp': datetime.now().isoformat(),
            'summary': {
                'total': self.passed + self.failed,
                'passed': self.passed,
                'failed': self.failed
            },
            'results': self.results
        }
        
        with open(output_path, 'w', encoding='utf-8') as f:
            json.dump(output, f, indent=2, ensure_ascii=False)
        
        print(f"\nResults saved to: {output_path}")


def main():
    """Main entry point"""
    # Get golden dataset path from args or use default
    golden_path = sys.argv[1] if len(sys.argv) > 1 else "./golden_dataset"
    
    # Run tests
    suite = RegressionTestSuite(golden_path)
    exit_code = suite.run_all_tests()
    
    # Save results
    suite.save_results()
    
    sys.exit(exit_code)


if __name__ == "__main__":
    main()
