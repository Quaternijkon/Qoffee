CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS devices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_devices_user_id ON devices(user_id);

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_id UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    token_hash TEXT NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_id ON refresh_tokens(user_id);

CREATE TABLE IF NOT EXISTS sync_change_log (
    id BIGSERIAL PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    table_name TEXT NOT NULL,
    entity_id UUID NOT NULL,
    operation TEXT NOT NULL,
    version BIGINT NOT NULL,
    payload_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    source_device_id UUID REFERENCES devices(id) ON DELETE SET NULL,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_sync_change_log_owner_cursor ON sync_change_log(owner_id, id);

CREATE TABLE IF NOT EXISTS sync_conflicts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    table_name TEXT NOT NULL,
    entity_id UUID NOT NULL,
    local_key TEXT NOT NULL,
    remote_version BIGINT NOT NULL,
    local_base_version BIGINT,
    remote_payload_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    local_payload_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_sync_conflicts_owner_open ON sync_conflicts(owner_id, resolved_at);

CREATE TABLE IF NOT EXISTS sync_snapshots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_id UUID REFERENCES devices(id) ON DELETE SET NULL,
    file_name TEXT NOT NULL,
    mime_type TEXT NOT NULL,
    content TEXT NOT NULL,
    checksum TEXT NOT NULL,
    byte_size BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_sync_snapshots_owner_created ON sync_snapshots(owner_id, created_at DESC);

DO $$
DECLARE
    table_name TEXT;
    quoted_table TEXT;
BEGIN
    FOREACH table_name IN ARRAY ARRAY[
        'archives',
        'bean_profiles',
        'grinder_profiles',
        'recipe_templates',
        'brew_records',
        'subjective_evaluations',
        'flavor_tags',
        'record_flavor_tags',
        'archive',
        'coffee_product',
        'coffee_batch',
        'equipment_asset_type',
        'equipment_asset',
        'water_profile',
        'recipe',
        'recipe_version',
        'recipe_step_template',
        'metric_definition',
        'metric_enum_option',
        'event_definition',
        'tag_definition',
        'source_definition',
        'unit_definition',
        'collection',
        'collection_item_link',
        'brew_run',
        'brew_run_asset_link',
        'brew_stage_run',
        'observation',
        'event',
        'subject_tag_link',
        'inventory_transaction',
        'attachment',
        'import_log'
    ]
    LOOP
        quoted_table := quote_ident(table_name);
        EXECUTE format(
            'CREATE TABLE IF NOT EXISTS %s (
                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                owner_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                version BIGINT NOT NULL DEFAULT 1,
                created_server_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                updated_server_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                deleted_server_at TIMESTAMPTZ,
                source_device_id UUID REFERENCES devices(id) ON DELETE SET NULL,
                local_key TEXT NOT NULL,
                payload_json JSONB NOT NULL DEFAULT ''{}''::jsonb,
                UNIQUE(owner_id, local_key)
            )',
            quoted_table
        );
        EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %s(owner_id, updated_server_at)', 'idx_' || table_name || '_owner_updated', quoted_table);
        EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %s(owner_id, deleted_server_at)', 'idx_' || table_name || '_owner_deleted', quoted_table);
    END LOOP;
END $$;
