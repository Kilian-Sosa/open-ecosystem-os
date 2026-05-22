create table drive_files (
  file_id varchar(64) primary key,
  workspace_id varchar(128) not null,
  owner_id varchar(128) not null,
  encrypted_name text not null,
  content_type varchar(255) not null,
  size_bytes bigint not null,
  checksum_sha256 char(64) not null,
  storage_key varchar(1024) not null,
  encryption_algorithm varchar(64) not null,
  encryption_key_id varchar(128) not null,
  content_iv varchar(64) not null,
  name_iv varchar(64) not null,
  created_at timestamp with time zone not null,
  updated_at timestamp with time zone not null,
  constraint drive_files_size_non_negative check (size_bytes >= 0)
);

create index drive_files_workspace_created_at_idx
  on drive_files (workspace_id, created_at desc);

create table audit_records (
  audit_id varchar(64) primary key,
  action varchar(128) not null,
  resource_type varchar(128) not null,
  resource_id varchar(128),
  workspace_id varchar(128) not null,
  actor_id varchar(128) not null,
  correlation_id varchar(128) not null,
  occurred_at timestamp with time zone not null,
  outcome varchar(32) not null,
  attributes_json text not null
);

create index audit_records_workspace_occurred_at_idx
  on audit_records (workspace_id, occurred_at desc);

create table event_outbox (
  event_id varchar(64) primary key,
  event_type varchar(128) not null,
  version integer not null,
  occurred_at timestamp with time zone not null,
  workspace_id varchar(128) not null,
  actor_id varchar(128) not null,
  correlation_id varchar(128) not null,
  causation_id varchar(128),
  source varchar(128) not null,
  idempotency_key varchar(256) not null unique,
  payload_json text not null,
  envelope_json text not null,
  published_at timestamp with time zone,
  created_at timestamp with time zone not null
);

create index event_outbox_unpublished_created_at_idx
  on event_outbox (published_at, created_at);
