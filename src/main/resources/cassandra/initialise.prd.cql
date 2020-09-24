/*
 * This is free and unencumbered software released into the public domain.
 */

create keyspace if not exists "dads_v1"
    with replication =
        { 'class'       : 'NetworkTopologyStrategy'
        , 'datacenter1' : '3'
        , 'datacenter2' : '2'
        };
