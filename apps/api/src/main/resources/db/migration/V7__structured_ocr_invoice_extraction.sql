alter table drive_files
  add constraint drive_files_file_workspace_unique unique (file_id, workspace_id);

alter table ocr_jobs
  add constraint ocr_jobs_job_workspace_unique unique (job_id, workspace_id);

alter table ocr_jobs
  add constraint ocr_jobs_job_file_workspace_unique unique (job_id, file_id, workspace_id);

alter table workflow_executions
  add constraint workflow_executions_execution_workspace_unique unique (execution_id, workspace_id);

alter table ocr_jobs
  add constraint ocr_jobs_file_workspace_fk
  foreign key (file_id, workspace_id) references drive_files (file_id, workspace_id);

create table ocr_results (
  ocr_result_id varchar(64) primary key,
  job_id varchar(64) not null unique,
  file_id varchar(64) not null,
  workspace_id varchar(128) not null,
  provider varchar(128) not null,
  provider_version varchar(128) not null,
  document_text text not null,
  page_count integer not null,
  word_count integer not null,
  created_at timestamp with time zone not null,
  updated_at timestamp with time zone not null,
  constraint ocr_results_result_workspace_unique unique (ocr_result_id, workspace_id),
  constraint ocr_results_lineage_unique unique (
    ocr_result_id, job_id, file_id, workspace_id
  ),
  constraint ocr_results_job_lineage_fk
    foreign key (job_id, file_id, workspace_id)
    references ocr_jobs (job_id, file_id, workspace_id),
  constraint ocr_results_file_workspace_fk
    foreign key (file_id, workspace_id)
    references drive_files (file_id, workspace_id),
  constraint ocr_results_page_count_positive check (page_count > 0),
  constraint ocr_results_word_count_non_negative check (word_count >= 0)
);

create index ocr_results_workspace_created_at_idx
  on ocr_results (workspace_id, created_at desc);

create table ocr_result_pages (
  ocr_page_id varchar(64) primary key,
  ocr_result_id varchar(64) not null,
  workspace_id varchar(128) not null,
  page_number integer not null,
  source_kind varchar(32) not null,
  page_text text not null,
  word_count integer not null,
  created_at timestamp with time zone not null,
  constraint ocr_result_pages_result_page_unique unique (ocr_result_id, page_number),
  constraint ocr_result_pages_lineage_unique unique (
    ocr_page_id, ocr_result_id, workspace_id
  ),
  constraint ocr_result_pages_number_lineage_unique unique (
    ocr_page_id, ocr_result_id, workspace_id, page_number
  ),
  constraint ocr_result_pages_result_workspace_fk
    foreign key (ocr_result_id, workspace_id)
    references ocr_results (ocr_result_id, workspace_id),
  constraint ocr_result_pages_page_number_positive check (page_number > 0),
  constraint ocr_result_pages_word_count_non_negative check (word_count >= 0),
  constraint ocr_result_pages_source_kind_valid check (
    source_kind in ('pdf_text_layer', 'tesseract_tsv')
  )
);

create index ocr_result_pages_workspace_result_page_idx
  on ocr_result_pages (workspace_id, ocr_result_id, page_number);

create table ocr_result_words (
  ocr_word_id varchar(64) primary key,
  ocr_page_id varchar(64) not null,
  ocr_result_id varchar(64) not null,
  workspace_id varchar(128) not null,
  reading_order integer not null,
  page_number integer not null,
  page_word_order integer not null,
  block_number integer not null,
  paragraph_number integer not null,
  line_number integer not null,
  word_number integer not null,
  word_text text not null,
  confidence numeric(5, 2),
  left_px integer,
  top_px integer,
  width_px integer,
  height_px integer,
  source_kind varchar(32) not null,
  created_at timestamp with time zone not null,
  constraint ocr_result_words_reading_order_unique unique (ocr_result_id, reading_order),
  constraint ocr_result_words_lineage_unique unique (
    ocr_word_id, ocr_result_id, workspace_id
  ),
  constraint ocr_result_words_page_lineage_fk
    foreign key (ocr_page_id, ocr_result_id, workspace_id, page_number)
    references ocr_result_pages (
      ocr_page_id, ocr_result_id, workspace_id, page_number
    ),
  constraint ocr_result_words_reading_order_non_negative check (reading_order >= 0),
  constraint ocr_result_words_page_number_positive check (page_number > 0),
  constraint ocr_result_words_page_word_order_non_negative check (page_word_order >= 0),
  constraint ocr_result_words_block_number_non_negative check (block_number >= 0),
  constraint ocr_result_words_paragraph_number_non_negative check (paragraph_number >= 0),
  constraint ocr_result_words_line_number_non_negative check (line_number >= 0),
  constraint ocr_result_words_word_number_non_negative check (word_number >= 0),
  constraint ocr_result_words_confidence_valid check (
    confidence is null or (confidence >= 0 and confidence <= 100)
  ),
  constraint ocr_result_words_box_non_negative check (
    (left_px is null or left_px >= 0)
    and (top_px is null or top_px >= 0)
    and (width_px is null or width_px >= 0)
    and (height_px is null or height_px >= 0)
  ),
  constraint ocr_result_words_box_all_or_none check (
    (left_px is null and top_px is null and width_px is null and height_px is null)
    or
    (left_px is not null and top_px is not null and width_px is not null and height_px is not null)
  ),
  constraint ocr_result_words_source_kind_valid check (
    source_kind in ('pdf_text_layer', 'tesseract_tsv')
  )
);

create index ocr_result_words_workspace_result_order_idx
  on ocr_result_words (workspace_id, ocr_result_id, reading_order);

create table invoice_extractions (
  extraction_id varchar(64) primary key,
  workflow_execution_id varchar(64) not null unique,
  ocr_result_id varchar(64) not null,
  job_id varchar(64) not null,
  file_id varchar(64) not null,
  workspace_id varchar(128) not null,
  extractor_name varchar(128) not null,
  extractor_version varchar(128) not null,
  status varchar(32) not null,
  warnings_json text not null,
  aggregate_confidence numeric(5, 2),
  field_count integer not null,
  warning_count integer not null,
  created_at timestamp with time zone not null,
  updated_at timestamp with time zone not null,
  constraint invoice_extractions_lineage_unique unique (
    extraction_id, ocr_result_id, workspace_id
  ),
  constraint invoice_extractions_execution_workspace_fk
    foreign key (workflow_execution_id, workspace_id)
    references workflow_executions (execution_id, workspace_id),
  constraint invoice_extractions_ocr_lineage_fk
    foreign key (ocr_result_id, job_id, file_id, workspace_id)
    references ocr_results (ocr_result_id, job_id, file_id, workspace_id),
  constraint invoice_extractions_status_valid check (
    status in ('completed', 'review_required')
  ),
  constraint invoice_extractions_confidence_valid check (
    aggregate_confidence is null
    or (aggregate_confidence >= 0 and aggregate_confidence <= 100)
  ),
  constraint invoice_extractions_field_count_non_negative check (field_count >= 0),
  constraint invoice_extractions_warning_count_non_negative check (warning_count >= 0)
);

create index invoice_extractions_workspace_created_at_idx
  on invoice_extractions (workspace_id, created_at desc);

create index invoice_extractions_workspace_job_idx
  on invoice_extractions (workspace_id, job_id);

create table invoice_extraction_fields (
  field_id varchar(64) primary key,
  extraction_id varchar(64) not null,
  ocr_result_id varchar(64) not null,
  workspace_id varchar(128) not null,
  field_key varchar(128) not null,
  display_value text not null,
  normalized_value text not null,
  status varchar(32) not null,
  confidence numeric(5, 2),
  source_page_number integer not null,
  source_block_number integer not null,
  source_paragraph_number integer not null,
  source_line_number integer not null,
  created_at timestamp with time zone not null,
  constraint invoice_extraction_fields_key_unique unique (extraction_id, field_key),
  constraint invoice_extraction_fields_lineage_unique unique (
    field_id, ocr_result_id, workspace_id
  ),
  constraint invoice_extraction_fields_extraction_lineage_fk
    foreign key (extraction_id, ocr_result_id, workspace_id)
    references invoice_extractions (extraction_id, ocr_result_id, workspace_id),
  constraint invoice_extraction_fields_status_valid check (
    status in ('extracted', 'low_confidence')
  ),
  constraint invoice_extraction_fields_confidence_valid check (
    confidence is null or (confidence >= 0 and confidence <= 100)
  ),
  constraint invoice_extraction_fields_page_number_positive check (source_page_number > 0),
  constraint invoice_extraction_fields_block_non_negative check (source_block_number >= 0),
  constraint invoice_extraction_fields_paragraph_non_negative check (
    source_paragraph_number >= 0
  ),
  constraint invoice_extraction_fields_line_non_negative check (source_line_number >= 0)
);

create index invoice_extraction_fields_workspace_extraction_idx
  on invoice_extraction_fields (workspace_id, extraction_id);

create table invoice_extraction_field_sources (
  field_id varchar(64) not null,
  ocr_result_id varchar(64) not null,
  workspace_id varchar(128) not null,
  ocr_word_id varchar(64) not null,
  source_role varchar(16) not null,
  source_order integer not null,
  primary key (field_id, source_order),
  constraint invoice_extraction_field_sources_word_unique unique (field_id, ocr_word_id),
  constraint invoice_extraction_field_sources_field_lineage_fk
    foreign key (field_id, ocr_result_id, workspace_id)
    references invoice_extraction_fields (field_id, ocr_result_id, workspace_id),
  constraint invoice_extraction_field_sources_word_lineage_fk
    foreign key (ocr_word_id, ocr_result_id, workspace_id)
    references ocr_result_words (ocr_word_id, ocr_result_id, workspace_id),
  constraint invoice_extraction_field_sources_role_valid check (
    source_role in ('label', 'value')
  ),
  constraint invoice_extraction_field_sources_order_non_negative check (source_order >= 0)
);

create index invoice_extraction_field_sources_workspace_word_idx
  on invoice_extraction_field_sources (workspace_id, ocr_word_id);
