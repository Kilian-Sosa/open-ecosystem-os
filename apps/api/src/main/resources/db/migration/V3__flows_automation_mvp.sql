create table workflows (
  workflow_id varchar(64) primary key,
  workspace_id varchar(128) not null,
  name varchar(255) not null,
  description varchar(1024) not null,
  status varchar(32) not null,
  current_version_id varchar(64),
  current_version_number integer not null,
  created_by varchar(128) not null,
  updated_by varchar(128) not null,
  created_at timestamp with time zone not null,
  updated_at timestamp with time zone not null,
  constraint workflows_status_valid check (status in ('draft', 'active', 'paused')),
  constraint workflows_current_version_non_negative check (current_version_number >= 0)
);

create index workflows_workspace_updated_at_idx
  on workflows (workspace_id, updated_at desc);

create index workflows_workspace_status_idx
  on workflows (workspace_id, status);

create table workflow_versions (
  version_id varchar(64) primary key,
  workflow_id varchar(64) not null references workflows (workflow_id),
  workspace_id varchar(128) not null,
  version_number integer not null,
  definition_json text not null,
  created_by varchar(128) not null,
  created_at timestamp with time zone not null,
  published_at timestamp with time zone,
  constraint workflow_versions_number_positive check (version_number > 0),
  constraint workflow_versions_unique_number unique (workflow_id, version_number)
);

create index workflow_versions_workflow_created_at_idx
  on workflow_versions (workflow_id, created_at desc);

alter table workflows
  add constraint workflows_current_version_fk
  foreign key (current_version_id) references workflow_versions (version_id);

create table workflow_executions (
  execution_id varchar(64) primary key,
  workflow_id varchar(64) not null references workflows (workflow_id),
  workflow_version_id varchar(64) not null references workflow_versions (version_id),
  workflow_version_number integer not null,
  workspace_id varchar(128) not null,
  actor_id varchar(128) not null,
  correlation_id varchar(128) not null,
  trigger_type varchar(32) not null,
  source_event_id varchar(64),
  source_event_type varchar(128),
  trigger_idempotency_key varchar(256) not null,
  status varchar(32) not null,
  retry_count integer not null,
  failure_reason varchar(512),
  started_at timestamp with time zone not null,
  completed_at timestamp with time zone,
  failed_at timestamp with time zone,
  created_at timestamp with time zone not null,
  updated_at timestamp with time zone not null,
  constraint workflow_executions_trigger_valid check (trigger_type in ('manual', 'event')),
  constraint workflow_executions_status_valid check (status in ('running', 'completed', 'failed')),
  constraint workflow_executions_retry_non_negative check (retry_count >= 0),
  constraint workflow_executions_unique_trigger unique (workspace_id, trigger_idempotency_key)
);

create index workflow_executions_workspace_created_at_idx
  on workflow_executions (workspace_id, created_at desc);

create index workflow_executions_workflow_created_at_idx
  on workflow_executions (workflow_id, created_at desc);

create table workflow_step_executions (
  step_execution_id varchar(64) primary key,
  execution_id varchar(64) not null references workflow_executions (execution_id),
  workflow_id varchar(64) not null references workflows (workflow_id),
  workspace_id varchar(128) not null,
  step_key varchar(128) not null,
  step_name varchar(255) not null,
  action_type varchar(128) not null,
  status varchar(32) not null,
  retry_count integer not null,
  failure_reason varchar(512),
  input_json text not null,
  output_json text not null,
  started_at timestamp with time zone not null,
  completed_at timestamp with time zone,
  failed_at timestamp with time zone,
  created_at timestamp with time zone not null,
  updated_at timestamp with time zone not null,
  constraint workflow_step_executions_status_valid check (status in ('running', 'completed', 'failed')),
  constraint workflow_step_executions_retry_non_negative check (retry_count >= 0),
  constraint workflow_step_executions_unique_step unique (execution_id, step_key)
);

create index workflow_step_executions_execution_idx
  on workflow_step_executions (execution_id, started_at);

create table notifications (
  notification_id varchar(64) primary key,
  workspace_id varchar(128) not null,
  actor_id varchar(128) not null,
  title varchar(255) not null,
  body varchar(1024) not null,
  severity varchar(32) not null,
  status varchar(32) not null,
  source_type varchar(128) not null,
  source_id varchar(128) not null,
  correlation_id varchar(128) not null,
  idempotency_key varchar(256) not null unique,
  created_at timestamp with time zone not null,
  read_at timestamp with time zone,
  constraint notifications_severity_valid check (severity in ('info', 'warning', 'danger')),
  constraint notifications_status_valid check (status in ('unread', 'read'))
);

create index notifications_workspace_created_at_idx
  on notifications (workspace_id, created_at desc);

create table knowledge_items (
  knowledge_item_id varchar(64) primary key,
  workspace_id varchar(128) not null,
  title varchar(255) not null,
  summary varchar(1024) not null,
  source_file_id varchar(64),
  source_ocr_job_id varchar(64),
  source_workflow_execution_id varchar(64) not null references workflow_executions (execution_id),
  source_event_id varchar(64),
  metadata_json text not null,
  created_by varchar(128) not null,
  correlation_id varchar(128) not null,
  idempotency_key varchar(256) not null unique,
  created_at timestamp with time zone not null,
  updated_at timestamp with time zone not null
);

create index knowledge_items_workspace_created_at_idx
  on knowledge_items (workspace_id, created_at desc);

insert into workflows (
  workflow_id,
  workspace_id,
  name,
  description,
  status,
  current_version_id,
  current_version_number,
  created_by,
  updated_by,
  created_at,
  updated_at
) values (
  'flow_invoice_automation',
  'wrk_dev_placeholder',
  'Invoice Processing Automation',
  'Runs when OCR completes and creates the first notification, audit entry, and Knowledge placeholder.',
  'active',
  null,
  1,
  'usr_dev_placeholder',
  'usr_dev_placeholder',
  current_timestamp,
  current_timestamp
);

insert into workflow_versions (
  version_id,
  workflow_id,
  workspace_id,
  version_number,
  definition_json,
  created_by,
  created_at,
  published_at
) values (
  'wfv_invoice_automation_v1',
  'flow_invoice_automation',
  'wrk_dev_placeholder',
  1,
  '{"trigger":{"type":"event","eventType":"OcrCompleted"},"steps":[{"id":"notify-review","name":"Create review notification","action":{"type":"create_notification","title":"OCR completed for invoice file","body":"A document finished OCR and is ready for review.","severity":"info"}},{"id":"audit-automation","name":"Record automation audit","action":{"type":"create_audit_entry","action":"flows.invoice_automation.completed","resourceType":"workflow_execution","attributes":{"workflow":"invoice_automation"}}},{"id":"knowledge-placeholder","name":"Create knowledge placeholder","action":{"type":"create_knowledge_item_placeholder","title":"OCR knowledge placeholder","summary":"A placeholder Knowledge item was created from an OCR completion event."}}]}',
  'usr_dev_placeholder',
  current_timestamp,
  current_timestamp
);

update workflows
set current_version_id = 'wfv_invoice_automation_v1'
where workflow_id = 'flow_invoice_automation';
