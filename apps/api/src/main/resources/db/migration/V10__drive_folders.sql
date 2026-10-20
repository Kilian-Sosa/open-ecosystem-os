create table drive_folders (
  folder_id varchar(64) primary key,
  workspace_id varchar(128) not null references workspaces (workspace_id),
  owner_id varchar(128) not null references identity_users (user_id),
  parent_folder_id varchar(64),
  encrypted_name text not null,
  name_iv varchar(64) not null,
  created_at timestamp with time zone not null,
  updated_at timestamp with time zone not null,
  constraint drive_folders_id_workspace_unique unique (folder_id, workspace_id),
  constraint drive_folders_parent_workspace_fk
    foreign key (parent_folder_id, workspace_id)
    references drive_folders (folder_id, workspace_id)
);

create index drive_folders_workspace_parent_idx
  on drive_folders (workspace_id, parent_folder_id, created_at, folder_id);

alter table drive_files add column folder_id varchar(64);

alter table drive_files
  add constraint drive_files_folder_workspace_fk
  foreign key (folder_id, workspace_id)
  references drive_folders (folder_id, workspace_id);

create index drive_files_workspace_folder_created_at_idx
  on drive_files (workspace_id, folder_id, created_at desc);
