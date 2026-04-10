#!/usr/bin/env python3
"""
Data Extraction Pipeline for Offline ML Evaluation

Extracts anonymized interview session data from the production database
for offline model evaluation and experimentation.

Usage:
    python data_extraction.py --output data/sessions.csv
    python data_extraction.py --start-date 2026-03-01 --output data/march.csv
"""

import argparse
import os
import sys
from datetime import datetime
from typing import Optional, List, Dict
import logging

try:
    import pandas as pd
    import mysql.connector
    from mysql.connector import Error
except ImportError as e:
    print(f"Error: Missing required package. Install with:")
    print(f"  pip install pandas mysql-connector-python")
    sys.exit(1)

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)


class DataExtractor:
    """Extracts and anonymizes interview session data"""
    
    def __init__(self, db_config: Dict[str, str]):
        """
        Initialize data extractor with database configuration.
        
        Args:
            db_config: Database connection parameters
        """
        self.db_config = db_config
        self.connection = None
    
    def connect(self):
        """Establish database connection"""
        try:
            self.connection = mysql.connector.connect(
                host=self.db_config['host'],
                port=self.db_config.get('port', 3306),
                database=self.db_config['database'],
                user=self.db_config['user'],
                password=self.db_config['password']
            )
            logger.info("Database connection established")
        except Error as e:
            logger.error(f"Database connection failed: {e}")
            raise
    
    def disconnect(self):
        """Close database connection"""
        if self.connection and self.connection.is_connected():
            self.connection.close()
            logger.info("Database connection closed")
    
    def extract_sessions(
        self,
        start_date: Optional[str] = None,
        end_date: Optional[str] = None,
        role_level: Optional[str] = None,
        tech_stack: Optional[str] = None,
        min_questions: int = 0,
        limit: Optional[int] = None
    ) -> pd.DataFrame:
        """
        Extract interview sessions with filters.
        
        Args:
            start_date: Start date (YYYY-MM-DD)
            end_date: End date (YYYY-MM-DD)
            role_level: Filter by role level (junior/mid/senior)
            tech_stack: Filter by tech stack
            min_questions: Minimum number of questions answered
            limit: Maximum number of sessions to extract
        
        Returns:
            DataFrame with session data
        """
        query = """
        SELECT 
            i.id as session_id,
            i.tech_stack,
            i.language,
            i.interview_type,
            i.status,
            i.started_at,
            i.ended_at,
            i.duration_seconds,
            COUNT(im.id) as question_count,
            AVG(im.evaluation_score) as avg_score,
            MAX(im.evaluation_score) as max_score,
            MIN(im.evaluation_score) as min_score,
            STDDEV(im.evaluation_score) as score_variance
        FROM interview i
        LEFT JOIN interview_message im ON i.id = im.interview_id
        WHERE i.status = 'completed'
        """
        
        params = []
        
        if start_date:
            query += " AND i.started_at >= %s"
            params.append(start_date)
        
        if end_date:
            query += " AND i.started_at <= %s"
            params.append(end_date)
        
        if tech_stack:
            query += " AND i.tech_stack LIKE %s"
            params.append(f"%{tech_stack}%")
        
        query += " GROUP BY i.id"
        
        if min_questions > 0:
            query += f" HAVING question_count >= {min_questions}"
        
        query += " ORDER BY i.started_at DESC"
        
        if limit:
            query += f" LIMIT {limit}"
        
        logger.info(f"Executing query with filters: start_date={start_date}, "
                   f"end_date={end_date}, tech_stack={tech_stack}, "
                   f"min_questions={min_questions}")
        
        cursor = self.connection.cursor(dictionary=True)
        cursor.execute(query, params)
        rows = cursor.fetchall()
        cursor.close()
        
        logger.info(f"Extracted {len(rows)} sessions")
        
        return pd.DataFrame(rows)
    
    def extract_detailed_responses(
        self,
        session_ids: List[str]
    ) -> pd.DataFrame:
        """
        Extract detailed response data for specific sessions.
        
        Args:
            session_ids: List of session IDs to extract
        
        Returns:
            DataFrame with response features
        """
        if not session_ids:
            return pd.DataFrame()
        
        placeholders = ','.join(['%s'] * len(session_ids))
        
        query = f"""
        SELECT 
            im.interview_id as session_id,
            im.id as message_id,
            im.message_type,
            im.evaluation_score,
            im.evaluation_rubric_level,
            im.technical_accuracy,
            im.depth_score,
            im.experience_score,
            im.communication_score,
            im.created_at,
            LENGTH(im.user_message) as response_length,
            LENGTH(im.ai_message) as question_length
        FROM interview_message im
        WHERE im.interview_id IN ({placeholders})
        ORDER BY im.interview_id, im.created_at
        """
        
        cursor = self.connection.cursor(dictionary=True)
        cursor.execute(query, session_ids)
        rows = cursor.fetchall()
        cursor.close()
        
        logger.info(f"Extracted {len(rows)} responses for {len(session_ids)} sessions")
        
        return pd.DataFrame(rows)
    
    def anonymize_data(self, df: pd.DataFrame) -> pd.DataFrame:
        """
        Anonymize sensitive information.
        
        Args:
            df: DataFrame with potentially sensitive data
        
        Returns:
            Anonymized DataFrame
        """
        # Session IDs are already UUIDs, no additional anonymization needed
        # Remove any timestamp precision beyond day level
        if 'started_at' in df.columns:
            df['started_at'] = pd.to_datetime(df['started_at']).dt.date
        if 'ended_at' in df.columns:
            df['ended_at'] = pd.to_datetime(df['ended_at']).dt.date
        if 'created_at' in df.columns:
            df['created_at'] = pd.to_datetime(df['created_at']).dt.date
        
        logger.info("Data anonymization complete")
        return df
    
    def extract_and_save(
        self,
        output_path: str,
        include_responses: bool = True,
        **filters
    ):
        """
        Extract data with filters and save to CSV.
        
        Args:
            output_path: Output CSV file path
            include_responses: Whether to include detailed response data
            **filters: Extraction filters (start_date, end_date, etc.)
        """
        self.connect()
        
        try:
            # Extract sessions
            sessions_df = self.extract_sessions(**filters)
            
            if sessions_df.empty:
                logger.warning("No sessions found with given filters")
                return
            
            # Anonymize
            sessions_df = self.anonymize_data(sessions_df)
            
            # Extract detailed responses if requested
            if include_responses:
                session_ids = sessions_df['session_id'].tolist()
                responses_df = self.extract_detailed_responses(session_ids)
                
                if not responses_df.empty:
                    responses_df = self.anonymize_data(responses_df)
                    
                    # Save both files
                    base_path = output_path.replace('.csv', '')
                    sessions_path = f"{base_path}_sessions.csv"
                    responses_path = f"{base_path}_responses.csv"
                    
                    sessions_df.to_csv(sessions_path, index=False)
                    responses_df.to_csv(responses_path, index=False)
                    
                    logger.info(f"Saved sessions to {sessions_path}")
                    logger.info(f"Saved responses to {responses_path}")
                else:
                    sessions_df.to_csv(output_path, index=False)
                    logger.info(f"Saved {len(sessions_df)} sessions to {output_path}")
            else:
                sessions_df.to_csv(output_path, index=False)
                logger.info(f"Saved {len(sessions_df)} sessions to {output_path}")
            
            # Print summary statistics
            self.print_summary(sessions_df)
            
        finally:
            self.disconnect()
    
    def print_summary(self, df: pd.DataFrame):
        """Print summary statistics of extracted data"""
        logger.info("\n=== Data Extraction Summary ===")
        logger.info(f"Total sessions: {len(df)}")
        logger.info(f"Date range: {df['started_at'].min()} to {df['started_at'].max()}")
        
        if 'tech_stack' in df.columns:
            logger.info(f"\nTech stacks:")
            for stack, count in df['tech_stack'].value_counts().head().items():
                logger.info(f"  {stack}: {count}")
        
        if 'question_count' in df.columns:
            logger.info(f"\nQuestion count stats:")
            logger.info(f"  Mean: {df['question_count'].mean():.1f}")
            logger.info(f"  Median: {df['question_count'].median():.1f}")
            logger.info(f"  Min: {df['question_count'].min()}")
            logger.info(f"  Max: {df['question_count'].max()}")
        
        if 'avg_score' in df.columns:
            logger.info(f"\nAverage score stats:")
            logger.info(f"  Mean: {df['avg_score'].mean():.2f}")
            logger.info(f"  Std: {df['avg_score'].std():.2f}")


def main():
    parser = argparse.ArgumentParser(
        description='Extract interview data for offline ML evaluation'
    )
    
    # Output options
    parser.add_argument(
        '--output', '-o',
        type=str,
        required=True,
        help='Output CSV file path'
    )
    
    # Filter options
    parser.add_argument(
        '--start-date',
        type=str,
        help='Start date (YYYY-MM-DD)'
    )
    parser.add_argument(
        '--end-date',
        type=str,
        help='End date (YYYY-MM-DD)'
    )
    parser.add_argument(
        '--role-level',
        type=str,
        choices=['junior', 'mid', 'senior'],
        help='Filter by role level'
    )
    parser.add_argument(
        '--tech-stack',
        type=str,
        help='Filter by tech stack (partial match)'
    )
    parser.add_argument(
        '--min-questions',
        type=int,
        default=0,
        help='Minimum number of questions answered'
    )
    parser.add_argument(
        '--limit',
        type=int,
        help='Maximum number of sessions to extract'
    )
    parser.add_argument(
        '--no-responses',
        action='store_true',
        help='Exclude detailed response data'
    )
    
    # Database options (use environment variables for security)
    parser.add_argument(
        '--db-host',
        type=str,
        default=os.getenv('DB_HOST'),
        help='Database host'
    )
    parser.add_argument(
        '--db-port',
        type=int,
        default=int(os.getenv('DB_PORT', '22629')),
        help='Database port'
    )
    parser.add_argument(
        '--db-name',
        type=str,
        default=os.getenv('DB_NAME', 'ai_interview'),
        help='Database name'
    )
    parser.add_argument(
        '--db-user',
        type=str,
        default=os.getenv('DB_USER', 'avnadmin'),
        help='Database user'
    )
    parser.add_argument(
        '--db-password',
        type=str,
        default=os.getenv('DB_PASSWORD'),
        help='Database password (or use DB_PASSWORD env var)'
    )
    
    args = parser.parse_args()
    
    # Validate database password
    if not args.db_password:
        logger.error("Database password required. Set DB_PASSWORD environment variable or use --db-password")
        sys.exit(1)
    
    # Create output directory if needed
    output_dir = os.path.dirname(args.output)
    if output_dir and not os.path.exists(output_dir):
        os.makedirs(output_dir)
        logger.info(f"Created output directory: {output_dir}")
    
    # Configure database connection
    db_config = {
        'host': args.db_host,
        'port': args.db_port,
        'database': args.db_name,
        'user': args.db_user,
        'password': args.db_password
    }
    
    # Extract data
    extractor = DataExtractor(db_config)
    extractor.extract_and_save(
        output_path=args.output,
        include_responses=not args.no_responses,
        start_date=args.start_date,
        end_date=args.end_date,
        role_level=args.role_level,
        tech_stack=args.tech_stack,
        min_questions=args.min_questions,
        limit=args.limit
    )


if __name__ == '__main__':
    main()
