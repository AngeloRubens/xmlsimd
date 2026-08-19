# simdxml compatibility kit

Compatibility claims are tied to named, executable profiles. A profile is 100% only when every
listed case passes without disabled or ignored tests. Reference comparisons run sequentially.

External W3C, JAXB RI, Metro/CXF and application-server corpora are kept outside the core unit
suite and pinned by revision. The Jakarta JAXB source-corpus adapter for the Javax provider is
documented in `compatibility/tck-adapters/README.md`. Its result is an unofficial cross-generation
regression run, not the Oracle/JCP JAXB 2.3 TCK and not a Jakarta certification claim; every
normative difference or exclusion must appear in the versioned exclusion manifest.

## `secure-streaming`

Currently covered: XML declaration for UTF-8 XML 1.0, elements, empty elements, attributes, entity
and numeric-character references, UTF-8 names/content, qualified names, namespace declarations as
attributes, comments, processing instructions, CDATA and mixed content. Semantic event output is
compared with both JDK StAX and Woodstox using only standard StAX behavior.

Deliberately outside this security profile: DTDs, external/general custom entities, XML 1.1,
non-UTF-8 transcoding, validation and complete namespace resolution. Consequently this profile is
not a claim of complete W3C XML processor conformance.

## `jaxb-binding`

Currently covered for unmarshalling and UTF-8 marshalling: standard `@XmlRootElement`, `@XmlElement`, `@XmlAttribute`, `@XmlValue`, nested
objects, repeated elements into `List`, primitive/scalar/enum conversion, unknown-element skipping,
XML Schema boolean and floating-point lexical forms, arbitrary-precision numbers, inherited fields,
`@XmlTransient`, no-argument classes and explicit namespace URIs on roots, elements and attributes.
Binding compares expanded names (`{namespace}local`) rather than prefixes, follows scoped/default
`xmlns` declarations, and emits namespace-correct round trips. `@XmlAccessorType` FIELD,
PROPERTY, PUBLIC_MEMBER and NONE discovery is precompiled; bean getter/setter access, object and
primitive arrays, `List`, `Set`, `Queue` and concrete `Collection` implementations are
supported. `@XmlElementWrapper` is native on byte-array, StAX and SAX paths. Maps use JAXB's
portable `XmlAdapter` entry-bean pattern rather than a proprietary wire format. Object results and
bidirectional output are compared with JAXB RI.

Remaining profile gaps are package-level `@XmlSchema` namespace defaults, callbacks/listeners,
binary/date XML Schema types, XJC-generated models, package-level annotations and the complete
standard API overload matrix. `@XmlType` metadata and polymorphism via `xsi:type` are covered by
the core compatibility kit. Gaps are tracked explicitly rather than silently excluded.

Direct `Map<K,V>` properties still require `@XmlJavaTypeAdapter`, as they do for portable JAXB
models; the adapter hot path and its entry-bean representation are covered against JAXB RI.

Jackson-specific annotations/databind features and StAX2/Woodstox extensions are not part of either
standard profile.

## `jakarta-provider`

The separate `simdxml-jaxb-provider` artifact is loaded through the standard
`jakarta.xml.bind.JAXBContextFactory` ServiceLoader contract. Its tests call only Jakarta APIs and
verify provider discovery, global-root unmarshalling, UTF-8 marshalling, fragment mode, Writer,
InputStream and `Source` paths. The remaining overloads and lifecycle features stay listed as
unsupported until executable compatibility cases pass.

### Zero-code-change deployment examples

The intended replacement boundary is dependency-level: application source remains unchanged and
only the provider dependency, archive contents or server configuration is adjusted.

#### Quarkus 3 / Jakarta

Existing code continues to use the standard API:

```java
JAXBContext context = JAXBContext.newInstance(Invoice.class);
Marshaller marshaller = context.createMarshaller();
Unmarshaller unmarshaller = context.createUnmarshaller();
```

Add the provider alongside the existing Quarkus JAXB extension:

```xml
<dependency>
  <groupId>org.simdxml</groupId>
  <artifactId>simdxml-jaxb-provider</artifactId>
  <version>${simdxml.version}</version>
</dependency>
<dependency>
  <groupId>io.quarkus</groupId>
  <artifactId>quarkus-jaxb</artifactId>
</dependency>
```

Quarkus CXF keeps the same `@WebService`, model classes and endpoint path. During augmentation,
schema/WSDL generation uses the standard JAXB cold-path delegate; request unmarshalling and
response marshalling use simdxml.

#### Open Liberty / Java EE 8

The application continues to use `javax.xml.bind.JAXBContext`, with no changes to its WAR,
servlet, JAX-WS endpoint or model classes. Add the Javax provider:

```xml
<dependency>
  <groupId>org.simdxml</groupId>
  <artifactId>simdxml-jaxb-javax-provider</artifactId>
  <version>${simdxml.version}</version>
</dependency>
```

The existing server configuration remains valid:

```xml
<featureManager>
  <feature>servlet-4.0</feature>
  <feature>jaxb-2.2</feature>
</featureManager>
```

The provider is packaged in the application class loader and discovered through the standard
JAXB 2.3 provider services. The Open Liberty smoke test verifies selection over HTTP.

#### Tomcat 9 / Java EE 8 WAR

Tomcat requires no server installation change. Package the same Javax provider in the WAR:

```xml
<dependency>
  <groupId>org.simdxml</groupId>
  <artifactId>simdxml-jaxb-javax-provider</artifactId>
  <version>${simdxml.version}</version>
</dependency>
```

`web.xml`, servlet code, endpoint code and JAXB model code remain unchanged. The `tomcat-it`
profile deploys the WAR on a downloaded Tomcat 9 instance and verifies that the provider is loaded
from `WEB-INF/lib`.

#### GlassFish, Metro and Jakarta servers

Use the Jakarta provider artifact and retain the server's existing JAXB/JAX-WS configuration:

```xml
<dependency>
  <groupId>org.simdxml</groupId>
  <artifactId>simdxml-jaxb-provider</artifactId>
  <version>${simdxml.version}</version>
</dependency>
```

The application continues to call the standard Jakarta JAXB and JAX-WS APIs. If parent-first class
loading selects the bundled provider, use the server's documented application-provider preference;
no application source change is required.

These examples demonstrate dependency-level integration, not certification. A runtime-specific
compatibility claim requires endpoint tests for namespace/QName handling, `JAXBElement`,
`xsi:type`, faults, attachments and the server's class-loading policy.

## `javax-provider-java8`

The separate `simdxml-jaxb-javax-provider` artifact is compiled to Java 8 bytecode and depends on
the JAXB-neutral `simdxml-core-java8` artifact. It publishes both the JAXB 2.3
`javax.xml.bind.JAXBContextFactory` service and the legacy Java 8
`javax.xml.bind.JAXBContext` service entry. An executable smoke test invokes only
`JAXBContext.newInstance`, verifies that simdxml was discovered, and performs a marshal/unmarshal
round trip on the local OpenJDK 8 JRE. This establishes API discovery and runtime linkage; it is
not yet a WebLogic 12c certification or complete JAXB 2.x TCK claim.

The `openliberty-it` executable profile additionally builds a Java 8 WAR, downloads a pinned Open
Liberty 26.0.0.1 kernel, installs only `servlet-4.0` and `jaxb-2.2`, and tests provider selection by
HTTP from inside the container. On 2026-08-19 it passed with one server JVM and one sequential
Failsafe test (0 failures); the installed runtime occupied 41 MiB and the WAR 296 KiB. The same
profile is wired into `.github/workflows/javaee8-openliberty.yml` and has no dependency on a local
application-server installation.

The same WAR passes against downloaded Tomcat Embedded 9.0.120, including an assertion that the
provider implementation came from `WEB-INF/lib`. A separate Quarkus 3.38.1 runtime test passes for
the Jakarta JAXB provider. Quarkus CXF 3.38.0 reaches simdxml during augmentation and completes
code-first schema/WSDL generation through the container/standard cold-path delegate. The complete
SOAP HTTP assertion runs in CI; a restricted local sandbox that prohibits opening a server socket
can still validate augmentation without being mistaken for a JAXB failure.

Rare JAXB operations are deliberately separated from the SIMD marshal/unmarshal hot path. Each
`JAXBContext` lazily resolves and caches a container implementation for operations such as
`generateSchema()`. Jakarta runtimes recognize GlassFish JAXB RI and EclipseLink MOXy; Java EE 8
runtimes recognize the traditional JAXB RI and MOXy. A vendor-specific factory can be selected
without per-message discovery using
`-Dorg.simdxml.jaxb.coldPathFactory=com.vendor.bind.ContextFactory`. The optional GlassFish JAXB
runtime dependency is only the standalone fallback; application servers may supply their own.

`JAXBElement` is not a cold-path fallback. Both the Jakarta and Javax bridges extract its expanded
`QName`, value and `nil` state and invoke the native UTF-8 simdxml writer. This covers the wrapper
used by CXF/JAX-WS responses without constructing or invoking a container marshaller.

## Financial verticals

The first executable profile recognizes ISO 20022 `pain.001`, `pain.002`, `pacs.008`, `pacs.002`,
`camt.053` and `camt.054` message roots. The initial projection covers `MsgId`, `NbOfTxs`, `CtrlSum`,
the first `IBAN` and first `BICFI`/`BIC`. Its byte/hash fast path is tested against the switchable
generic-name path. This is routing/audit projection, not XSD, business-rule, signature or settlement
validation.

## Runtime matrix

The portable heap parser and binding metadata have an executable Java 8 linkage gate. The metadata
recognizes Jakarta and Javax annotations by binary name and the core has no mandatory dependency on
either JAXB API. Modern acceleration remains behind
providers so VarHandle, Vector API, FFM and virtual-thread symbols do not enter that baseline.

| Runtime | Portable backend | Optional providers |
|---|---|---|
| Java 8 | baseline, packed/SWAR, strict UTF-8 | Unsafe when permitted |
| Java 9-15 | Java 8 set | VarHandle |
| Java 16-20 | Java 8 set | matching Vector API provider |
| Java 21 | Java 8 set | Vector and virtual-thread context policy |
| Java 22-26 | Java 8 set | Vector, virtual threads and FFM/direct memory |

## Application-server integration status

| Integration | Executable test in this repository | Current status |
|---|---|---|
| Quarkus + Quarkiverse CXF + Jakarta JAXB | `simdxml-server-smoke-quarkus` | Implemented and run in CI |
| Apache CXF standalone + Jakarta JAXB | — | Planned; no executable test yet |
| Tomcat 9 + Javax JAXB WAR | `tomcat-it` | Implemented and run in CI |
| Open Liberty + Javax JAXB WAR | `openliberty-it` | Implemented and run in CI |
| GlassFish/Metro | — | Planned; no executable test yet |
| Axis2/AXIOM | — | Planned; no executable test yet |

The current Quarkus test exercises Quarkiverse CXF inside Quarkus; it is not a standalone Apache CXF
container or `JAXBDataBinding` replacement test. A standalone CXF claim requires its own executable
module and dependency-only application fixture.

In particular, this repository does **not** currently claim an Axis2 integration. The Axis2 entry in
the framework roadmap identifies the intended AXIOM/StAX adapter and WSDL2Java databinding work; a
claim will be made only after a dedicated Axis2 module starts a real SOAP endpoint and verifies
JAXB provider selection, namespaces/QNames, faults, `JAXBElement`, `xsi:type` and attachments.

On 2026-08-19, Maven compiled 63 portable core sources plus one smoke test with `--release 8`, and
the resulting strict UTF-8 plus ISO 20022 flyweight smoke test ran successfully on the local
OpenJDK 8 JRE using `scalar-swar64`. The Javax provider was also packaged as class-file version 52
and its packaged-JAR provider-discovery round trip passed on that JRE. Run the offline compilation
gate with:

```text
mvn -o -f compatibility/pom.xml compile
```

The distribution split currently builds these independent artifacts:

- `simdxml-core-java8`;
- `simdxml-backend-unsafe`;
- `simdxml-backend-varhandle`;
- `simdxml-backend-vector-jdk24`;
- `simdxml-threading-jdk21`;
- `simdxml-direct-ffm-jdk24`.

Runtime smoke tests passed on Java 8 for scalar/SWAR and Unsafe, and on JRE 25 for VarHandle and the
JDK 24 Vector provider. Additional Vector artifacts are still required for incompatible incubator
API generations; a JDK 26 claim will only be made after testing on an actual JDK 26 runtime.
