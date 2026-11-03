CREATE TABLE app_user (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  login text NOT NULL CHECK (length(login) BETWEEN 1 AND 64),
  password_hash text NOT NULL,
  role text NOT NULL CHECK (role IN ('OPERATOR','ADMIN')),
  active boolean NOT NULL DEFAULT true,
  failed_attempts int NOT NULL DEFAULT 0,
  locked_until timestamptz,
  password_changed_at timestamptz NOT NULL DEFAULT now(),
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_app_user_login ON app_user (lower(login));

CREATE TABLE system_setting (
  key text PRIMARY KEY,
  value text NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by bigint REFERENCES app_user(id)
);
INSERT INTO system_setting(key, value) VALUES
  ('retention.results_days','30'),
  ('retention.journal_days','365'),
  ('traps.udp_port','162'),
  ('session.timeout_minutes','720'),
  ('auth.lock_attempts','5'),
  ('auth.lock_minutes','15');

CREATE TABLE profile (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  name text NOT NULL UNIQUE CHECK (length(name) BETWEEN 1 AND 128),
  description text NOT NULL DEFAULT '',
  seed_key text UNIQUE,
  version bigint NOT NULL DEFAULT 0,
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE profile_check (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  profile_id bigint NOT NULL REFERENCES profile(id) ON DELETE CASCADE,
  code text NOT NULL CHECK (code ~ '^[a-z0-9_-]{1,64}$'),
  name text NOT NULL,
  kind text NOT NULL CHECK (kind IN ('PING','TCP','SNMP','SCPI')),
  params jsonb NOT NULL DEFAULT '{}',
  interval_sec int NOT NULL CHECK (interval_sec BETWEEN 5 AND 86400),
  timeout_ms int NOT NULL CHECK (timeout_ms BETWEEN 100 AND 120000),
  warn_threshold numeric,
  crit_threshold numeric,
  hysteresis numeric NOT NULL DEFAULT 0 CHECK (hysteresis >= 0),
  threshold_direction text NOT NULL DEFAULT 'ABOVE' CHECK (threshold_direction IN ('ABOVE','BELOW')),
  fail_count int NOT NULL DEFAULT 3 CHECK (fail_count >= 1),
  success_count int NOT NULL DEFAULT 2 CHECK (success_count >= 1),
  required boolean NOT NULL DEFAULT true,
  is_primary boolean NOT NULL DEFAULT false,
  enabled boolean NOT NULL DEFAULT true,
  unconfirmed boolean NOT NULL DEFAULT false,
  unconfirmed_note text,
  source text,
  UNIQUE (profile_id, code),
  CHECK (NOT is_primary OR (required AND enabled))
);
CREATE UNIQUE INDEX ux_profile_check_primary ON profile_check (profile_id) WHERE is_primary;

CREATE TABLE device_group (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  name text NOT NULL UNIQUE CHECK (length(name) BETWEEN 1 AND 128)
);

CREATE TABLE device_type (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  name text NOT NULL UNIQUE CHECK (length(name) BETWEEN 1 AND 128),
  default_profile_id bigint REFERENCES profile(id),
  icon text NOT NULL DEFAULT 'generic' CHECK (icon ~ '^[a-z0-9-]{1,32}$')
);

CREATE TABLE device (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  name text NOT NULL CHECK (length(name) BETWEEN 1 AND 255),
  type_id bigint NOT NULL REFERENCES device_type(id),
  group_id bigint REFERENCES device_group(id),
  ip inet NOT NULL CHECK (family(ip) = 4 AND masklen(ip) = 32),
  lat numeric(9,6) NOT NULL CHECK (lat BETWEEN -90 AND 90),
  lon numeric(9,6) NOT NULL CHECK (lon BETWEEN -180 AND 180),
  address text NOT NULL DEFAULT '',
  profile_id bigint REFERENCES profile(id),
  status text NOT NULL DEFAULT 'NO_DATA' CHECK (status IN ('CRITICAL','WARNING','NO_DATA','OK')),
  status_changed_at timestamptz NOT NULL DEFAULT now(),
  monitoring_enabled boolean NOT NULL DEFAULT true,
  note text NOT NULL DEFAULT '',
  version bigint NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  deleted_at timestamptz,
  CHECK (deleted_at IS NOT NULL OR profile_id IS NOT NULL)
);
CREATE UNIQUE INDEX ux_device_ip_active ON device (ip) WHERE deleted_at IS NULL;
CREATE INDEX ix_device_profile_active ON device (profile_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_device_name_active ON device (lower(name)) WHERE deleted_at IS NULL;

CREATE TABLE access_secret (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  snmp_version text NOT NULL CHECK (snmp_version IN ('V2C','V3')),
  ciphertext bytea NOT NULL,
  key_version smallint NOT NULL DEFAULT 1,
  device_id bigint REFERENCES device(id),
  group_id bigint REFERENCES device_group(id),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CHECK (num_nonnulls(device_id, group_id) = 1)
);
CREATE UNIQUE INDEX ux_secret_device ON access_secret (device_id) WHERE device_id IS NOT NULL;
CREATE UNIQUE INDEX ux_secret_group ON access_secret (group_id) WHERE group_id IS NOT NULL;

CREATE TABLE trap_rule (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  profile_id bigint NOT NULL REFERENCES profile(id) ON DELETE CASCADE,
  trap_oid text NOT NULL CHECK (trap_oid ~ '^[0-9]+(\.[0-9]+)+$'),
  action text NOT NULL CHECK (action IN ('EVENT','ALARM','CLOSE')),
  level text CHECK (level IN ('WARNING','CRITICAL')),
  pair_key text,
  correlation_oid text,
  description text NOT NULL DEFAULT '',
  unconfirmed boolean NOT NULL DEFAULT false,
  source text,
  UNIQUE (profile_id, trap_oid),
  CHECK (action <> 'ALARM' OR level IS NOT NULL),
  CHECK (action = 'EVENT' OR pair_key IS NOT NULL)
);

CREATE TABLE check_state (
  device_id bigint NOT NULL REFERENCES device(id),
  check_id bigint NOT NULL REFERENCES profile_check(id) ON DELETE CASCADE,
  last_ts timestamptz,
  last_success boolean,
  last_value_num numeric,
  last_value_text text,
  last_error text,
  consecutive_failures int NOT NULL DEFAULT 0,
  consecutive_successes int NOT NULL DEFAULT 0,
  health text NOT NULL DEFAULT 'UNKNOWN' CHECK (health IN ('UNKNOWN','OK','FAILED')),
  threshold_level text NOT NULL DEFAULT 'NONE' CHECK (threshold_level IN ('NONE','WARNING','CRITICAL')),
  threshold_ok_streak int NOT NULL DEFAULT 0,
  PRIMARY KEY (device_id, check_id)
);

-- Результаты: разделы по суткам UTC. Без внешних ключей ради скорости вставки.
CREATE TABLE check_result (
  ts timestamptz NOT NULL,
  device_id bigint NOT NULL,
  check_id bigint NOT NULL,
  success boolean NOT NULL,
  value_num numeric,
  value_text text,
  error_code text,
  duration_ms int NOT NULL
) PARTITION BY RANGE (ts);
CREATE INDEX ix_check_result_device_ts ON check_result (device_id, ts DESC);
CREATE TABLE check_result_default PARTITION OF check_result DEFAULT;

CREATE TABLE event (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  ts timestamptz NOT NULL,
  device_id bigint REFERENCES device(id),
  type text NOT NULL CHECK (type IN ('STATUS_CHANGE','TRAP','TRAP_UNKNOWN_SOURCE','USER_ACTION')),
  source_ip inet,
  description text NOT NULL,
  details jsonb
);
CREATE INDEX ix_event_ts ON event (ts DESC);
CREATE INDEX ix_event_device_ts ON event (device_id, ts DESC);
CREATE INDEX ix_event_type_ts ON event (type, ts DESC);

CREATE TABLE alarm (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  device_id bigint NOT NULL REFERENCES device(id),
  level text NOT NULL CHECK (level IN ('WARNING','CRITICAL')),
  reason_key text NOT NULL,
  reason_text text NOT NULL,
  source text NOT NULL CHECK (source IN ('CHECK','TRAP')),
  check_id bigint,
  trap_rule_id bigint,
  started_at timestamptz NOT NULL,
  ended_at timestamptz,
  close_reason text CHECK (close_reason IN ('RECOVERED','PAIR_TRAP','POLL_OK','DEVICE_DELETED','MONITORING_OFF','CHECK_REMOVED')),
  ack_by bigint REFERENCES app_user(id),
  ack_at timestamptz,
  ack_comment text,
  CHECK ((ended_at IS NULL) = (close_reason IS NULL)),
  CHECK ((ack_at IS NULL) = (ack_by IS NULL) AND (ack_at IS NULL) = (ack_comment IS NULL))
);
CREATE UNIQUE INDEX ux_alarm_active ON alarm (device_id, reason_key) WHERE ended_at IS NULL;
CREATE INDEX ix_alarm_active_started ON alarm (started_at DESC) WHERE ended_at IS NULL;
CREATE INDEX ix_alarm_archive ON alarm (ended_at DESC) WHERE ended_at IS NOT NULL;
CREATE INDEX ix_alarm_device_started ON alarm (device_id, started_at DESC);

CREATE TABLE audit_log (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  ts timestamptz NOT NULL,
  user_id bigint REFERENCES app_user(id),
  user_login text NOT NULL,
  action text NOT NULL,
  object_type text NOT NULL,
  object_id text,
  old_value jsonb,
  new_value jsonb
);
CREATE INDEX ix_audit_ts ON audit_log (ts DESC);
