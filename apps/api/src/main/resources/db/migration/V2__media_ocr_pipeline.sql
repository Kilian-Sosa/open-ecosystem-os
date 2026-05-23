create table ocr_jobs (
  job_id varchar(64) primary key,
  file_id varchar(64) not null unique,
  workspace_id varchar(128) not null,
  actor_id varchar(128) not null,
  source_event_id varchar(64) not null,
  correlation_id varchar(128) not null,
  content_type varchar(255) not null,
  storage_key varchar(1024) not null,
  status varchar(32) not null,
  provider varchar(128),
  attempt_count integer not null,
  max_attempts integer not null,
  extracted_text text,
  extracted_text_length integer,
  failure_code varchar(128),
  failure_message varchar(512),
  queued_at timestamp with time zone not null,
  processing_started_at timestamp with time zone,
  completed_at timestamp with time zone,
  failed_at timestamp with time zone,
  next_attempt_at timestamp with time zone,
  created_at timestamp with time zone not null,
  updated_at timestamp with time zone not null,
  constraint ocr_jobs_attempts_non_negative check (attempt_count >= 0),
  constraint ocr_jobs_max_attempts_positive check (max_attempts > 0),
  constraint ocr_jobs_text_length_non_negative check (
    extracted_text_length is null or extracted_text_length >= 0
  ),
  constraint ocr_jobs_status_valid check (
    status in ('queued', 'processing', 'completed', 'failed')
  )
);

create index ocr_jobs_workspace_created_at_idx
  on ocr_jobs (workspace_id, created_at desc);

create index ocr_jobs_status_next_attempt_idx
  on ocr_jobs (status, next_attempt_at);

create table event_consumptions (
  consumer_name varchar(128) not null,
  idempotency_key varchar(256) not null,
  event_id varchar(64) not null,
  consumed_at timestamp with time zone not null,
  primary key (consumer_name, idempotency_key)
);

create index event_consumptions_event_id_idx
  on event_consumptions (event_id);
