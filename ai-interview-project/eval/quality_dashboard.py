#!/usr/bin/env python3
"""
ML Quality Dashboard
Real-time visualization of AI output quality metrics and regression test results
"""

import streamlit as st
import pandas as pd
import json
from pathlib import Path
from datetime import datetime, timedelta
import mysql.connector
from mysql.connector import Error
import plotly.graph_objects as go
import plotly.express as px
from typing import List, Dict, Any
from scipy import stats
import os

# Page configuration
st.set_page_config(
    page_title="ML Quality Dashboard",
    page_icon="📊",
    layout="wide",
    initial_sidebar_state="expanded"
)

# Database configuration
DB_CONFIG = {
    'host': os.getenv('DB_HOST', 'localhost'),
    'port': int(os.getenv('DB_PORT', 3306)),
    'database': os.getenv('DB_NAME', 'ai_interview_db'),
    'user': os.getenv('DB_USER', 'root'),
    'password': os.getenv('DB_PASSWORD', 'root')
}

# Golden dataset path
GOLDEN_DATASET_PATH = Path(__file__).parent / "golden_dataset"


@st.cache_resource
def get_db_connection():
    """Create database connection"""
    try:
        connection = mysql.connector.connect(**DB_CONFIG)
        return connection
    except Error as e:
        st.error(f"Database connection failed: {e}")
        return None


def fetch_quality_metrics(hours: int = 24) -> pd.DataFrame:
    """Fetch quality metrics from database"""
    connection = get_db_connection()
    if not connection:
        return pd.DataFrame()
    
    try:
        query = """
        SELECT 
            metric_name,
            metric_value,
            model_version,
            endpoint,
            created_at
        FROM ai_metrics_log
        WHERE metric_name LIKE 'ml.%'
          AND created_at >= NOW() - INTERVAL %s HOUR
        ORDER BY created_at DESC
        """
        
        df = pd.read_sql(query, connection, params=(hours,))
        return df
    except Error as e:
        st.error(f"Failed to fetch metrics: {e}")
        return pd.DataFrame()
    finally:
        if connection.is_connected():
            connection.close()


def fetch_validation_results(limit: int = 100) -> pd.DataFrame:
    """Fetch recent validation results"""
    connection = get_db_connection()
    if not connection:
        return pd.DataFrame()
    
    try:
        query = """
        SELECT 
            metric_name,
            metric_value,
            endpoint,
            tags,
            created_at
        FROM ai_metrics_log
        WHERE metric_name LIKE 'ml.validator.%'
        ORDER BY created_at DESC
        LIMIT %s
        """
        
        df = pd.read_sql(query, connection, params=(limit,))
        return df
    except Error as e:
        st.error(f"Failed to fetch validation results: {e}")
        return pd.DataFrame()
    finally:
        if connection.is_connected():
            connection.close()


def load_regression_test_results() -> Dict[str, Any]:
    """Load latest regression test results"""
    test_results_path = Path(__file__).parent / "test_results.json"
    
    if not test_results_path.exists():
        return {}
    
    try:
        with open(test_results_path, 'r', encoding='utf-8') as f:
            return json.load(f)
    except Exception as e:
        st.error(f"Failed to load test results: {e}")
        return {}


def load_golden_examples(category: str, limit: int = 5) -> List[Dict]:
    """Load golden examples from dataset"""
    category_path = GOLDEN_DATASET_PATH / category
    
    if not category_path.exists():
        return []
    
    examples = []
    for file_path in list(category_path.glob("*.json"))[:limit]:
        try:
            with open(file_path, 'r', encoding='utf-8') as f:
                example = json.load(f)
                example['_file'] = file_path.name
                examples.append(example)
        except Exception:
            continue
    
    return examples


def render_sidebar():
    """Render sidebar with filters and controls"""
    st.sidebar.title("📊 ML Quality Dashboard")
    st.sidebar.markdown("---")
    
    # Time range selector
    st.sidebar.subheader("⏱️ Time Range")
    time_range = st.sidebar.selectbox(
        "Select time range",
        ["Last 1 hour", "Last 6 hours", "Last 24 hours", "Last 7 days"],
        index=2
    )
    
    hours_map = {
        "Last 1 hour": 1,
        "Last 6 hours": 6,
        "Last 24 hours": 24,
        "Last 7 days": 168
    }
    hours = hours_map[time_range]
    
    # Model filter
    st.sidebar.subheader("🤖 Model Filter")
    model_filter = st.sidebar.multiselect(
        "Select models",
        ["gpt-4o-mini", "gpt-4", "gpt-3.5-turbo"],
        default=["gpt-4o-mini"]
    )
    
    # Endpoint filter
    st.sidebar.subheader("🎯 Endpoint Filter")
    endpoint_filter = st.sidebar.multiselect(
        "Select endpoints",
        ["RESUME_ANALYSIS", "GENERATE_QUESTIONS", "SCORE_ANSWER"],
        default=["RESUME_ANALYSIS", "GENERATE_QUESTIONS", "SCORE_ANSWER"]
    )
    
    st.sidebar.markdown("---")
    
    # Refresh button
    if st.sidebar.button("🔄 Refresh Data", use_container_width=True):
        st.cache_data.clear()
        st.rerun()
    
    # Database status
    st.sidebar.subheader("💾 Database Status")
    connection = get_db_connection()
    if connection and connection.is_connected():
        st.sidebar.success("✅ Connected")
        connection.close()
    else:
        st.sidebar.error("❌ Disconnected")
    
    return hours, model_filter, endpoint_filter


def render_quality_scores(df: pd.DataFrame):
    """Panel 1: Real-time Quality Scores"""
    st.header("📈 Real-time Quality Scores")
    
    if df.empty:
        st.warning("No quality metrics data available")
        return
    
    # Group by metric name
    metrics = [
        'ml.output.completeness',
        'ml.token.efficiency',
        'ml.vocabulary.diversity',
        'ml.repetition.score',
        'ml.technical.accuracy',
        'ml.clarity.score'
    ]
    
    metric_labels = {
        'ml.output.completeness': 'Completeness',
        'ml.token.efficiency': 'Token Efficiency',
        'ml.vocabulary.diversity': 'Vocabulary Diversity',
        'ml.repetition.score': 'Repetition Score',
        'ml.technical.accuracy': 'Technical Accuracy',
        'ml.clarity.score': 'Clarity Score'
    }
    
    # Current values (latest)
    col1, col2, col3 = st.columns(3)
    col4, col5, col6 = st.columns(3)
    cols = [col1, col2, col3, col4, col5, col6]
    
    for i, metric in enumerate(metrics):
        metric_df = df[df['metric_name'] == metric]
        if not metric_df.empty:
            latest_value = metric_df.iloc[0]['metric_value']
            avg_value = metric_df['metric_value'].mean()
            
            with cols[i]:
                st.metric(
                    label=metric_labels.get(metric, metric),
                    value=f"{latest_value:.3f}",
                    delta=f"Avg: {avg_value:.3f}"
                )
    
    # Time series chart
    st.subheader("Quality Metrics Over Time")
    
    fig = go.Figure()
    
    for metric in metrics:
        metric_df = df[df['metric_name'] == metric].sort_values('created_at')
        if not metric_df.empty:
            fig.add_trace(go.Scatter(
                x=metric_df['created_at'],
                y=metric_df['metric_value'],
                mode='lines+markers',
                name=metric_labels.get(metric, metric),
                line=dict(width=2)
            ))
    
    fig.update_layout(
        xaxis_title="Time",
        yaxis_title="Score",
        hovermode='x unified',
        height=400,
        showlegend=True
    )
    
    st.plotly_chart(fig, use_container_width=True)


def render_output_examples():
    """Panel 2: Output Examples with Quality Annotations"""
    st.header("📝 Output Examples")
    
    # Category selector
    category = st.selectbox(
        "Select category",
        ["resume_analysis", "interview_questions", "scoring", "multi_turn"]
    )
    
    examples = load_golden_examples(category, limit=5)
    
    if not examples:
        st.warning(f"No examples found for {category}")
        return
    
    # Display examples
    for example in examples:
        with st.expander(f"📄 {example.get('_file', 'Example')}"):
            col1, col2 = st.columns([2, 1])
            
            with col1:
                st.subheader("Content")
                if category == "resume_analysis":
                    st.write(f"**Candidate**: {example.get('name', 'N/A')}")
                    st.write(f"**Position**: {example.get('position', 'N/A')}")
                    st.write(f"**Experience**: {example.get('yearsOfExperience', 'N/A')} years")
                    st.write(f"**Tech Stack**: {', '.join(example.get('techStack', []))}")
                elif category == "interview_questions":
                    st.write(f"**Question ID**: {example.get('questionId', 'N/A')}")
                    st.write(f"**Difficulty**: {example.get('difficulty', 'N/A')}")
                    st.write(f"**Domain**: {example.get('domain', 'N/A')}")
                    st.write(f"**Question**: {example.get('question', 'N/A')}")
                elif category == "scoring":
                    st.write(f"**Question**: {example.get('question', 'N/A')[:100]}...")
                    st.write(f"**Score**: {example.get('evaluation', {}).get('score', 'N/A')}")
                    st.write(f"**Level**: {example.get('evaluation', {}).get('level', 'N/A')}")
            
            with col2:
                st.subheader("Quality Metrics")
                metrics = example.get('qualityMetrics', {})
                if metrics:
                    for key, value in metrics.items():
                        st.metric(key, f"{value:.2f}" if isinstance(value, float) else value)


def render_error_gallery(validation_df: pd.DataFrame):
    """Panel 3: Error Gallery with Failed Validations"""
    st.header("🚨 Error Gallery")
    
    if validation_df.empty:
        st.success("✅ No validation failures detected")
        return
    
    # Filter for failures (score < 0.8)
    validation_df['metric_value'] = pd.to_numeric(validation_df['metric_value'], errors='coerce')
    failures = validation_df[validation_df['metric_value'] < 0.8]
    
    if failures.empty:
        st.success("✅ No validation failures detected")
        return
    
    st.warning(f"⚠️ {len(failures)} validation failures detected")
    
    # Group by validator
    st.subheader("Failures by Validator")
    
    failure_counts = failures.groupby('metric_name').size().reset_index(name='count')
    failure_counts['metric_name'] = failure_counts['metric_name'].str.replace('ml.validator.', '')
    
    fig = px.bar(
        failure_counts,
        x='metric_name',
        y='count',
        title="Validation Failures by Type",
        labels={'metric_name': 'Validator', 'count': 'Failure Count'}
    )
    
    st.plotly_chart(fig, use_container_width=True)
    
    # Recent failures table
    st.subheader("Recent Failures")
    
    display_df = failures[['metric_name', 'metric_value', 'endpoint', 'created_at']].head(20)
    display_df['metric_name'] = display_df['metric_name'].str.replace('ml.validator.', '')
    display_df.columns = ['Validator', 'Score', 'Endpoint', 'Time']
    
    st.dataframe(display_df, use_container_width=True)


def render_comparison_view(df: pd.DataFrame):
    """Panel 4: Comparison View (Current vs Baseline)"""
    st.header("📊 Comparison View")
    
    if df.empty:
        st.warning("No data available for comparison")
        return
    
    # Calculate current (last 24h) vs baseline (7 days ago)
    now = datetime.now()
    current_start = now - timedelta(hours=24)
    baseline_start = now - timedelta(days=8)
    baseline_end = now - timedelta(days=7)
    
    current_df = df[df['created_at'] >= current_start]
    baseline_df = df[(df['created_at'] >= baseline_start) & (df['created_at'] < baseline_end)]
    
    metrics = [
        'ml.output.completeness',
        'ml.token.efficiency',
        'ml.vocabulary.diversity',
        'ml.clarity.score'
    ]
    
    comparison_data = []
    
    for metric in metrics:
        current_avg = current_df[current_df['metric_name'] == metric]['metric_value'].mean()
        baseline_avg = baseline_df[baseline_df['metric_name'] == metric]['metric_value'].mean()
        
        if pd.notna(current_avg) and pd.notna(baseline_avg):
            change = ((current_avg - baseline_avg) / baseline_avg) * 100
            comparison_data.append({
                'Metric': metric.replace('ml.', ''),
                'Current (24h)': f"{current_avg:.3f}",
                'Baseline (7d ago)': f"{baseline_avg:.3f}",
                'Change (%)': f"{change:+.1f}%"
            })
    
    if comparison_data:
        comparison_df = pd.DataFrame(comparison_data)
        st.dataframe(comparison_df, use_container_width=True)
        
        # Visualization
        fig = go.Figure()
        
        for _, row in comparison_df.iterrows():
            current = float(row['Current (24h)'])
            baseline = float(row['Baseline (7d ago)'])
            
            fig.add_trace(go.Bar(
                name='Current',
                x=[row['Metric']],
                y=[current],
                marker_color='lightblue'
            ))
            
            fig.add_trace(go.Bar(
                name='Baseline',
                x=[row['Metric']],
                y=[baseline],
                marker_color='lightgray'
            ))
        
        fig.update_layout(
            barmode='group',
            title="Current vs Baseline Comparison",
            yaxis_title="Score",
            height=400
        )
        
        st.plotly_chart(fig, use_container_width=True)
    else:
        st.info("Not enough data for comparison")


def render_regression_tests():
    """Panel 5: Regression Test Results"""
    st.header("🧪 Regression Test Results")
    
    test_results = load_regression_test_results()
    
    if not test_results:
        st.warning("No regression test results available. Run: `python eval/run_regression_tests.py`")
        return
    
    # Summary
    summary = test_results.get('summary', {})
    timestamp = test_results.get('timestamp', 'N/A')
    
    st.subheader("Test Summary")
    
    col1, col2, col3, col4 = st.columns(4)
    
    with col1:
        st.metric("Total Tests", summary.get('total', 0))
    with col2:
        st.metric("✅ Passed", summary.get('passed', 0))
    with col3:
        st.metric("❌ Failed", summary.get('failed', 0))
    with col4:
        pass_rate = (summary.get('passed', 0) / summary.get('total', 1)) * 100
        st.metric("Pass Rate", f"{pass_rate:.0f}%")
    
    st.caption(f"Last run: {timestamp}")
    
    # Individual test results
    st.subheader("Individual Test Results")
    
    results = test_results.get('results', [])
    
    for result in results:
        test_name = result.get('test', 'Unknown')
        status = result.get('status', 'UNKNOWN')
        
        if status == 'PASSED':
            st.success(f"✅ **{test_name}**: PASSED")
        else:
            st.error(f"❌ **{test_name}**: FAILED")
        
        # Show details
        with st.expander("Details"):
            for key, value in result.items():
                if key not in ['test', 'status']:
                    st.write(f"**{key}**: {value}")


def fetch_experiments() -> pd.DataFrame:
    """Fetch experiments from database"""
    connection = get_db_connection()
    if not connection:
        return pd.DataFrame()
    
    try:
        query = """
        SELECT 
            id,
            name,
            type,
            status,
            traffic_split,
            min_sample_size,
            primary_metric,
            started_at,
            completed_at,
            winner
        FROM ml_experiments
        ORDER BY created_at DESC
        LIMIT 50
        """
        
        df = pd.read_sql(query, connection)
        return df
    except Error as e:
        st.warning(f"Could not fetch experiments: {e}")
        return pd.DataFrame()
    finally:
        if connection.is_connected():
            connection.close()


def fetch_experiment_metrics(experiment_id: str) -> pd.DataFrame:
    """Fetch metrics for a specific experiment"""
    connection = get_db_connection()
    if not connection:
        return pd.DataFrame()
    
    try:
        query = """
        SELECT 
            experiment_id,
            variant,
            quality_score,
            latency_ms,
            cost_usd,
            validation_pass,
            created_at
        FROM experiment_metrics
        WHERE experiment_id = %s
        ORDER BY created_at DESC
        """
        
        df = pd.read_sql(query, connection, params=(experiment_id,))
        return df
    except Error as e:
        st.warning(f"Could not fetch experiment metrics: {e}")
        return pd.DataFrame()
    finally:
        if connection.is_connected():
            connection.close()


def render_experiments():
    """Render A/B experiments tab"""
    st.header("🔬 A/B Experiments")
    st.markdown("Monitor active experiments and analyze results")
    
    # Fetch experiments
    experiments_df = fetch_experiments()
    
    if experiments_df.empty:
        st.info("No experiments found. Experiments will appear here once created via the API.")
        st.markdown("**Example: Create an experiment**")
        st.code("""
POST /api/ml/experiments
{
  "id": "prompt_variant_2024",
  "name": "Test new prompt structure",
  "type": "prompt_variant",
  "baseline_config": "{\\"prompt_version\\": \\"v1.0\\"}",
  "variant_config": "{\\"prompt_version\\": \\"v1.2\\"}",
  "traffic_split": 0.5,
  "min_sample_size": 100
}
        """, language="json")
        return
    
    # Active experiments section
    st.subheader("Active Experiments")
    active_experiments = experiments_df[experiments_df['status'] == 'active']
    
    if not active_experiments.empty:
        for _, exp in active_experiments.iterrows():
            with st.expander(f"🟢 {exp['name']} ({exp['id']})"):
                col1, col2, col3 = st.columns(3)
                
                with col1:
                    st.metric("Type", exp['type'])
                    st.metric("Traffic Split", f"{int(exp['traffic_split']*100)}%")
                
                with col2:
                    st.metric("Status", exp['status'])
                    st.metric("Min Samples", exp['min_sample_size'])
                
                with col3:
                    st.metric("Primary Metric", exp['primary_metric'])
                    started = exp['started_at']
                    st.metric("Started", started.strftime("%Y-%m-%d") if started else "N/A")
                
                # Fetch and display metrics
                metrics_df = fetch_experiment_metrics(exp['id'])
                
                if not metrics_df.empty:
                    # Calculate stats
                    baseline_df = metrics_df[metrics_df['variant'] == 'baseline']
                    variant_df = metrics_df[metrics_df['variant'] == 'variant']
                    
                    st.markdown("**Sample Sizes:**")
                    col1, col2 = st.columns(2)
                    with col1:
                        st.metric("Baseline", len(baseline_df))
                    with col2:
                        st.metric("Variant", len(variant_df))
                    
                    # Comparison chart
                    if len(baseline_df) > 0 and len(variant_df) > 0:
                        st.markdown("**Quality Score Comparison:**")
                        
                        fig = go.Figure()
                        fig.add_trace(go.Box(
                            y=baseline_df['quality_score'],
                            name="Baseline",
                            marker_color='lightblue'
                        ))
                        fig.add_trace(go.Box(
                            y=variant_df['quality_score'],
                            name="Variant",
                            marker_color='lightgreen'
                        ))
                        fig.update_layout(
                            yaxis_title="Quality Score",
                            height=300
                        )
                        st.plotly_chart(fig, use_container_width=True)
                        
                        # Stats comparison
                        st.markdown("**Statistics:**")
                        comparison_data = {
                            'Metric': ['Quality Score', 'Latency (ms)', 'Cost ($)', 'Pass Rate'],
                            'Baseline': [
                                f"{baseline_df['quality_score'].mean():.2f}",
                                f"{baseline_df['latency_ms'].mean():.1f}",
                                f"${baseline_df['cost_usd'].mean():.4f}",
                                f"{baseline_df['validation_pass'].mean()*100:.1f}%"
                            ],
                            'Variant': [
                                f"{variant_df['quality_score'].mean():.2f}",
                                f"{variant_df['latency_ms'].mean():.1f}",
                                f"${variant_df['cost_usd'].mean():.4f}",
                                f"{variant_df['validation_pass'].mean()*100:.1f}%"
                            ]
                        }
                        st.table(pd.DataFrame(comparison_data))
                        
                        # Statistical significance
                        st.markdown("**Statistical Significance:**")
                        
                        # Perform Welch's t-test
                        baseline_scores = baseline_df['quality_score'].values
                        variant_scores = variant_df['quality_score'].values
                        
                        t_stat, p_value = stats.ttest_ind(baseline_scores, variant_scores, equal_var=False)
                        
                        # Calculate confidence intervals (95%)
                        baseline_mean = baseline_scores.mean()
                        variant_mean = variant_scores.mean()
                        baseline_se = stats.sem(baseline_scores)
                        variant_se = stats.sem(variant_scores)
                        baseline_ci = stats.t.interval(0.95, len(baseline_scores)-1, baseline_mean, baseline_se)
                        variant_ci = stats.t.interval(0.95, len(variant_scores)-1, variant_mean, variant_se)
                        
                        sig_col1, sig_col2, sig_col3 = st.columns(3)
                        
                        with sig_col1:
                            st.metric("p-value", f"{p_value:.4f}")
                            if p_value < 0.05:
                                st.success("✓ Statistically significant (p < 0.05)")
                            else:
                                st.warning("✗ Not significant (p ≥ 0.05)")
                        
                        with sig_col2:
                            st.metric("Baseline 95% CI", 
                                     f"[{baseline_ci[0]:.2f}, {baseline_ci[1]:.2f}]")
                        
                        with sig_col3:
                            st.metric("Variant 95% CI", 
                                     f"[{variant_ci[0]:.2f}, {variant_ci[1]:.2f}]")
                        
                        # Effect size
                        effect_size = (variant_mean - baseline_mean) / baseline_mean * 100
                        st.metric("Effect Size", 
                                 f"{effect_size:+.2f}%",
                                 delta=f"{variant_mean - baseline_mean:.2f} points")
                else:
                    st.info("No metrics collected yet")
    else:
        st.info("No active experiments")
    
    # Completed experiments section
    st.subheader("Completed Experiments")
    completed_experiments = experiments_df[experiments_df['status'].isin(['completed', 'rolled_back'])]
    
    if not completed_experiments.empty:
        for _, exp in completed_experiments.iterrows():
            status_emoji = "✅" if exp['status'] == 'completed' else "🔄"
            with st.expander(f"{status_emoji} {exp['name']} - Winner: {exp['winner'] or 'N/A'}"):
                col1, col2, col3 = st.columns(3)
                
                with col1:
                    st.metric("Status", exp['status'])
                    st.metric("Winner", exp['winner'] or 'N/A')
                
                with col2:
                    started = exp['started_at']
                    st.metric("Started", started.strftime("%Y-%m-%d") if started else "N/A")
                    completed = exp['completed_at']
                    st.metric("Completed", completed.strftime("%Y-%m-%d") if completed else "N/A")
                
                with col3:
                    st.metric("Type", exp['type'])
                    st.metric("Primary Metric", exp['primary_metric'])
    else:
        st.info("No completed experiments yet")


def main():
    """Main dashboard function"""
    # Render sidebar
    hours, model_filter, endpoint_filter = render_sidebar()
    
    # Fetch data
    with st.spinner("Loading data..."):
        df = fetch_quality_metrics(hours)
        validation_df = fetch_validation_results()
    
    # Filter data
    if not df.empty and model_filter:
        df = df[df['model_version'].isin(model_filter)]
    
    if not df.empty and endpoint_filter:
        df = df[df['endpoint'].isin(endpoint_filter)]
    
    # Main content tabs
    tab1, tab2, tab3, tab4, tab5, tab6 = st.tabs([
        "📈 Quality Scores",
        "📝 Output Examples",
        "🚨 Error Gallery",
        "📊 Comparison",
        "🧪 Regression Tests",
        "🔬 A/B Experiments"
    ])
    
    with tab1:
        render_quality_scores(df)
    
    with tab2:
        render_output_examples()
    
    with tab3:
        render_error_gallery(validation_df)
    
    with tab4:
        render_comparison_view(df)
    
    with tab5:
        render_regression_tests()
    
    with tab6:
        render_experiments()


if __name__ == "__main__":
    main()
