create table identity_users (
  user_id varchar(128) primary key,
  display_name varchar(255) not null,
  email varchar(255) not null unique,
  avatar_initials varchar(16) not null,
  status varchar(32) not null,
  is_seeded boolean not null,
  created_at timestamp with time zone not null,
  updated_at timestamp with time zone not null,
  constraint identity_users_status_valid check (status in ('active', 'disabled'))
);

create table workspaces (
  workspace_id varchar(128) primary key,
  name varchar(255) not null,
  slug varchar(128) not null unique,
  status varchar(32) not null,
  is_seeded boolean not null,
  created_at timestamp with time zone not null,
  updated_at timestamp with time zone not null,
  constraint workspaces_status_valid check (status in ('active', 'disabled'))
);

create table workspace_memberships (
  workspace_id varchar(128) not null references workspaces (workspace_id),
  user_id varchar(128) not null references identity_users (user_id),
  role varchar(64) not null,
  is_default boolean not null,
  created_at timestamp with time zone not null,
  updated_at timestamp with time zone not null,
  primary key (workspace_id, user_id, role),
  constraint workspace_memberships_role_valid check (
    role in (
      'INSTANCE_OWNER',
      'WORKSPACE_ADMIN',
      'DEVELOPER',
      'EDITOR',
      'VIEWER',
      'GUEST',
      'AUDITOR',
      'DEVELOPMENT_PLACEHOLDER'
    )
  )
);

create index workspace_memberships_user_idx
  on workspace_memberships (user_id, workspace_id);

insert into identity_users (
  user_id,
  display_name,
  email,
  avatar_initials,
  status,
  is_seeded,
  created_at,
  updated_at
) values (
  'usr_dev_placeholder',
  'Demo Admin',
  'demo.admin@example.test',
  'DA',
  'active',
  true,
  current_timestamp,
  current_timestamp
);

insert into workspaces (
  workspace_id,
  name,
  slug,
  status,
  is_seeded,
  created_at,
  updated_at
) values (
  'wrk_dev_placeholder',
  'Open Ecosystem Demo Workspace',
  'demo',
  'active',
  true,
  current_timestamp,
  current_timestamp
);

insert into workspace_memberships (
  workspace_id,
  user_id,
  role,
  is_default,
  created_at,
  updated_at
) values
  (
    'wrk_dev_placeholder',
    'usr_dev_placeholder',
    'WORKSPACE_ADMIN',
    true,
    current_timestamp,
    current_timestamp
  ),
  (
    'wrk_dev_placeholder',
    'usr_dev_placeholder',
    'DEVELOPMENT_PLACEHOLDER',
    true,
    current_timestamp,
    current_timestamp
  );
