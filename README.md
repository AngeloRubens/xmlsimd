# simdxml-java

[Architecture](ARCHITECTURE.md) · [Compatibility](COMPATIBILITY.md) ·
[Benchmarks](BENCHMARKS.md) · [Contributing](CONTRIBUTING.md) · [Security](SECURITY.md)

> **Status: technical preview.** The project is suitable for reproducible experiments and early
> integration work. It is not yet a certified replacement for JAXB, Woodstox, GlassFish or any
> SEPA/healthcare validator. See the [publication readiness checklist](PUBLICATION.md) before
> making compatibility or performance claims.

Experimental, dependency-free XML parser for Java, inspired by the two-stage design of
[`simdjson-java`](https://github.com/simdjson/simdjson-java).

## The idea

XML is often treated as intrinsically slow because general-purpose implementations combine
decoding, allocation, validation and object binding in one path. simdxml-java separates those
concerns. A byte-oriented first stage identifies structural candidates using Vector API, SWAR,
Unsafe or VarHandle backends; a forward-only second stage resolves XML grammar state. JAXB-style
binding is precompiled once per context, while schema generation and other cold operations can
delegate to the runtime already installed by an application server.

The result is one portable design with progressively stronger backends, rather than one JDK-specific
implementation: Java 8-compatible scalar/SWAR code remains available, while newer JDKs can opt into
Vector API, VarHandle, Unsafe, direct-memory and virtual-thread integrations. SOAP, HL7/CDA/FHIR and
ISO 20022 projections are switchable so their cost can be measured instead of hidden in a generic
parser result.

## Why teams may care

The Jakarta and Javax provider artifacts are designed for dependency-level integration: existing
`JAXBContext.newInstance(...)` application code remains the public entry point. The same immutable
context can be shared, while marshaller/unmarshaller instances retain JAXB's normal request/thread
lifecycle. This makes the project a candidate for controlled CXF, Metro, GlassFish and Java EE 8
experiments without requiring an application rewrite.

The headline numbers currently in [BENCHMARKS.md](BENCHMARKS.md) are local preliminary measurements
on a two-core host. They are deliberately accompanied by JVM flags, heap size, GC, checksum,
dataset and command lines. Re-run them on the target hardware before using them in a presentation.

Stage 1 validates UTF-8 and uses the JDK Vector API to locate XML structural bytes in parallel.
Stage 2 consumes those indexes while checking nesting and builds a small, immutable-facing DOM.

The primary API is forward-only streaming; DOM construction is optional. A JAXB-compatible binder
maps the event stream directly to annotated no-argument classes.

```java
byte[] xml = "<catalog><book id='7'>SIMD XML</book></catalog>".getBytes(UTF_8);
SimdXmlParser parser = new SimdXmlParser();
XmlDocument document = parser.parse(xml, xml.length);
String title = document.root().child("book").orElseThrow().text();
```

```java
SimdXmlStreamReader reader = parser.stream(xml);
while (reader.hasNext()) {
    if (reader.next() == XmlEvent.START_ELEMENT) System.out.println(reader.name());
}
```

```java
@XmlRootElement(name = "book")
public final class Book {
    @XmlAttribute public int id;
    @XmlValue public String title;
    public Book() {}
}
Book book = parser.parse("<book id='7'>SIMD XML</book>".getBytes(UTF_8), Book.class);
```

## Build

Java 24 is required because the implementation uses the incubating Vector API.

```shell
export JAVA_HOME=/path/to/jdk-24
export PATH="$JAVA_HOME/bin:$PATH"
mvn test
```

Compilation is tested with JDK 24; benchmark execution is performed with the local Temurin 25
HotSpot Server VM so that Vector API compilation and runtime measurements remain separate.

## Supported

- UTF-8 documents, optional BOM and XML 1.0/1.1 declaration
- elements, attributes, empty elements and mixed content
- comments, CDATA and processing instructions
- the five predefined entities and decimal/hex character references
- namespace-qualified names preserved verbatim
- configurable capacity and maximum nesting depth
- forward-only streaming and direct Jakarta JAXB annotation binding

At runtime the parser automatically selects the Vector API backend. If the incubator module is not
available it falls back to an optimized 64-bit SWAR scanner. Force a backend with
`-Dorg.simdxml.indexer=scalar` or `-Dorg.simdxml.indexer=vector`.

Available backend values are `auto`, `vector`, `unsafe`, `varhandle` (with `scalar` as an alias).
Automatic selection tries Vector first, then Unsafe, then the portable VarHandle implementation.

SOAP/HL7 metadata projection has an independently switchable vertical path. It is enabled by
default and can be disabled either explicitly with the three-argument parser constructor or with:

```shell
-Dorg.simdxml.vertical.enabled=false
```

Gateways may fix the vertical family once and avoid per-message discovery:

```java
SimdXmlParser payments = SimdXmlParser.builder()
        .withVerticalProfile(VerticalProfile.PAYMENTS).build();
VerticalMessageInfo info = payments.inspectVertical(xml);
```

The equivalent JVM property is `-Dorg.simdxml.vertical.profile=payments`; supported initial values
are `auto`, `none`, `soap-healthcare` and `payments`. `AUTO` performs a bounded allocation-free byte
probe. Explicit `inspectPayment`/`inspectHealthcare` methods remain the lowest-dispatch API.

Both settings use the forward-only reader; disabling the vertical path does not switch to DOM.
The optimized path compares pre-encoded protocol tokens using the local-name FNV hash accumulated
during the existing byte scan, followed by an exact byte comparison to rule out collisions.

Whole-document UTF-8 validation is strict by default. For input that has already been validated by
a trusted upstream boundary, the independent validation pass can be disabled with
`-Dorg.simdxml.utf8.validation=none` or the four-argument parser constructor and
`Utf8Validation.NONE`. XML structural and XXE protections remain active; malformed UTF-8 may then be
replaced during Java string decoding, so this mode must not be used as an input-validation boundary.
Strict validation has independently selectable implementations via
`-Dorg.simdxml.utf8.strategy=auto|vector|swar`. In `auto`, documents up to 4096 bytes use the
lower-setup-cost SWAR validator and larger documents use Vector when its runtime module is available;
override the crossover with `-Dorg.simdxml.utf8.vector.threshold=<bytes>`.
The strict slow path uses byte lookup tables for legal sequence width and second-byte bounds
(overlong encodings, surrogates, and values above U+10FFFF), avoiding substring parsing and keeping
the same canonical checks in Vector and non-Vector configurations.

The pull reader keeps element names and entity-free text as UTF-8 byte slices. Java `String`
instances are materialized only when `name()` or `text()` is requested; empty attribute sets do not
allocate a map. Tag matching, protocol probes, and nesting continue to operate directly on bytes.
Documents up to 4096 bytes use single-pass direct delimiter search instead of constructing stage 1;
larger inputs retain the SIMD two-stage pipeline. Configure this crossover with
`-Dorg.simdxml.tiny.threshold=<bytes>` or set it to zero to force two-stage operation.

High-volume one-parser-per-thread pipelines can use `reusableStream(byte[])`. It resets a
parser-owned reader with compact stack, inline small-attribute map, and a bounded persistent UTF-8
symbol table. The previous reader view becomes invalid at reset, following a flyweight lifecycle.
For SBE/Chronicle-style processing, `nameBytes()` and `rawTextBytes()` return reader-owned
`XmlByteSlice` flyweights and `scanReusable(byte[], XmlEventConsumer)` drives a reusable callback
loop. These APIs avoid UTF-16 materialization; raw text slices are intentionally before XML entity
expansion and must be consumed or copied before the next event/reset.

The Fury-style builder selects concurrency explicitly:

```java
SimdXmlParser singleThread = SimdXmlParser.builder().withCapacity(capacity).build();
ThreadSafeSimdXmlParser shared = SimdXmlParser.builder().withCapacity(capacity).buildThreadSafe();
```

The shared facade uses one lock-free `ThreadLocal` context for platform threads. Virtual threads use
a bounded parser pool for synchronous parse, binding, SOAP/HL7 inspection, and callback scans so a
parser is not allocated per request. A returned streaming reader receives a dedicated context on a
virtual thread because its structural index must remain leased for the reader lifetime.

Run the complete test suite on a JVM where the Vector module is not loaded:

```shell
mvn -Pscalar-runtime test
```

Test the Unsafe backend without loading the Vector module:

```shell
mvn -Punsafe-runtime test
```

DTDs and external/custom entities are deliberately rejected (avoiding XXE and expansion attacks).
This first version does not perform namespace resolution, XSD validation, encoding transcoding,
or schema-to-record binding. It is therefore a focused pull parser/object-tree core rather than a complete
replacement for a validating XML processor.

## Why XML needs a different stage 2

The SIMD classification idea transfers well from JSON, but XML quotes only delimit values inside
tags, and comments, CDATA and processing instructions each have their own terminator. Those states
remain scalar and branch-light in stage 2. This preserves correctness instead of pretending XML can
reuse JSON's quote-mask algorithm unchanged.

## Paper-derived optimizations

The implementation adapts techniques from Langdale and Lemire's *Parsing Gigabytes of JSON per
Second* where XML permits them:

- stage 1 emits compact structural indexes from block bitmasks;
- the Vector backend classifies XML punctuation with high/low-nibble lookup tables and two shuffles;
- the non-Vector backend processes four independent 64-bit SWAR words per loop iteration;
- UTF-8 validation has an allocation-free 64-bit ASCII fast path and validates canonical sequences;
- the reader is On-Demand/forward-only, so skipped subtrees do not become DOM objects;
- comment, CDATA and PI terminators are found using SWAR candidate masks followed by exact checks.

JSON's global quote prefix-XOR is intentionally not copied: XML quotes only delimit attribute values
inside markup and both quote kinds are ordinary text elsewhere. XML grammar state is therefore
resolved in stage 2. DTDs remain rejected, avoiding contextual entity expansion and XXE.

## 1BRC-derived optimizations and benchmark protocol

The streaming reader uses a compact primitive stack of byte-array slices and incremental FNV-1a
hashes for open element names. Closing tags check hash and length first, then compare bytes only for
the matching candidate. This adapts the allocation-light key handling used by leading 1BRC Java
entries without weakening XML nesting validation. Stage 1 reuses its structural-index buffer across
iterations; the Unsafe and VarHandle paths use unrolled 64-bit loads, while Vector keeps the nibble
lookup classifier.

`RealXmlBenchmark` supports `index` (stage 1 only) and `stream` modes. For GC-free stage-1 runs on
the local Temurin 25 runtime, use a fixed pre-touched heap and Parallel GC:

```shell
JAVA25_HOME=/path/to/jdk-25-jre
"$JAVA25_HOME/bin/java" -server \
  -XX:ActiveProcessorCount=2 \
  -Xms1500m -Xmx1500m -XX:+AlwaysPreTouch \
  -XX:+UseParallelGC -XX:+DisableExplicitGC -XX:-UsePerfData \
  --add-modules jdk.incubator.vector -Dorg.simdxml.indexer=vector \
  -cp target/classes:target/test-classes org.simdxml.RealXmlBenchmark \
  /path/to/simplewiki-stub-64m.xml 5 index
```

The benchmark prints a checksum; results are comparable only when every backend performs the same
projection. Each library/version is executed in its own JVM fork, strictly one JVM at a time on the
two-core benchmark host. Maven Surefire is likewise fixed to one reused fork with test parallelism
disabled.

`XmlLibraryBenchmark` provides streaming baselines for the JDK StAX implementation, Woodstox/StAX2
and Jackson XML. `HealthcareVerticalBenchmark` compares vertical-on, vertical-off and JAXB RI using
the same SOAP/HL7 metadata projection. External libraries are test-scoped and do not become runtime
dependencies of simdxml.

Woodstox 7.2.2 is the default baseline. The historical 6.7.0 implementation is retained for
regression comparisons through the `woodstox-6` Maven profile; versions must run in separate JVM
forks to avoid classpath mediation between identical artifact coordinates.

On the 395.42 MiB SimpleWiki stub dump, JRE 25, five measured iterations, Parallel GC and a fixed
1500 MiB heap, the current adaptive pull projection measured 137.66 MiB/s average. Woodstox 6.7.0 measured
123.06 MiB/s and Woodstox 7.2.2 measured 111.95 MiB/s under the same JVM flags. These are local
engineering measurements, not universal claims; rerun them on each target CPU/JVM.
With the independent UTF-8 pass disabled for trusted/prevalidated input, simdxml measured
133.86 MiB/s average and 136.21 MiB/s best on the same dump. The small difference reflects the
very cheap Vector ASCII fast path; Woodstox still decodes and checks UTF-8 as part of parsing rather
than exposing an equivalent switch that accepts arbitrary malformed input.

On the 882-byte SOAP fixture, the ordinary simdxml reader measured 163,289 documents/s and the
flyweight reader 183,932 documents/s, versus 115,753 for Woodstox 7.2.2. On the 1,809-byte
SOAP/HL7 fixture they measured 85,643, 93,990, and 69,594 documents/s respectively. These generic
benchmarks consume the complete event stream; the separate healthcare benchmark measures the
specialized metadata projection.

The byte-flyweight projection (`simdxml-bytes`) measured 236,734 documents/s on SOAP and 118,973
documents/s on SOAP/HL7. On Wiki it measured 190.29 MiB/s. This mode consumes raw UTF-8 slice
lengths rather than materialized Java string lengths, so it is reported separately from the
String-equivalent cross-library table.

## Direct memory and JAXB-style facade

`DirectSimdXmlParser` scans `MemorySegment` and direct/heap `ByteBuffer` without converting them to
`byte[]`. `DirectXmlByteSlice` keeps name/text views on the original memory. Long searches use the
Vector API when available and SWAR otherwise; native segments use Chronicle-style raw address loads
through the optional Unsafe backend, with safe MemorySegment access as fallback. The caller owns the
segment lifetime. Direct stage 1 is disabled by default because A/B testing showed single-pass native
address scanning faster; `-Dorg.simdxml.direct.index.threshold=<bytes>` can re-enable it experimentally.

```java
DirectSimdXmlParser direct = new DirectSimdXmlParser(256, Utf8Validation.STRICT);
direct.scan(directByteBuffer, (event, name, text) -> { /* consume flyweights now */ });
```

The extended callback also receives `DirectXmlAttributes`: names and raw values are mutable
flyweights over the original segment, so lookup, tag matching and attribute inspection stay in
bytes without transient `String` objects. As with the other flyweight APIs, a view must not be
retained after the next event. `DirectSimdXmlParser.inspectHealthcare` applies the same mechanism to
SOAP 1.1/1.2 and HL7 v3 projections. A Fury-style shareable facade supports both platform and virtual
threads without sharing mutable scanner state:

```java
ThreadSafeDirectSimdXmlParser shared = DirectSimdXmlParser.builder()
        .withMaxDepth(256).withUtf8Validation(Utf8Validation.STRICT).buildThreadSafe();
shared.scan(directByteBuffer, (event, name, text) -> { /* consume immediately */ });
```

`SimdJaxbContext` is an optimized JAXB-style decorator. It prewarms annotation metadata, child-name
maps and constructors once. `SimdUnmarshaller` is reusable and non-thread-safe like Jakarta JAXB's
Unmarshaller; `SimdJaxbContext.unmarshal` is shareable and uses the platform/virtual-thread strategy.
The immutable annotation model (`XmlBindingMetadata`) is separate from the streaming executor
(`XmlBinder`), so reflection never occurs inside the XML event loop.

```java
SimdJaxbContext context = SimdJaxbContext.builder(Catalog.class).withCapacity(capacity).build();
SimdUnmarshaller unmarshaller = context.createUnmarshaller();
Catalog value = unmarshaller.unmarshal(xml, Catalog.class);
```

Field writes have independently selectable backends for reproducible A/B testing:

```shell
-Dorg.simdxml.binding.access=reflection
-Dorg.simdxml.binding.access=varhandle
```

`auto` (the default) currently selects warmed reflection. On the local JRE 25 benchmark with an
863-byte/32-book object graph and 200,000 measured bindings, reflection reached 79,689 objects/s
versus 72,833 for precompiled VarHandles (about 9.4% faster). VarHandles remain available for
restricted deployments or further JVM-specific testing; forcing them fails early at context
prewarm if module access is unavailable.

The platform/virtual-thread ownership policy is centralized in `ParserContextPool`, shared by heap
and direct facades. It remains outside scanner loops: stage 1, UTF-8 validation, byte flyweights and
stage 2 retain their monomorphic hot paths.

Latest local JRE 25 strict-validation results: on Wiki, ordinary/reusable/byte-flyweight/direct were
134.45/139.38/191.15/229.56 MiB/s; Woodstox 7.2.2, Jackson+Woodstox, JDK StAX and Xerces SAX were
111.76/100.56/97.46/137.09 MiB/s. On 882-byte SOAP, all-feature byte/direct reached 234,439 and
215,819 documents/s versus Woodstox 117,617. On 1,809-byte SOAP/HL7 they reached 118,598 and
131,416 versus Woodstox 68,660. Equivalent 863-byte object binding measured 76,231 objects/s for
simdxml JAXB-style binding and 12,630 for JAXB RI. Raw-byte and callback projections are intentionally
reported separately from String/object projections.
