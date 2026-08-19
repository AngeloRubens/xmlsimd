# simdxml-java: architecture

## Purpose

simdxml-java is a forward-only XML processing pipeline designed to keep byte classification,
grammar validation and object binding separate. The design has two goals:

1. make the common parsing path allocation-light and predictable;
2. provide compatibility facades for JAXB and SOAP runtimes without putting framework code in the
   parser's hot loops.

The implementation is not a DOM replacement. A DOM is created only when an explicitly requested
cold-path adapter needs one; the primary API consumes an event stream or byte slices.

## Processing pipeline

```text
byte[] / ByteBuffer / MemorySegment
              |
              v
   optional UTF-8 validation
              |
              v
  Stage 1: structural classification
  Vector API | Unsafe | VarHandle/SWAR | scalar
              |
              v
  Stage 2: XML grammar and namespace state
  pull reader | flyweight callbacks | binding events
              |
              v
  projections
  generic XML | JAXB binding | SOAP/HL7 | ISO 20022/payments
              |
              v
  compatibility facades
  Jakarta JAXB | Javax JAXB | CXF/Metro/Axis2 adapters
```

### Stage 1 — structural classification

Stage 1 scans bytes for XML structural candidates (`<`, `>`, quotes, comment/CDATA/PI markers and
ampersands). It does not interpret XML grammar. The Vector backend uses nibble lookup tables and
vector shuffles; the non-Vector backends use unrolled 64-bit SWAR loads or safe scalar loads.
Structural buffers are reused between iterations where the caller uses the reusable API.

### Stage 2 — grammar and namespace resolution

Stage 2 consumes the structural index in document order. It validates element nesting, attributes,
entity references, UTF-8 policy and namespace scopes. Namespace frames are immutable-linked scopes;
unqualified names stay on a cheaper local-name path, while qualified names are compared as expanded
`{namespace-uri}local-name` values. No JSON-specific quote-mask algorithm is applied to XML.

### Projections and vertical profiles

The generic projection materializes the requested event values. Byte and direct-memory projections
instead expose callback-scoped offset/length views; a view must not outlive the callback. SOAP,
healthcare and payment profiles are optional accelerators, not alternate correctness rules. Each
profile can be selected explicitly, discovered once, or disabled for A/B measurements and must
produce the same checksum as the generic projection.

The payment profile uses precomputed byte lookup tables, bounded name hashes, exact collision checks
and numeric parsing directly from byte slices. SOAP/HL7 profiles recognize protocol metadata while
leaving payload semantics to the generic binder.

## Backends and Java compatibility

The heap parser is available on the Java 8-compatible scalar/SWAR path. Newer optional artifacts
provide VarHandle, Unsafe, Vector API, virtual-thread and direct/FFM implementations. Capability
selection occurs once during construction; the hot loop contains no reflection or provider dispatch.

`AbstractXmlParser` owns only immutable policy (maximum depth, UTF-8 mode and profile selection).
Backend-specific byte loads remain in concrete implementations so the JIT sees monomorphic access
patterns. Shared syntax tables and byte classifiers are immutable and safe to reuse.

## JAXB and application-server integration

`XmlBindingMetadata` precomputes annotations, constructors, properties, namespace names and access
strategies. `XmlBinder` executes that plan; annotation reflection is not performed for every XML
event. Jakarta and Javax providers expose their native API packages and share the core metadata
engine without linking the opposite API into the deployment.

Hot-path unmarshal/marshal operations use the simdxml binder. Cold-path operations required by a
container—schema generation, WSDL tooling and other provider-specific facilities—delegate to the
JAXB implementation already supplied by the application server or to the optional JAXB RI. This
keeps CXF, Metro, Axis2, GlassFish and Java EE 8 integration compatible without moving cold-path
work into the scanner.

## Thread safety and ownership

Immutable metadata, syntax tables and profile configuration are shareable. A parser executor and a
marshaller/unmarshaller retain request-local mutable state and are therefore not shared concurrently,
matching JAXB's lifecycle rules. The thread-safe facades lease an exclusive parser context per
operation: platform threads use confined contexts and virtual threads borrow from a bounded pool.
The lease covers the complete callback, so flyweight views cannot observe another request's data.

## Framework adapters

CXF, Metro/JAX-WS and Axis2 adapters route through the same SOAP projection. When a framework still
owns raw transport bytes, the adapter can preserve the byte-oriented path. When it supplies an
`XMLStreamReader`, simdxml consumes that reader without constructing a DOM or reparsing the message.
Transport, API compatibility and parser policy remain separate modules, which keeps the core
extensible and testable.
