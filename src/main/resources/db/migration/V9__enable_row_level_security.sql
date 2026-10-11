-- Lock the application tables away from Supabase's REST API.
--
-- Supabase exposes the `public` schema over PostgREST, and ships a publishable key to every client
-- so the front end can sign users in. With row level security off, that key is enough to read and
-- write every table below through the REST API: session tokens in spring_session, saved bags and
-- the user ids that own them in bag_profile, and the disc catalog itself.
--
-- This application never uses that route. It connects straight to Postgres as the role that owns
-- these tables, and an owner is not subject to row level security, so enabling it changes nothing
-- for the app while shutting the other door. No policies are created on purpose: with none, the
-- API roles get no rows at all, which is exactly the intent.
--
-- The privilege revoke is belt and braces, and is guarded because `anon` and `authenticated` only
-- exist on Supabase. Enabling row level security itself is plain Postgres and runs anywhere.
--
-- flyway_schema_history is deliberately NOT touched here, and every statement below names its
-- tables instead of using "all tables in schema". Flyway keeps a second connection idle inside a
-- transaction holding a lock on its own history table for the whole run, so an ALTER or REVOKE
-- against it from inside a migration waits on that lock forever and the application never starts.
-- Securing that table is a one-off step run outside Flyway: see docs/SUPABASE.md.
do $$
declare
    app_table text;
begin
    foreach app_table in array array[
        'bag_model', 'bag_profile', 'disc', 'disc_embedding', 'plastic_type',
        'spring_session', 'spring_session_attributes'
    ]
    loop
        execute format('alter table public.%I enable row level security', app_table);
    end loop;
end
$$;

do $$
declare
    api_role text;
    app_table text;
begin
    foreach api_role in array array['anon', 'authenticated']
    loop
        if exists (select 1 from pg_roles where rolname = api_role) then
            foreach app_table in array array[
                'bag_model', 'bag_profile', 'disc', 'disc_embedding', 'plastic_type',
                'spring_session', 'spring_session_attributes'
            ]
            loop
                execute format('revoke all on table public.%I from %I', app_table, api_role);
            end loop;
            -- Supabase grants these roles access to every NEW table by default; stop that, so a
            -- table added by a later migration is closed from the start rather than open until
            -- someone remembers.
            execute format('alter default privileges in schema public revoke all on tables from %I', api_role);
            execute format('alter default privileges in schema public revoke all on sequences from %I', api_role);
        end if;
    end loop;
end
$$;
