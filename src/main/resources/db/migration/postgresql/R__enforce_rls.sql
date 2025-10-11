-- ${migration_timestamp}
-- Repeatable migration to enable RLS and create a standard tenant policy on all tables
-- in the configured schema, excluding only explicitly listed tables.
-- Placeholders provided by RlsAutoConfiguration:
--   ${rls_enabled} (boolean literal)
--   ${rls_schema}
--   ${rls_tenant_column}
--   ${rls_policy_name}
--   ${rls_exclude_tables} (comma-separated names)

DO
$$
    DECLARE
        v_schema     text := '${rls_schema}';
        v_tenant_col text := '${rls_tenant_column}';
        v_policy     text := '${rls_policy_name}';
        has_col      boolean;
        rls_on       boolean;
        has_policy   boolean;
        rec          record;
    BEGIN
        IF ${rls_enabled} THEN
            FOR rec IN
                SELECT t.table_name
                FROM information_schema.tables t
                WHERE t.table_schema = v_schema
                  AND t.table_type = 'BASE TABLE'
                  AND NOT EXISTS (SELECT 1
                                  FROM regexp_split_to_table('${rls_exclude_tables}', ',') AS value
                                  WHERE length(trim(value)) > 0
                                    AND lower(trim(value)) = lower(t.table_name))
                LOOP
                    -- Ensure table has the tenant column
                    SELECT EXISTS (SELECT 1
                                   FROM information_schema.columns c
                                   WHERE c.table_schema = v_schema
                                     AND c.table_name = rec.table_name
                                     AND c.column_name = v_tenant_col)
                    INTO has_col;

                    IF has_col THEN
                        -- Enable RLS only if not already enabled
                        SELECT c.relrowsecurity
                        FROM pg_class c
                                 JOIN pg_namespace n ON n.oid = c.relnamespace
                        WHERE n.nspname = v_schema
                          AND c.relname = rec.table_name
                        INTO rls_on;

                        IF coalesce(rls_on, FALSE) = FALSE THEN
                            EXECUTE format('ALTER TABLE %I.%I ENABLE ROW LEVEL SECURITY', v_schema, rec.table_name);
                        END IF;

                        -- Create policy only if missing
                        SELECT EXISTS (SELECT 1
                                       FROM pg_policies p
                                       WHERE p.schemaname = v_schema
                                         AND p.tablename = rec.table_name
                                         AND p.policyname = v_policy)
                        INTO has_policy;

                        IF NOT has_policy THEN
                            EXECUTE format(
                                    'CREATE POLICY %1$I ON %2$I.%3$I USING (%4$I = current_setting(''app.tenant_id'')::uuid)',
                                    v_policy, v_schema, rec.table_name, v_tenant_col
                                    );
                        END IF;
                    ELSE
                        RAISE NOTICE 'RLS skipped for %.%: missing tenant column %', v_schema, rec.table_name, v_tenant_col;
                    END IF;
                END LOOP;
        END IF;
    END
$$;
