/*
 * This is free and unencumbered software released into the public domain.
 */

use aurum;

-- Cumulative tables
create table if not exists day (
    source  uuid,
    rowtime timestamp, -- day
    coltime timestamp, -- hour
    value   counter,
    primary key ((source, rowtime), coltime)
);

create table if not exists month (
    source  uuid,
    rowtime timestamp, -- month
    coltime timestamp, -- day
    value   counter,
    primary key ((source, rowtime), coltime)
);

create table if not exists year_week (
    source  uuid,
    rowtime timestamp,   -- year
    coltime timestamp,   -- (start of) week
    value   counter,
    primary key ((source, rowtime), coltime)
);

create table if not exists year (
    source  uuid,
    rowtime timestamp, -- year
    coltime timestamp, -- month
    value   counter,
    primary key ((source, rowtime), coltime)
);

create table if not exists forever (
    source  uuid,
    rowtime timestamp, -- 1 jan 1970, epoch time 0
    coltime timestamp, -- year
    value   counter,
    primary key ((source, rowtime), coltime)
);

use dads_v1;

-- Realtime tables
create table realtime_decimal (
    source uuid,
    time   timestamp,
    value  decimal,
    primary key (source, time)
) with default_time_to_live = 3600;

-- Precaution only, TTL should be included with an INSERT/UPDATE.
-- Taken from AP StorageProfile.realTimeTTL (Postgres)

-- Realtime (for later)
-- Just to give an idea of the future.
-- create table realtime_string (
--     source        uuid,
--     time          timestamp,
--     value         varchar,
--     primary key (source, time)
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

