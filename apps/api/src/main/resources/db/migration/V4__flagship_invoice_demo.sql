create table demo_invoice_runs (
  run_id varchar(64) primary key,
  workspace_id varchar(128) not null,
  actor_id varchar(128) not null,
  correlation_id varchar(128) not null,
  file_id varchar(64) not null,
  created_at timestamp with time zone not null,
  updated_at timestamp with time zone not null
);

create index demo_invoice_runs_workspace_created_at_idx
  on demo_invoice_runs (workspace_id, created_at desc);

create index demo_invoice_runs_file_idx
  on demo_invoice_runs (file_id);

create index demo_invoice_runs_correlation_idx
  on demo_invoice_runs (workspace_id, correlation_id);

create table demo_invoice_extractions (
  extraction_id varchar(64) primary key,
  run_id varchar(64) references demo_invoice_runs (run_id),
  workspace_id varchar(128) not null,
  actor_id varchar(128) not null,
  file_id varchar(64) not null,
  ocr_job_id varchar(64) not null,
  workflow_execution_id varchar(64) not null references workflow_executions (execution_id),
  invoice_number varchar(128) not null,
  supplier_name varchar(255) not null,
  supplier_test_nif varchar(64) not null,
  supplier_test_iban varchar(128) not null,
  total_amount numeric(12, 2) not null,
  currency varchar(8) not null,
  due_date date not null,
  is_test_data boolean not null,
  metadata_json text not null,
  created_at timestamp with time zone not null,
  constraint demo_invoice_extractions_unique_execution unique (workflow_execution_id),
  constraint demo_invoice_extractions_test_data check (is_test_data = true)
);

create index demo_invoice_extractions_workspace_created_at_idx
  on demo_invoice_extractions (workspace_id, created_at desc);

create index demo_invoice_extractions_run_idx
  on demo_invoice_extractions (run_id);

create table search_documents (
  search_document_id varchar(64) primary key,
  workspace_id varchar(128) not null,
  source_type varchar(128) not null,
  source_id varchar(128) not null,
  title varchar(255) not null,
  summary varchar(1024) not null,
  content text not null,
  resource_href varchar(1024) not null,
  correlation_id varchar(128) not null,
  status varchar(32) not null,
  attempt_count integer not null,
  max_attempts integer not null,
  failure_code varchar(128),
  failure_message varchar(512),
  metadata_json text not null,
  created_at timestamp with time zone not null,
  updated_at timestamp with time zone not null,
  indexed_at timestamp with time zone,
  failed_at timestamp with time zone,
  constraint search_documents_unique_source unique (workspace_id, source_type, source_id),
  constraint search_documents_status_valid check (
    status in ('pending', 'indexing', 'indexed', 'failed')
  ),
  constraint search_documents_attempts_non_negative check (attempt_count >= 0),
  constraint search_documents_max_attempts_positive check (max_attempts > 0)
);

create index search_documents_workspace_created_at_idx
  on search_documents (workspace_id, created_at desc);

create index search_documents_workspace_status_idx
  on search_documents (workspace_id, status);

create index search_documents_correlation_idx
  on search_documents (workspace_id, correlation_id);

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
  'wfv_invoice_automation_v2',
  'flow_invoice_automation',
  'wrk_dev_placeholder',
  2,
  '{"trigger":{"type":"event","eventType":"OcrCompleted"},"steps":[{"id":"extract-invoice-fields","name":"Extract fake/test invoice fields","action":{"type":"extract_invoice_fields"}},{"id":"notify-review","name":"Create review notification","action":{"type":"create_notification","title":"Fake/test invoice automation completed","body":"A fake/test invoice finished OCR and structured extraction. Review the seeded demo result.","severity":"info"}},{"id":"audit-automation","name":"Record automation audit","action":{"type":"create_audit_entry","action":"flows.invoice_automation.completed","resourceType":"workflow_execution","attributes":{"workflow":"invoice_automation","dataClassification":"fake_test_data"}}},{"id":"knowledge-placeholder","name":"Create knowledge placeholder","action":{"type":"create_knowledge_item_placeholder","title":"Fake/test invoice knowledge placeholder","summary":"A placeholder Knowledge item was created from the fake/test invoice OCR completion event."}},{"id":"index-search-result","name":"Index fake/test invoice result","action":{"type":"request_search_indexing"}}]}',
  'usr_dev_placeholder',
  current_timestamp,
  current_timestamp
);

update workflows
set
  description = 'Runs when OCR completes and creates fake/test extraction, notification, audit, Knowledge, and search records.',
  current_version_id = 'wfv_invoice_automation_v2',
  current_version_number = 2,
  updated_at = current_timestamp
where workflow_id = 'flow_invoice_automation';
