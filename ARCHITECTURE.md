# Architecture

The project keeps optional compatibility layers outside the core and keeps extension dispatch out
of per-byte loops.

```text
byte[] / MemorySegment
        |
 UTF-8 validation ---- configurable strict/none
        |
 stage 1 ------------ Vector / Unsafe / VarHandle-SWAR / scalar
        |
 stage 2 ------------ pull reader / flyweight callback / optional tree
        |
projections -------- generic binding / SOAP-healthcare / payment verticals
        |
 bridges ------------ Jakarta JAXB / Java EE 8 JAXB / framework adapters
```

`SimdXmlParser` and `DirectSimdXmlParser` are reusable but not thread-safe. Their shareable facades
delegate ownership to `ParserContextPool`: platform threads receive a `ThreadLocal` context and
virtual threads borrow from a bounded pool. No parser instance is concurrently entered.

Both concrete parsers extend the deliberately thin `AbstractXmlParser`, which owns only immutable
cross-backend policy such as maximum depth and UTF-8 validation. XML byte tokens and classification
tables are composed through `XmlByteUtils`. Memory reads, delimiter searches and stage-2 loops are
not abstract methods: they remain duplicated where necessary in the heap and direct backends so the
JIT sees monomorphic byte/word access in every hot loop.

The portable heap backend targets Java 8 and uses explicit little-endian SWAR loads without linking
VarHandle, Vector API, FFM or virtual-thread methods. Modern capabilities are selected once through
providers loaded by class name: JDK 9+ field access, JDK 21+ thread classification, Vector backends
and JDK 22+ FFM/direct access can therefore remain outside the Java 8 linkage surface.

The Maven distribution enforces that boundary with separate `simdxml-core-java8`, Unsafe,
VarHandle, Vector, virtual-thread and direct/FFM artifacts under `compatibility/`. The combined root
artifact remains a development/benchmark reactor until downstream integration modules have moved to
the split coordinates.

JAXB annotation discovery, constructors and field access are precompiled in
`XmlBindingMetadata`; `XmlBinder` only executes that plan. Jakarta and Java EE 8 providers expose
different binary APIs but delegate to the same core metadata and parser. Namespace-specific bridge
wrappers must contain no parsing algorithms. The core metadata has no static linkage to either
annotation package: it recognizes both binary names and leaves the respective API dependency in its
provider artifact, preventing a Jakarta JAXB jar from leaking into a Java EE 8/WebLogic deployment.

Vertical projections are optional accelerators, never alternative correctness rules. Every vertical
must produce the same result as its generic projection and be switchable for A/B measurements.
The payment path applies the same 1BRC/SBE rules as the core: precomputed FNV hashes reject most
names, exact byte comparisons reject collisions, numeric counters parse from flyweight bytes and
only fields present in `PaymentMessageInfo` become strings.

Payment and SOAP/healthcare projections also expose callback-scoped read-only flyweights. Plain
contiguous values retain only source offset/length; entity-expanded or fragmented XML falls back to
a materialized value to preserve XML semantics. A thread-safe facade holds its parser context lease
for the entire callback. Platform-thread contexts are confined and virtual threads borrow exclusive
contexts from the bounded pool.

CXF, Metro/JAX-WS and Axis2 adapters must delegate to this shared SOAP vertical engine. Transport
adapters should pass raw bytes to preserve stage-1 and zero-copy acceleration; an `XMLStreamReader`
compatibility path is permitted when a framework has already parsed the transport, but must not
construct a DOM or parse the message a second time.
