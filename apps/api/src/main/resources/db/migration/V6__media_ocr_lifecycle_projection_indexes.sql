create index event_outbox_workspace_correlation_occurred_idx
  on event_outbox (workspace_id, correlation_id, occurred_at, event_id);

create index workflow_executions_workspace_source_event_started_idx
  on workflow_executions (workspace_id, source_event_id, started_at, execution_id);

create index audit_records_workspace_correlation_resource_occurred_idx
  on audit_records (workspace_id, correlation_id, resource_id, occurred_at, audit_id);
