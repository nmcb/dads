/*
 * This is free and unencumbered software released into the public domain.
 */

create keyspace if not exists "aurum"
  with replication =
      { 'class'              : 'SimpleStrategy'
      , 'replication_factor' : 1
      };

create keyspace if not exists "dads_v1"
  with replication =
      { 'class'              : 'SimpleStrategy'
      , 'replication_factor' : 1
      };
