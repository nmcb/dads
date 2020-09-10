## DADS

Provides a measurement store.

### Publish and Deploy in Development

To develop, start a local development environment run:

```
% docker-compose -f docker-dev.yml up
```

To initialise cassandra for local development run:

```
% cqlsh -f src/test/resources/cassandra/initialise.local.cql
% cqlsh -f src/main/resources/cassandra/cassandra.v1.cql
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
% cqlsh -f src/main/resources/cassandra/initialise.prod.cql
% cqlsh -f src/main/resources/cassandra/cassandra.v1.cql
```

To deploy in production run:

```
% sbt docker:publishLocal
% docker-compose docker-prd.yml up
```
