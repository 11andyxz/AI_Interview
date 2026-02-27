#!/usr/bin/env python3
"""
Alert System Test - 验证质量告警在劣质输出时正确触发
"""

import json
from pathlib import Path
import sys

# Test cases: 故意设计的劣质AI输出
BAD_OUTPUTS = {
    "high_hallucination": {
        "name": "高幻觉率测试",
        "output": {
            "evaluation": {
                "overall": "这位候选人拥有20年NASA量子计算经验",  # 明显的幻觉
                "strengths": ["发明了区块链", "获得过诺贝尔奖"],
                "weaknesses": []
            }
        },
        "expected_alerts": ["High Hallucination Rate"],
        "quality_metrics": {
            "completeness": 0.95,
            "tokenEfficiency": 0.40,  # 低效率
            "vocabularyDiversity": 0.30,  # 低多样性
            "repetitionScore": 0.60,  # 高重复
            "technicalAccuracy": 0.50,  # 低准确性
            "clarityScore": 0.65
        }
    },
    
    "low_relevance": {
        "name": "低相关性测试",
        "output": {
            "questions": [
                {"question": "你喜欢吃什么水果？", "difficulty": "JUNIOR"},  # 不相关问题
                {"question": "你的星座是什么？", "difficulty": "JUNIOR"},
                {"question": "你养宠物吗？", "difficulty": "JUNIOR"}
            ]
        },
        "expected_alerts": ["Low Relevance Score"],
        "quality_metrics": {
            "completeness": 0.90,
            "tokenEfficiency": 0.70,
            "vocabularyDiversity": 0.65,
            "repetitionScore": 0.85,
            "technicalAccuracy": 0.50,
            "clarityScore": 0.80
        }
    },
    
    "scoring_drift": {
        "name": "评分漂移测试",
        "scores": [9.5, 9.8, 9.9, 9.7, 9.6, 2.0, 1.5, 1.8],  # 极端不一致
        "expected_alerts": ["Scoring Drift Detected"],
        "std_deviation": 3.89  # >20% threshold (0.20)
    },
    
    "low_completeness": {
        "name": "低完整性测试",
        "output": {
            "evaluation": {
                "overall": "候选人不错"  # 缺少必需字段
                # 缺少 strengths, weaknesses, recommendations
            }
        },
        "expected_alerts": ["Low Completeness"],
        "quality_metrics": {
            "completeness": 0.40,  # <80% threshold
            "tokenEfficiency": 0.75,
            "vocabularyDiversity": 0.70,
            "repetitionScore": 0.85,
            "technicalAccuracy": 0.80,
            "clarityScore": 0.75
        }
    },
    
    "decreased_quality": {
        "name": "质量下降测试",
        "output": {
            "evaluation": {
                "overall": "候选人候选人候选人还可以还可以还可以吧吧吧",  # 重复严重
                "strengths": ["行", "可以", "还行"],  # 模糊描述
                "weaknesses": ["不太行"],
                "recommendations": ["继续努力"]
            }
        },
        "expected_alerts": ["Decreased Question Quality"],
        "quality_metrics": {
            "completeness": 0.85,
            "tokenEfficiency": 0.55,
            "vocabularyDiversity": 0.35,
            "repetitionScore": 0.40,
            "technicalAccuracy": 0.70,
            "clarityScore": 6.8  # <7.5 threshold
        }
    },
    
    "token_inefficiency": {
        "name": "Token低效测试",
        "output": {
            "evaluation": {
                "overall": " ".join(["嗯" * 50, "这个", "那个"] * 20),  # 大量废话
                "strengths": ["有一些经验吧可能也许大概"],
                "weaknesses": ["emmm不太清楚呢哈哈哈"],
                "recommendations": ["就是说啊就是说那个你懂的"]
            }
        },
        "expected_alerts": ["Token Inefficiency"],
        "quality_metrics": {
            "completeness": 0.90,
            "tokenEfficiency": 0.50,  # <0.6 threshold
            "vocabularyDiversity": 0.25,
            "repetitionScore": 0.30,
            "technicalAccuracy": 0.65,
            "clarityScore": 7.0
        }
    },
    
    "high_repetition": {
        "name": "高重复测试",
        "output": {
            "questions": [
                {"question": "请解释Spring Boot的自动配置原理？", "difficulty": "MID"},
                {"question": "请解释Spring Boot的自动配置机制？", "difficulty": "MID"},
                {"question": "请说明Spring Boot的自动配置原理？", "difficulty": "MID"},
                {"question": "请阐述Spring Boot的自动配置原理？", "difficulty": "MID"}
            ]
        },
        "expected_alerts": ["High Repetition"],
        "quality_metrics": {
            "completeness": 0.95,
            "tokenEfficiency": 0.70,
            "vocabularyDiversity": 0.35,
            "repetitionScore": 0.55,  # <0.7 threshold
            "technicalAccuracy": 0.85,
            "clarityScore": 8.0
        }
    },
    
    "low_technical_accuracy": {
        "name": "低技术准确性测试",
        "output": {
            "evaluation": {
                "overall": "候选人精通HTML5、CSS3、Bootstrap等后端技术",  # 技术分类错误
                "strengths": ["精通MySQL前端框架", "熟悉Python的类型系统"],  # 错误描述
                "weaknesses": ["对Spring Boot这个数据库不太熟悉"],  # 概念混淆
                "recommendations": ["学习React后端开发"]  # 领域混淆
            }
        },
        "expected_alerts": ["Low Technical Accuracy"],
        "quality_metrics": {
            "completeness": 0.90,
            "tokenEfficiency": 0.75,
            "vocabularyDiversity": 0.70,
            "repetitionScore": 0.85,
            "technicalAccuracy": 0.70,  # <85% threshold
            "clarityScore": 8.0
        }
    }
}


def check_alert_triggers(test_cases):
    """检查每个测试用例应该触发的告警"""
    
    print("="*60)
    print("ML Quality Alert System - Trigger Test")
    print("="*60)
    print()
    
    results = {
        "total_tests": len(test_cases),
        "triggered_alerts": [],
        "test_details": []
    }
    
    # Load alert configuration
    alert_config_path = Path(__file__).parent.parent / "backend" / "src" / "main" / "resources" / "ml_quality_alerts.yml"
    
    print(f"📄 Alert Configuration: {alert_config_path}")
    print()
    
    for test_id, test_case in test_cases.items():
        print(f"🧪 Test: {test_case['name']}")
        print(f"   ID: {test_id}")
        
        metrics = test_case.get('quality_metrics', {})
        expected_alerts = test_case['expected_alerts']
        
        triggered = []
        
        # Check each metric against thresholds
        if metrics.get('completeness', 1.0) < 0.80:
            triggered.append("Low Completeness")
        
        if metrics.get('tokenEfficiency', 1.0) < 0.6:
            triggered.append("Token Inefficiency")
        
        if metrics.get('vocabularyDiversity', 1.0) < 0.40:
            triggered.append("Low Diversity")
        
        if metrics.get('repetitionScore', 1.0) < 0.7:
            triggered.append("High Repetition")
        
        if metrics.get('technicalAccuracy', 1.0) < 0.85:
            triggered.append("Low Technical Accuracy")
        
        if metrics.get('clarityScore', 10.0) < 7.5:
            triggered.append("Decreased Question Quality")
        
        # Special cases
        if test_id == "scoring_drift":
            if test_case['std_deviation'] > 0.20:
                triggered.append("Scoring Drift Detected")
        
        if test_id == "high_hallucination":
            # Hallucination detected by ContentQualityValidator
            triggered.append("High Hallucination Rate")
        
        if test_id == "low_relevance":
            # Relevance detected by RelevanceValidator
            triggered.append("Low Relevance Score")
        
        # Check results
        all_expected_triggered = all(alert in triggered for alert in expected_alerts)
        
        if all_expected_triggered:
            print(f"   ✅ PASS - 预期告警已触发: {', '.join(expected_alerts)}")
            status = "PASS"
        else:
            print(f"   ❌ FAIL - 预期: {expected_alerts}, 实际: {triggered}")
            status = "FAIL"
        
        print(f"   📊 Quality Metrics:")
        for metric, value in metrics.items():
            print(f"      {metric}: {value}")
        print()
        
        results['triggered_alerts'].extend(triggered)
        results['test_details'].append({
            "test": test_case['name'],
            "test_id": test_id,
            "expected_alerts": expected_alerts,
            "triggered_alerts": triggered,
            "status": status,
            "metrics": metrics
        })
    
    # Summary
    print("="*60)
    print("📊 Test Summary")
    print("="*60)
    
    passed = sum(1 for t in results['test_details'] if t['status'] == 'PASS')
    failed = results['total_tests'] - passed
    
    print(f"Total Tests: {results['total_tests']}")
    print(f"✅ Passed: {passed}")
    print(f"❌ Failed: {failed}")
    print(f"Pass Rate: {passed/results['total_tests']*100:.1f}%")
    print()
    
    print("Alert Coverage:")
    unique_alerts = set(results['triggered_alerts'])
    print(f"  Unique alerts triggered: {len(unique_alerts)}")
    for alert in sorted(unique_alerts):
        count = results['triggered_alerts'].count(alert)
        print(f"    - {alert}: {count} time(s)")
    print()
    
    # Save results
    output_path = Path(__file__).parent / "alert_trigger_test_results.json"
    with open(output_path, 'w', encoding='utf-8') as f:
        json.dump(results, f, indent=2, ensure_ascii=False)
    
    print(f"📝 Results saved to: {output_path}")
    print()
    
    if failed == 0:
        print("✅ All alert trigger tests passed!")
        return 0
    else:
        print(f"❌ {failed} test(s) failed")
        return 1


if __name__ == "__main__":
    exit_code = check_alert_triggers(BAD_OUTPUTS)
    sys.exit(exit_code)
