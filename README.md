# simdxml-java

simdxml-java is a byte-oriented, forward-only XML parser and JAXB-compatible binding engine for
Java. It separates structural scanning, XML grammar validation and object binding so each stage can
be optimized and benchmarked independently.

> **Status: technical preview.** The project is intended for reproducible evaluation and controlled
> integration experiments. It is not a JAXB, JAX-WS, Woodstox, GlassFish, SEPA or healthcare TCK
> certification. See [PUBLICATION.md](PUBLICATION.md) before making compatibility or performance
> claims.

[Architecture](ARCHITECTURE.md) · [Benchmarks](BENCHMARKS.md) · [Compatibility](COMPATIBILITY.md) ·
[Contributing](CONTRIBUTING.md) · [Security](SECURITY.md) · [Release procedure](RELEASE.md)

## Design summary

The parser uses a two-stage pipeline:

1. **Structural classification** scans UTF-8 bytes using Vector API, Unsafe, VarHandle/SWAR or
   scalar backends and produces compact structural candidates.
2. **Grammar resolution** validates nesting, attributes, entities, namespaces and XML token state
   with a forward-only reader.

The same event stream feeds generic XML projections, byte flyweights, direct/off-heap callbacks,
JAXB-style binding and protocol-specific profiles for SOAP/HL7 and ISO 20022. Cold operations such
as schema generation delegate to the JAXB implementation supplied by the application server or the
optional JAXB RI.

The core contains no DOM dependency and does not construct a tree unless an explicitly requested
adapter needs one. See [ARCHITECTURE.md](ARCHITECTURE.md) for the complete component and ownership
model.

## Highlights

- UTF-8 strict validation with a trusted-input opt-out.
- Vector, Unsafe, VarHandle/SWAR and scalar structural scanners.
- Forward-only pull reader with reusable and callback-scoped flyweight APIs.
- Direct `ByteBuffer`/`MemorySegment` support without a mandatory `byte[]` copy.
- Namespace-aware JAXB-style marshal and unmarshal.
- Jakarta JAXB and Javax JAXB provider artifacts with the same core metadata engine.
- Optional SOAP 1.1/1.2, HL7/CDA/FHIR and ISO 20022 projections.
- Thread-safe facades with exclusive context ownership and virtual-thread support.
- No DTD or external entity expansion, reducing XXE and entity-expansion risk.

## Minimal streaming example

```java
import java.nio.charset.StandardCharsets;
import org.simdxml.SimdXmlParser;
import org.simdxml.SimdXmlStreamReader;
import org.simdxml.XmlEvent;

byte[] xml = "<catalog><book id='7'>SIMD XML</book></catalog>"
        .getBytes(StandardCharsets.UTF_8);

SimdXmlParser parser = new SimdXmlParser();
SimdXmlStreamReader reader = parser.stream(xml);
while (reader.hasNext()) {
    if (reader.next() == XmlEvent.START_ELEMENT) {
        System.out.println(reader.name());
    }
}
```

## JAXB-style binding

```java
import java.nio.charset.StandardCharsets;
import org.simdxml.SimdJaxbContext;
import org.simdxml.SimdUnmarshaller;

@jakarta.xml.bind.annotation.XmlRootElement(name = "book")
public final class Book {
    @jakarta.xml.bind.annotation.XmlAttribute public int id;
    @jakarta.xml.bind.annotation.XmlValue public String title;
    public Book() {}
}

SimdJaxbContext context = SimdJaxbContext.builder(Book.class).build();
SimdUnmarshaller unmarshaller = context.createUnmarshaller();
Book book = unmarshaller.unmarshal(
        "<book id='7'>SIMD XML</book>".getBytes(StandardCharsets.UTF_8), Book.class);
```

`SimdJaxbContext` is shareable after construction. Marshaller and unmarshaller instances retain
JAXB-compatible request ownership rules; use the thread-safe facade when one object is accessed by
multiple platform or virtual threads.

## Runtime configuration

Backend selection is performed once when the parser is built:

```text
-Dorg.simdxml.indexer=auto|vector|unsafe|varhandle|scalar
-Dorg.simdxml.utf8.validation=strict|none
-Dorg.simdxml.utf8.strategy=auto|vector|swar
-Dorg.simdxml.vertical.enabled=true|false
-Dorg.simdxml.vertical.profile=auto|none|soap-healthcare|payments
```

For trusted, prevalidated input, `Utf8Validation.NONE` removes the independent UTF-8 pass; it must
not be used as an input-validation boundary. Disabling vertical profiles does not change XML
correctness or switch the parser to DOM.

Explicit protocol selection avoids per-message discovery:

```java
SimdXmlParser payments = SimdXmlParser.builder()
        .withVerticalProfile(VerticalProfile.PAYMENTS)
        .build();
VerticalMessageInfo info = payments.inspectVertical(xml);
```

For byte-oriented high-throughput consumers, `reusableStream`, `scanReusable`, `nameBytes()` and
`rawTextBytes()` expose callback-scoped views. A flyweight must be consumed or copied before the
next event or parser reset.

## Java and framework compatibility

The core portable path is Java 8-compatible. The Maven reactor also provides optional artifacts for
VarHandle, Unsafe, Vector API, virtual threads and direct/FFM access. The main development and
Vector compilation profile uses JDK 24.

Provider and integration modules cover:

- Jakarta XML Binding and Java EE 8/Javax XML Binding;
- Quarkus CXF, downloaded Tomcat and Open Liberty smoke deployments;
- provider-neutral JAXB compatibility tests;
- selected W3C XML, ISO 20022, healthcare and framework fixtures.

Framework adapters keep transport concerns outside the parser. CXF, Metro/JAX-WS and Axis2 can
delegate JAXB operations to the provider while retaining their existing application APIs.

## Build and test

JDK 24 is required for the main compilation profile:

```shell
export JAVA_HOME=/path/to/jdk-24
export PATH="$JAVA_HOME/bin:$PATH"
mvn -B -ntp test
```

Useful compatibility gates are intentionally opt-in and sequential:

```shell
mvn -B -ntp -f compatibility/pom.xml \
  -Piso20022-validation -pl :simdxml-iso20022-validation -am test
mvn -B -ntp -f compatibility/pom.xml \
  -Phealthcare-validation -pl :simdxml-healthcare-validation -am test
mvn -B -ntp -f integrations/pom.xml \
  -Pquarkus-cxf-it -pl :simdxml-server-smoke-quarkus -am test
```

The complete reproducible release checklist is in [RELEASE.md](RELEASE.md). GitHub Actions runs
the compatibility, application-server and performance workflows sequentially where measurements
would otherwise interfere with each other.

## Performance snapshot

The following local JRE 25 measurements use two active processors, a fixed 1,500 MiB heap,
Parallel GC and one JVM per implementation. They are preliminary engineering measurements; the
full methodology, checksums, fixture sizes and raw artifact policy are in [BENCHMARKS.md](BENCHMARKS.md).

| workload | simdxml | reference | delta |
|---|---:|---:|---:|
| SimpleWiki ordinary | 134.45 MiB/s | Woodstox 7.2.2: 111.76 MiB/s | **+20.3%** |
| SimpleWiki byte-flyweight | 191.15 MiB/s | Woodstox 7.2.2: 111.76 MiB/s | **+71.1%** |
| SimpleWiki direct-memory | 229.56 MiB/s | Woodstox 7.2.2: 111.76 MiB/s | **+105.4%** |
| SOAP, 882 B | 234,439 documents/s | Woodstox 7.2.2: 117,617 documents/s | **+99.3%** |
| SOAP + HL7, 1,809 B | 131,416 documents/s direct | Woodstox 7.2.2: 68,660 documents/s | **+91.4%** |
| JAXB unmarshal graph | 22,320 objects/s | JAXB RI: 8,668 objects/s | **+157.6%** |
| JAXB marshal graph | 47,651 objects/s | JAXB RI: 27,142 objects/s | **+75.6%** |

The projections in the table do not all have identical allocation models: byte-flyweight and direct
rows are reported separately from String-materializing competitors. Every published comparison must
retain the same semantic checksum and document projection.

## Security and scope

DTD declarations and external/custom entities are rejected by design. XML structural checks remain
active when trusted-input UTF-8 validation is selected. XSD validation, schema generation and other
cold-path facilities are delegated to the configured runtime rather than implemented in the scanner.
See [SECURITY.md](SECURITY.md) for the threat model and reporting process.

## License

See [LICENSE](LICENSE).
