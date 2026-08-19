# Contributing

Changes must preserve correctness before performance. New optimized paths require:

1. a generic reference path;
2. differential tests with identical semantics and checksum;
3. an explicit feature switch for vertical optimizations;
4. Vector and scalar-runtime tests;
5. before/after measurements from separate, sequential JVM processes;
6. no generated benchmark result committed without environment and dataset metadata.

Use JDK 24 to compile. The local project convention is one Maven/Surefire JVM at a time with
`-XX:ActiveProcessorCount=2`. Run:

```shell
mvn test
mvn -Pscalar-runtime test
mvn -Punsafe-runtime test
mvn -f integrations/pom.xml test
```

Hot-loop changes should avoid allocation, polymorphic dispatch and `String` materialization unless
the benchmark projection explicitly requires strings. Public APIs require Javadoc and tests for
ownership, thread safety and flyweight lifetime.
