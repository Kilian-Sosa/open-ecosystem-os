alter table drive_files
  add column visibility varchar(32) not null default 'private';

alter table drive_files
  add constraint drive_files_visibility_valid
  check (visibility in ('private', 'workspace'));

create index drive_files_workspace_owner_created_at_idx
  on drive_files (workspace_id, owner_id, created_at desc);

create index drive_files_workspace_visibility_created_at_idx
  on drive_files (workspace_id, visibility, created_at desc);

create table drive_file_user_grants (
  grant_id varchar(64) primary key,
  workspace_id varchar(128) not null references workspaces (workspace_id),
  file_id varchar(64) not null,
  grantee_user_id varchar(128) not null references identity_users (user_id),
  action varchar(64) not null,
  granted_by_user_id varchar(128) not null references identity_users (user_id),
  granted_at timestamp with time zone not null,
  revoked_by_user_id varchar(128) references identity_users (user_id),
  revoked_at timestamp with time zone,
  created_at timestamp with time zone not null,
  updated_at timestamp with time zone not null,
  constraint drive_file_user_grants_file_workspace_fk
    foreign key (file_id, workspace_id)
    references drive_files (file_id, workspace_id)
    on delete cascade,
  constraint drive_file_user_grants_action_valid
    check (action = 'file:view'),
  constraint drive_file_user_grants_revocation_pair
    check (
      (revoked_at is null and revoked_by_user_id is null)
      or
      (revoked_at is not null and revoked_by_user_id is not null)
    ),
  constraint drive_file_user_grants_revocation_order
    check (revoked_at is null or revoked_at >= granted_at),
  constraint drive_file_user_grants_target_unique
    unique (workspace_id, file_id, grantee_user_id, action)
);

create index drive_file_user_grants_grantee_lookup_idx
  on drive_file_user_grants (
    workspace_id, grantee_user_id, action, revoked_at, file_id
  );

create index drive_file_user_grants_file_lookup_idx
  on drive_file_user_grants (
    workspace_id, file_id, action, revoked_at
  );
