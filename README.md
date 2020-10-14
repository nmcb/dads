## DADS (yet another four letter acronym)

Provides for humanly understandable time unit bucketed counter storage.

```
SourceId       : java.util.UUID        -- Type 5
Instant        : java.time.Instant     -- scala.Long since Unix EPOCH in millis
ChronoUnit     : java.time.ChronoUnit  -- Hours, Days, Months, Years, i.e. [HOURS DAYS MONTHS YEARS]                                       
CassandraTable : scala.String          -- Cassandra storage identifier
Value          : scala.Long            -- An (under normal "counter" operations positive) integer

                                                            -- FIXME model (Forever,Years)
Bucket         : ChronoUnit -> ChronoUnit -> CassandraTable -- Counter identifier indirection
CounterOn      : Instant    -> Bucket                       -- Counter identifier indirected
Adjustment     : SourceId   -> Instant    -> Value

CounterAddTo   : CounterOn -> Adjustment -> Done               -- Counter mutator verb
CounterGetFrom : CounterOn -> SourceId   -> Instant -> Value   -- Counter accessor verb
```

Additionally, it provides for future humanly understandable real-time decimal storage.

```
Decimal     : SourceId -> Instant -> Value

Number a where
  toValue a   : Value
  fromValue v : Number

Set         : Number a => a -> Done
LastGetLast : Number a => Sourceid -> Number a
```

Real-time storage is provided in-memory and persistent; latter storage form with a 1 hour TTL.

### Publish and Deploy in Development

To develop from within sbt, start a local development environment outside:

```
% docker-compose -f docker-dev.yml up
```

To initialise cassandra for local development run:

```
% cqlsh -f src/main/resources/cassandra/initialise.dev.cql
% cqlsh -f src/main/resources/cassandra/dads.v1.cql
```

To test the development codebase against a local development environment:

```
% sbt test
```

To generate a Docker image in your development machine run:

```
% sbt docker:publishLocal
```

To deploy the application in development (test production locally) run:

```
% docker-compose -f docker-app.yml up
```

### Publish and Deploy in production

Deployment in production is provided automagically. 
