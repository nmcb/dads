/*
 * This is free and unencumbered software released into the public domain.
 */



-- Counter tables
-- TODO Storage model
-- - bucket ~= table disallows dynamic dispatch
-- - missing green/blue and prd/dev data markers (options-bits?)

create table if not exists "aurum"."day" (
    source  uuid,
    rowtime timestamp, -- day
    coltime timestamp, -- hour
    value   counter,
    primary key ((source, rowtime), coltime)
);

create table if not exists "aurum"."month" (
    source  uuid,
    rowtime timestamp, -- month
    coltime timestamp, -- day
    value   counter,
    primary key ((source, rowtime), coltime)
);

create table if not exists "aurum"."year_week" (
    source  uuid,
    rowtime timestamp,   -- year
    coltime timestamp,   -- (start of) week
    value   counter,
    primary key ((source, rowtime), coltime)
);

create table if not exists "aurum"."year" (
    source  uuid,
    rowtime timestamp, -- year
    coltime timestamp, -- month
    value   counter,
    primary key ((source, rowtime), coltime)
);

create table if not exists "aurum"."forever" (
    source  uuid,
    rowtime timestamp, -- 1 jan 1970, epoch time 0
    coltime timestamp, -- year
    value   counter,
    primary key ((source, rowtime), coltime)
);

-- RealTime tables
create table "dads_v1"."realtime_decimal" (
    source uuid,
    time   timestamp,
    value  decimal,
    primary key (source, time)
) with default_time_to_live = 3600;

-- Precaution only, TTL should be included with an INSERT/UPDATE.
-- Taken from AP StorageProfile.realTimeTTL (Postgres)

-- RealTime (for later)
-- Just to give an idea of the future.
-- create table realtime_string (
--     source        uuid,
--     time          timestamp,
--     value         varchar,
--     primary key (source, time)s
-- ) with default_time_to_live = 3600;

-- Possible table to facilitate state recovery and/or store the last known values.
-- Can be changed depending on needs.
-- create table source_state_decimal (
--     source            uuid,
--     last_seq_nr       int,
--     last_value        decimal,   -- May be null if necessary.
--     last_success_time timestamp, -- May be null if necessary.
--     primary key (source)
-- );

