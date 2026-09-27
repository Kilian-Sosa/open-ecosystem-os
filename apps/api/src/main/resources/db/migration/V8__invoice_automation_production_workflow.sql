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
  'wfv_invoice_automation_v3',
  'flow_invoice_automation',
  'wrk_dev_placeholder',
  3,
  '{"trigger":{"type":"event","eventType":"OcrCompleted"},"steps":[{"id":"extract-invoice-fields","name":"Extract invoice fields","action":{"type":"extract_invoice_fields"}},{"id":"notify-review","name":"Create review notification","action":{"type":"create_notification","title":"Invoice extraction completed","body":"An invoice extraction is ready for review.","severity":"info"}},{"id":"audit-automation","name":"Record automation audit","action":{"type":"create_audit_entry","action":"flows.invoice_automation.completed","resourceType":"workflow_execution","attributes":{"workflow":"invoice_automation"}}},{"id":"knowledge-placeholder","name":"Create Knowledge placeholder","action":{"type":"create_knowledge_item_placeholder","title":"Invoice knowledge placeholder","summary":"A Knowledge placeholder was created from an invoice automation event."}},{"id":"index-search-result","name":"Request invoice search indexing","action":{"type":"request_search_indexing"}}]}',
  'usr_dev_placeholder',
  current_timestamp,
  current_timestamp
);

update workflows
set
  description = 'Runs when OCR completes and creates invoice extraction, notification, audit, Knowledge, and search records.',
  current_version_id = 'wfv_invoice_automation_v3',
  current_version_number = 3,
  updated_at = current_timestamp
where workflow_id = 'flow_invoice_automation';
