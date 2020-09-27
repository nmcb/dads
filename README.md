## DADS

Provides bucketed counter storage.

```
SourceId       : java.util.UUID        -- Type 5
Instant        : java.time.Instant     -- scala.Long since Unix EPOCH in millis
ChronoUnit     : java.time.ChronoUnit  -- Hours, Days, Months, Years
CassandraTable : String                -- Cassandra storage identifier
Value          : scala.Long            -- An (under normal operations positive) integer

Bucket         : ChronoUnit -> ChronoUnit -> CassandraTable -- Counter identifier indirection
CounterOn      : Instant    -> Bucket                       -- Counter identifier indirected
Adjustment     : SourceId   -> Instant    -> Value

CounterAddTo   : CounterOn -> Adjustment -> Done               -- Counter mutator verb 
CounterGetFrom : CounterOn -> SourceId   -> Instant -> Value   -- Counter accessor verb
```


### Publish and Deploy in Development

To develop, start a local development environment:

```
% docker-compose -f docker-dev.yml up
```

To initialise cassandra for local development run:

```
% cqlsh -f src/main/resources/cassandra/initialise.dev.cql
% cqlsh -f src/main/resources/cassandra/dads.v1.cql
```

To test the local codebase against a local development environment:

```
% sbt test
```

To publish local to your development environment run:

```
% sbt docker:publishLocal
```

To deploy the application in development (test production locally) run:

```
% docker-compose -f docker-app.yml up
```

### Publish and Deploy in Production

To initialise cassandra for application production run:

```
% cqlsh -f src/main/resources/cassandra/initialise.prd.cql
% cqlsh -f src/main/resources/cassandra/dads.v1.cql
```

To deploy in production run:

```
% sbt docker:publishLocal
% docker-compose docker-prd.yml up
```
