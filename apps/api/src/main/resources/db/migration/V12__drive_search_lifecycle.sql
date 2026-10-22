create table drive_search_lifecycle (
  workspace_id varchar(128) not null references workspaces(workspace_id),
  file_id varchar(64) not null,
  revision bigint not null check (revision >= 0),
  desired_state varchar(16) not null check (desired_state in ('ACTIVE','ABSENT')),
  reconciled_revision bigint not null default -1,
  next_attempt_at timestamp with time zone not null,
  last_error_code varchar(64),
  primary key (workspace_id, file_id)
);
create index drive_search_due_idx on drive_search_lifecycle(next_attempt_at, workspace_id, file_id);
create table drive_search_remote_documents (
  workspace_id varchar(128) not null,
  file_id varchar(64) not null,
  document_id varchar(64) not null,
  desired_revision bigint not null,
  desired_present boolean not null,
  completed_revision bigint not null default -1,
  completed_present boolean not null default false,
  operation_id varchar(64),
  operation_revision bigint,
  operation_present boolean,
  operation_started_at timestamp with time zone,
  operation_state varchar(16) check (operation_state in ('SUBMITTING','PENDING','UNCERTAIN')),
  task_uid bigint,
  lookup_cursor bigint,
  primary key (workspace_id, document_id),
  foreign key (workspace_id,file_id) references drive_search_lifecycle(workspace_id,file_id),
  constraint drive_search_operation_pair check ((operation_id is null and operation_state is null) or (operation_id is not null and operation_state is not null))
);
insert into drive_search_lifecycle(workspace_id,file_id,revision,desired_state,next_attempt_at)
  select workspace_id,file_id,1,'ABSENT',current_timestamp from drive_files where deletion_group_id is not null;
