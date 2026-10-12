-- What a signed-in player has told us about themselves, so it follows them to every browser and
-- device instead of living in one tab: skill, how they entered it (level or PDGA rating), throwing
-- style, usual course and light/dark theme.
--
-- One row per owner, keyed like bag_profile.owner_key ("user:<uuid>"). The settings are a jsonb
-- document rather than one column per field: the player profile is about to grow (throw distances
-- per disc class, forehand and backhand), and each addition would otherwise be a migration. The
-- application validates the document on write, so what is stored is always readable.
create table player_settings (
    owner_key  varchar(128) primary key,
    settings   jsonb        not null default '{}'::jsonb,
    updated_at timestamptz  not null default now()
);

-- Same lock-out as the other application tables (see V9): the app connects as the table owner and
-- is unaffected, while Supabase's REST API gets no rows.
alter table public.player_settings enable row level security;

do $$
declare
    api_role text;
begin
    foreach api_role in array array['anon', 'authenticated']
    loop
        if exists (select 1 from pg_roles where rolname = api_role) then
            execute format('revoke all on table public.player_settings from %I', api_role);
        end if;
    end loop;
end
$$;
