/*
 * This is free and unencumbered software released into the public domain.
 */

create keyspace if not exists dads_v1
    with replication =
        { 'class'       : 'NetworkTopologyStrategy'
        , 'DC1'         : '2' -- We should probably fix this to 'datacenter1' for consistency.
        , 'datacenter2' : '2'
        };
