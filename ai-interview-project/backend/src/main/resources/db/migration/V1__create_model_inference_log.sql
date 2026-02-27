-- Flyway migration: create model_inference_log table (Postgres syntax)
CREATE TABLE IF NOT EXISTS model_inference_log (
  id BIGSERIAL PRIMARY KEY,
  ts TIMESTAMPTZ NOT NULL DEFAULT now(),
  request_id VARCHAR(255),
  model_version VARCHAR(128) NOT NULL,
  latency_ms DOUBLE PRECISION,
  tokens INTEGER,
  estimated_cost NUMERIC(12,6),
  validator_pass BOOLEAN,
  composite_quality DOUBLE PRECISION,
  failure_reason VARCHAR(1024),
  endpoint VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_model_inference_log_model_ts ON model_inference_log (model_version, ts);
CREATE INDEX IF NOT EXISTS idx_model_inference_log_ts ON model_inference_log (ts);
