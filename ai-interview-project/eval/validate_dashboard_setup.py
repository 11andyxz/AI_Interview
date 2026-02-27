#!/usr/bin/env python3
"""
Quick validation script to check dashboard readiness
"""

import sys
from pathlib import Path

def check_dashboard():
    """Validate dashboard setup"""
    print("🔍 Validating ML Quality Dashboard setup...\n")
    
    errors = []
    warnings = []
    
    # Check dashboard file
    dashboard_path = Path(__file__).parent / "quality_dashboard.py"
    if dashboard_path.exists():
        print("✅ quality_dashboard.py exists")
    else:
        errors.append("❌ quality_dashboard.py not found")
    
    # Check requirements file
    requirements_path = Path(__file__).parent / "requirements-dashboard.txt"
    if requirements_path.exists():
        print("✅ requirements-dashboard.txt exists")
    else:
        errors.append("❌ requirements-dashboard.txt not found")
    
    # Check dashboard README
    readme_path = Path(__file__).parent / "DASHBOARD_README.md"
    if readme_path.exists():
        print("✅ DASHBOARD_README.md exists")
    else:
        warnings.append("⚠️  DASHBOARD_README.md not found")
    
    # Check golden dataset
    golden_dataset_path = Path(__file__).parent / "golden_dataset"
    if golden_dataset_path.exists():
        resume_count = len(list((golden_dataset_path / "resume_analysis").glob("*.json")))
        question_count = len(list((golden_dataset_path / "interview_questions").glob("*.json")))
        scoring_count = len(list((golden_dataset_path / "scoring").glob("*.json")))
        multi_turn_count = len(list((golden_dataset_path / "multi_turn").glob("*.json")))
        total = resume_count + question_count + scoring_count + multi_turn_count
        
        print(f"✅ Golden dataset exists: {total} examples")
        print(f"   - resume_analysis: {resume_count}")
        print(f"   - interview_questions: {question_count}")
        print(f"   - scoring: {scoring_count}")
        print(f"   - multi_turn: {multi_turn_count}")
        
        if total < 150:
            warnings.append(f"⚠️  Golden dataset has only {total} examples (target: 150+)")
    else:
        errors.append("❌ golden_dataset directory not found")
    
    # Check test results file
    test_results_path = Path(__file__).parent / "test_results.json"
    if test_results_path.exists():
        print("✅ test_results.json exists (regression tests have been run)")
    else:
        warnings.append("⚠️  test_results.json not found (run regression tests first)")
    
    # Check Python dependencies
    print("\n🔍 Checking Python dependencies...")
    try:
        import streamlit
        print(f"✅ streamlit {streamlit.__version__}")
    except ImportError:
        errors.append("❌ streamlit not installed")
    
    try:
        import pandas
        print(f"✅ pandas {pandas.__version__}")
    except ImportError:
        errors.append("❌ pandas not installed")
    
    try:
        import plotly
        print(f"✅ plotly {plotly.__version__}")
    except ImportError:
        errors.append("❌ plotly not installed")
    
    try:
        import mysql.connector
        print(f"✅ mysql-connector-python {mysql.connector.__version__}")
    except ImportError:
        errors.append("❌ mysql-connector-python not installed")
    
    # Summary
    print("\n" + "="*50)
    print("📊 Validation Summary")
    print("="*50)
    
    if not errors and not warnings:
        print("✅ All checks passed! Dashboard is ready to run.")
        print("\n🚀 To start dashboard:")
        print("   streamlit run quality_dashboard.py")
        return 0
    
    if warnings:
        print(f"\n⚠️  {len(warnings)} warnings:")
        for warning in warnings:
            print(f"   {warning}")
    
    if errors:
        print(f"\n❌ {len(errors)} errors:")
        for error in errors:
            print(f"   {error}")
        print("\n💡 Fix errors before running dashboard:")
        print("   pip install -r requirements-dashboard.txt")
        return 1
    
    print("\n🚀 Dashboard is functional but has warnings. You can still start it:")
    print("   streamlit run quality_dashboard.py")
    return 0


if __name__ == "__main__":
    sys.exit(check_dashboard())
