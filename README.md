## DADS

---
### Publish and Deploy

To deploy in development run:

```
% sbt docker:publishLocal
% docker-compose up
```

To deploy in production run:

```
% sbt docker:publishLocal
% docker-compose docker-production.yml up
```
