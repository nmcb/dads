
create keyspace if not exists dads_v1
    with replication =
        { 'class'       : 'NetworkTopologyStrategy'
        , 'datacenter1' : '3'
        , 'datacenter2' : '2'
        };
