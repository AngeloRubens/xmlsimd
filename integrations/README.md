# simdxml integration bridges

The integration reactor keeps optional server/framework dependencies out of `simdxml-java`:

```shell
mvn -f integrations/pom.xml test
```

The build is configured for one reused test JVM at a time and two visible processors.

## Jakarta XML Binding provider

`org.simdxml:simdxml-jaxb-provider` is the dependency-only bridge. Its ServiceLoader descriptor
registers `org.simdxml.jaxb.SimdJaxbContextFactory` as a
`jakarta.xml.bind.JAXBContextFactory`. Existing application code remains unchanged:

```java
JAXBContext context = JAXBContext.newInstance(Message.class);
Marshaller marshaller = context.createMarshaller();
Unmarshaller unmarshaller = context.createUnmarshaller();
```

When the provider jar and `simdxml-java` are selected ahead of another JAXB implementation, these
objects are backed by simdxml. The bridge currently supports class-array context creation, UTF-8
marshal to `OutputStream`, `File`, `Writer` and `StreamResult`, and unmarshal from `InputStream`,
`File`, `URL`, `Reader`, `InputSource`, `Source`, DOM and StAX sources. Unsupported standard methods
fail explicitly with `JAXBException`/`UnsupportedOperationException` and remain red items in
`COMPATIBILITY.md`.

## Java EE 8 / Javax XML Binding provider

For Java EE 8 runtimes use `org.simdxml:simdxml-jaxb-javax-provider` together with
`org.simdxml:simdxml-core-java8`. Application endpoint and model code continues to call the normal
`javax.xml.bind` API. The bridge includes both the JAXB 2.3 factory service and the legacy Java 8
provider service, is compiled as Java 8 bytecode, and has been exercised through automatic
`JAXBContext.newInstance` discovery on an actual OpenJDK 8 JRE.

Marshaller and Unmarshaller instances retain JAXB's normal non-thread-safe lifecycle; the
JAXBContext and its immutable simdxml metadata are safe to share. A server should create or pool
marshaller/unmarshaller instances per request/thread, which is also safe for virtual-thread based
deployments on newer runtimes.

WebLogic may require its documented application class-loading/provider preference configuration
when its bundled JAXB implementation wins parent-first loading. No application implementation
change is intended, but the current artifact must not yet be described as a certified WebLogic
replacement: context-path/XJC packages, `JAXBElement`, schema/type polymorphism, MTOM and a real
server SOAP suite remain compatibility gates.

## Server bridges

Planned artifacts use the same bridge pattern:

- `simdxml-cxf`: CXF `DataBinding`/`DataReader`/`DataWriter`, with ServiceLoader metadata where CXF
  exposes it. The JAXB provider remains the zero-code-change path for JAXB-based CXF endpoints.
- `simdxml-metro`: Metro `BindingContextFactory`; Metro can select it globally or per endpoint. A
  pure dependency-only mode will also delegate through the Jakarta JAXB provider.
- `simdxml-axis2`: AXIOM/StAX runtime adapter plus a separate WSDL2Java databinding extension.
- `simdxml-jackson-xml`: Jackson module/factory adapter discovered through Jackson's module SPI. It
  must not duplicate Jackson binary class names, which would create classpath conflicts.

No server bridge will be advertised as drop-in until its integration compatibility suite starts a
real endpoint, performs SOAP request/response and fault round trips, and verifies namespace/QName,
JAXBElement, `xsi:type` and MTOM behavior.

## Reproducible Java EE 8 server smoke test

`simdxml-server-smoke-javaee8` builds a standard Java 8 WAR. Its `openliberty-it` Maven profile
resolves a pinned Open Liberty kernel into the module `target` directory, installs only
`servlet-4.0` and `jaxb-2.2`, starts one server JVM, calls the servlet over HTTP, and always stops
the server in `post-integration-test`. It does not depend on a developer-machine server install:

```shell
mvn -f integrations/pom.xml -pl :simdxml-server-smoke-javaee8 -am verify -Popenliberty-it
```

The endpoint verifies from inside the container that unmodified `JAXBContext.newInstance` selected
the simdxml Javax provider and that marshalling/unmarshalling round-trips correctly. Arquillian is
intentionally not used for this single-container smoke test because it is a deployment harness,
not a runtime, and would still require the same server download. It can be added later when the
same SOAP archive must be exercised against several containers.

The `tomcat-it` profile downloads only Tomcat Embedded 9.0.120, deploys the same packaged WAR on
an ephemeral port and additionally verifies that the selected provider was loaded from the WAR's
own `WEB-INF/lib`. It passed locally in 2.0 seconds:

```shell
mvn -f integrations/pom.xml -pl :simdxml-server-smoke-javaee8 -am verify -Ptomcat-it
```

The opt-in `quarkus-it` profile uses Quarkus 3.38.1 plus its stable JAXB extension and verifies the
Jakarta simdxml provider from inside a running Quarkus endpoint:

```shell
mvn -f integrations/pom.xml -Pquarkus-it -pl :simdxml-server-smoke-quarkus -am test
```

Quarkus CXF 3.38.0 is covered by a code-first SOAP endpoint. It selects simdxml during build
augmentation; schema/WSDL generation is delegated to the JAXB implementation supplied by the
runtime and the SOAP response verifies that the active context is simdxml. In restricted sandboxes
the final HTTP phase may be unable to open a socket, while augmentation and schema generation still
complete. The unrestricted CI job executes the complete endpoint test.

Cold-path JAXB behavior is resolved once per context and cached. Set
`-Dorg.simdxml.jaxb.coldPathFactory=<factory-class>` when a container requires an explicit vendor
factory; otherwise JAXB RI and EclipseLink MOXy are detected through the thread context classloader.
This mechanism is intentionally absent from the SIMD marshal/unmarshal hot path.
In particular, the `JAXBElement` wrappers created by CXF/JAX-WS are marshalled natively by simdxml;
only schema generation and other genuinely rare compatibility operations use the delegate.
