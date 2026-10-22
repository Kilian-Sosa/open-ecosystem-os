create table drive_trash_groups (
  group_id varchar(64) primary key,
  workspace_id varchar(128) not null references workspaces (workspace_id),
  root_item_type varchar(16) not null check (root_item_type in ('file', 'folder')),
  root_item_id varchar(64) not null,
  original_parent_folder_id varchar(64),
  actor_id varchar(128) not null references identity_users (user_id),
  trashed_at timestamp with time zone not null,
  purge_after timestamp with time zone not null,
  state varchar(16) not null check (state in ('TRASHED', 'PURGING', 'PURGED', 'RESTORED')),
  objects_deleted boolean not null default false,
  derived_deleted boolean not null default false,
  search_deleted boolean not null default false,
  cleanup_attempt integer not null default 0 check (cleanup_attempt >= 0),
  next_retry_at timestamp with time zone,
  claim_token varchar(64),
  lease_until timestamp with time zone,
  last_error_code varchar(64),
  constraint drive_trash_group_workspace_unique unique (group_id, workspace_id),
  constraint drive_trash_retention check (purge_after = trashed_at + interval '720' hour),
  constraint drive_trash_claim_pair check (
    (claim_token is null and lease_until is null) or
    (claim_token is not null and lease_until is not null and state = 'PURGING')),
  constraint drive_trash_purged_complete check (
    state <> 'PURGED' or (objects_deleted and derived_deleted and search_deleted))
);
create index drive_trash_due_idx on drive_trash_groups (state, purge_after, next_retry_at, lease_until);
create index drive_trash_workspace_idx on drive_trash_groups (workspace_id, trashed_at, group_id);
alter table drive_files add constraint drive_files_id_workspace_unique unique (file_id, workspace_id);
alter table drive_files add column deletion_group_id varchar(64);
alter table drive_files add column original_folder_id varchar(64);
alter table drive_files add constraint drive_files_trash_workspace_fk
  foreign key (deletion_group_id, workspace_id) references drive_trash_groups (group_id, workspace_id);
create index drive_files_trash_idx on drive_files (workspace_id, deletion_group_id, file_id);
alter table drive_folders add column deletion_group_id varchar(64);
alter table drive_folders add column original_parent_folder_id varchar(64);
alter table drive_folders add constraint drive_folders_trash_workspace_fk
  foreign key (deletion_group_id, workspace_id) references drive_trash_groups (group_id, workspace_id);
create index drive_folders_trash_idx on drive_folders (workspace_id, deletion_group_id, folder_id);
