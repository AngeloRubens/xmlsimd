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

## Why this project exists

XML is sometimes described as a legacy format because many new public APIs prefer JSON. That is an
incomplete view of production infrastructure. XML remains a contract boundary wherever messages
must be schema-defined, namespace-qualified, signed, encrypted, validated by independent parties,
archived for long periods or exchanged across organisations that cannot share an object model.
Replacing a JSON API is often a product choice; replacing a regulated XML boundary is usually a
multi-year interoperability programme. The result is a large mixed estate in which modern REST
frontends coexist with SOAP, SAML, ISO 20022, UBL, XBRL and healthcare XML backends.

This project exists to make that XML boundary measurable and efficient on the JVM. It targets the
expensive common path—UTF-8 scanning, structural recognition, namespaces, attributes, signature
metadata and binding—without requiring applications to build a DOM tree. It does not claim that XML
is the best format for every new service; it provides a faster implementation for the XML contracts
that organisations already have to operate.

The production ecosystems motivating the work include:

- **Public procurement and public administration.** The European Commission states that, since the
  April 2020 deadline, public authorities covered by the relevant procurement directives must be
  able to receive and process compliant electronic invoices. The EU eInvoicing model is based on
  EN 16931 and machine-readable syntaxes, including XML-based syntaxes. See the [European
  Commission eInvoicing overview](https://single-market-economy.ec.europa.eu/single-market/public-procurement/digital-procurement/einvoicing_en).
- **Financial messaging and payments.** ISO 20022 describes a global financial-messaging language,
  maintains a central repository of business components and publishes message definitions together
  with their XML schemas. The official catalogue contains payment, securities, cards, foreign
  exchange and trade message families. See the [ISO 20022 FAQ](https://www.iso20022.org/faq) and
  [message catalogue](https://www.iso20022.org/catalogue-messages).
- **Identity and message security.** SAML 2.0 defines XML assertions for authentication and
  federation, while WS-Security and XML Signature provide message-level integrity, confidentiality
  and identity tokens for SOAP and other enterprise exchanges. These formats are namespace- and
  attribute-heavy and are therefore sensitive to allocation and scanning costs. See the [OASIS SAML
  overview](https://docs.oasis-open.org/security/saml/Post2.0/sstc-saml-tech-overview-2.0.html) and
  [OASIS WS-Security](https://www.oasis-open.org/standard/wssv1-0/).
- **Regulatory and corporate reporting.** XBRL is the international standard for digital financial,
  performance, risk and compliance reporting; UBL/Peppol and national e-invoicing profiles apply the
  same schema-driven approach to procurement and invoices. See [XBRL International](https://www.xbrl.org/the-standard/what/the-standard-for-reporting/).
- **Interoperable public-sector delivery.** The European Commission's eDelivery AS4 profile is
  based on ebMS3/AS4 and specifies SOAP 1.2 with attachments for interoperable message delivery.
  This is precisely the kind of envelope, namespace and payload pipeline for which allocation-aware
  streaming matters. See [eDelivery AS4 2.0](https://ec.europa.eu/digital-building-blocks/sites/spaces/DIGITAL/pages/845480153/eDelivery%2BAS4%2B-%2B2.0).
- **SOAP services.** SOAP 1.2 is a W3C Recommendation defining a structured messaging framework
  using XML technologies and the `application/soap+xml` media type. It remains a compatibility
  boundary for enterprise and government service stacks. See the [W3C SOAP 1.2 Recommendation](https://www.w3.org/TR/soap12/).
- **Healthcare interoperability.** HL7 describes CDA as a primary clinical-document standard and
  reports that it is used millions of times per day for exchanging patient summaries and other
  medical documents. FHIR remains a normative specification with XML, JSON and RDF representations;
  its XML form is defined with XML Schema and Schematron constraints. See [HL7 CDA](https://hl7.org/cda/)
  and [FHIR XML](https://hl7.org/fhir/xml.html).

### XML in real production systems worldwide

The following examples show why XML remains relevant across continents and sectors. They are
references to deployed standards or public production gateways, not claims that every transaction
uses the same transport:

| Area | Concrete use | What XML provides | Reference |
|---|---|---|---|
| European Union | eInvoicing and public procurement | EN 16931-compliant machine-readable invoices and interoperable profiles | [European Commission eInvoicing](https://single-market-economy.ec.europa.eu/single-market/public-procurement/digital-procurement/einvoicing_en) |
| Europe / cross-border | SEPA, T2, TARGET and ISO 20022 payment infrastructures | Schema-defined `pain`, `pacs` and `camt` messages | [EPC payment schemes](https://www.europeanpaymentscouncil.eu/what-we-do/epc-payment-schemes/sepa-credit-transfer/sepa-credit-transfer-rulebook-and) |
| United States | Federal Reserve Fedwire Funds Service | ISO 20022 message format and XML syntax for the migration completed in 2025 | [Federal Reserve timeline](https://www.federalreserve.gov/newsevents/pressreleases/other20220627a.htm) |
| United States | IRS Modernized e-File and ACA transmissions | SOAP envelopes, XML tax schemas and MTOM attachments | [IRS MeF guidance](https://www.irs.gov/irm/part3/irm_03-042-004r) |
| China | Cross-border Interbank Payment System (CIPS) | ISO 20022-based message methodology for payments, settlement and financial markets | [CIPS standards](https://www.cips.com.cn/kjjqgsyyingw/cipsfw/index.shtml) |
| Japan | BOJ-NET | ISO 20022 messages for yen settlement and overseas deposits; the 2019 version was introduced in 2025 | [Bank of Japan](https://www5.boj.or.jp/bojnet/iso20022/iso260612-01.htm) |
| United Kingdom | NHS Spine and HMRC gateways | HL7 v3/SOAP/XML healthcare exchanges and tax/customs WSDL services | [NHS Spine](https://digital.nhs.uk/services/spine), [HMRC SOAP services](https://www.gov.uk/government/publications/hmrc-irmark-for-soap-protocol-services) |
| Australia | ATO Standard Business Reporting | XML reporting and SOAP-based security/service interfaces for government submissions | [ATO SBR terminology](https://softwaredevelopers.ato.gov.au/acronyms), [ATO security token service](https://softwaredevelopers.ato.gov.au/sites/default/files/resource-attachments/MAS-ST_SecurityTokenService_ServiceDefinition.pdf) |
| Global enterprise identity | SAML 2.0 federation | Signed XML assertions for authentication, attributes and SSO | [OASIS SAML](https://docs.oasis-open.org/security/saml/Post2.0/sstc-saml-tech-overview-2.0.html) |
| Global financial/regulatory reporting | XBRL/iXBRL | Taxonomies and machine-readable financial, risk and compliance reports | [XBRL International](https://www.xbrl.org/the-standard/what/the-standard-for-reporting/) |

These examples also explain the design target of simdxml: the same parser must handle compact
payment messages, deeply namespaced SOAP envelopes, signed identity assertions and large reporting
documents. The transport may be SOAP, AS4, HTTPS, EBICS or a file channel; the XML contract remains
the common interoperability boundary.

### Concrete SOAP scale examples

There are also deployed SOAP estates whose scale makes parser efficiency operationally relevant:

- **NHS England Spine.** The national Spine connects more than **44,000 healthcare IT systems in
  26,000 organisations** and reports more than **1.3 billion messages per month**. NHS documentation
  explicitly identifies older HL7 v3 interfaces as synchronous SOAP/XML and asynchronous ebXML, while
  newer interfaces are being migrated to FHIR. The volume figure covers the Spine platform as a whole,
  not SOAP alone, but it demonstrates the scale of the XML/SOAP estate that must coexist during
  migration. See [NHS Spine](https://digital.nhs.uk/services/spine) and [NHS API technologies](https://digital.nhs.uk/developer/guides-and-documentation/our-api-technologies).
- **US healthcare transactions.** Massachusetts MassHealth documents a SOAP submission option for
  HIPAA transactions, with XML-based WSDL/web-service envelopes carrying the trading-partner payload.
  This is an example of SOAP remaining a supported regulated-integration path alongside newer APIs.
  See the [MassHealth connectivity guidance](https://www.mass.gov/info-details/masshealth-connectivity-method-faq).
- **US federal tax filing (IRS Modernized e-File).** The IRS MeF application-to-application channel
  transmits business tax returns using SOAP; return data and attachments are XML documents defined by
  IRS schemas. The IRS operational guidance lists corporate, employment, partnership, excise and
  exempt-organisation returns, and the 2026 processing cycle has a new WSDL set. This is a national
  production workload, not a laboratory SOAP example. See the [IRS MeF operating guidance](https://www.irs.gov/irm/part3/irm_03-042-004r),
  [2026 MeF WSDL notice](https://www.irs.gov/e-file-providers/modernized-e-file-mef-status) and
  [SOAP/MTOM ACA guide](https://www.irs.gov/pub/irs-access/p5165_accessible.pdf).
- **UK HMRC tax and customs gateways.** HMRC continues to publish and update SOAP-protocol guidance
  (including IRmark canonicalisation) and exposes WSDL-based services for declarations and status
  queries. Its transaction-engine documentation still describes XML submissions and routed
  request/response conversations. See [HMRC SOAP IRmark](https://www.gov.uk/government/publications/hmrc-irmark-for-soap-protocol-services),
  [HMRC ATWD SOAP service](https://webservices.hmrc.gov.uk/ws/atwd/service) and the
  [XML developer guide](https://www.gov.uk/guidance/basic-guide-for-xml-software-developers).
- **European eDelivery in production.** Domibus, the Commission's AS4 Access Point implementation,
  is still actively released in 2026; its REST plugin is only a backend integration option and does
  not replace SOAP/AS4 between Access Points. The Commission also maintains a conformance catalogue
  of interoperable AS4 products. See [Domibus 5.1.8](https://ec.europa.eu/digital-building-blocks/sites/spaces/DIGITAL/blog/2025/05/28/900017959/Domibus%2B5.1.8%2Breleased%2Bwith%2Benhanced%2Bstability%2Bfor%2Bpull%2Bmessaging),
  [Domibus product updates](https://ec.europa.eu/digital-building-blocks/sites/pages/viewrecentblogposts.action?key=DIGITAL)
  and the [AS4 conformant-products catalogue](https://ec.europa.eu/digital-building-blocks/sites/spaces/DIGITAL/pages/721846393/eDelivery%2BAS4%2Bv1.x%2Bconformant%2Bproducts).

### Regional financial evidence

The financial case is not limited to one market. Official payment-system material shows a continuing
regional rollout of structured ISO 20022 message families, whose approved definitions are published
with XML schemas (while some ecosystems also support other syntaxes):

| Region | Evidence relevant to XML/structured financial messaging |
|---|---|
| **Europe** | The ECB describes SEPA as covering **41 countries** (status 22 May 2025). EPC/Eurosystem payment infrastructures use ISO 20022-based message guidelines for mass-volume SEPA, high-value euro and international payments. See the [ECB SEPA overview](https://www.ecb.europa.eu/paym/retail/sepa/html/index.en.html) and [ECB/EPC migration guidance](https://www.ecb.europa.eu/paym/target/target-professional-use-documents-links/tips/shared/pdf/tipsmeetdoc/ecb.tipsmeetdoc220707_EPC-guidance-document-Migration-to-the-2019-Version-of-the-ISO-20022-based-XML-Messaging-Standard-TIPSConsultativeGroup.en.pdf). |
| **United States** | The Federal Reserve announced that Fedwire Funds Service adopted the ISO 20022 message format on **10 March 2025**. The Federal Register implementation material identifies XML as the syntax for the ISO 20022 like-for-like messages. See the [Federal Reserve timeline](https://www.federalreserve.gov/newsevents/pressreleases/other20220627a.htm) and [Federal Register notice](https://www.federalreserve.gov/newsevents/pressreleases/files/2018-14351.pdf). |
| **China** | CIPS states that its message standards are formulated using the ISO 20022 message-development methodology and cover payment/settlement, financial markets, account/cash management and related domains. See the [official CIPS standards page](https://www.cips.com.cn/kjjqgsyyingw/cipsfw/index.shtml). |
| **Japan** | The Bank of Japan states that BOJ-NET adopted ISO 20022 messages for foreign-exchange yen settlement and overseas-deposit current-account transactions in **2015**, and revised to the 2019 version in **November 2025** for international interoperability. See the [BOJ ISO 20022 programme](https://www5.boj.or.jp/bojnet/iso20022/iso260612-01.htm). |

This regional evidence is deliberately stated as infrastructure and standard adoption, not as a
claim that every payment message in every institution is XML or that ISO 20022 alone determines
legal compliance. It does show why a high-performance XML implementation has practical value in
payment gateways, clearing adapters, reconciliation, reporting and cross-border interoperability.

SOAP must be distinguished from the financial message syntax. In Europe, the Commission's eDelivery
AS4 profile explicitly packages interoperable delivery around SOAP 1.2 and attachments. Fedwire,
CIPS and BOJ-NET adoption evidence concerns ISO 20022 message formats and system-specific transport
channels; it should not be read as a claim that those payment rails expose a public SOAP endpoint.
simdxml therefore treats SOAP envelope processing and ISO 20022 payload processing as separate,
composable verticals.

These standards do not imply that every workload should use XML, nor that XML is always the smallest
wire format. They do establish a large installed base of namespace-heavy, schema-driven and often
SOAP-wrapped documents in finance, healthcare and public administration. The purpose of simdxml is
to make that existing XML investment cheaper to process: preserve the established APIs and wire
contracts, while moving scanning, validation, byte matching and binding onto allocation-aware hot
paths. The supported verticals and their validation scope are documented in
[COMPATIBILITY.md](COMPATIBILITY.md); they are not claims of regulatory or clinical certification.

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

Framework adapters keep transport concerns outside the parser. Quarkus/Quarkiverse CXF is covered
by the current integration test; standalone Apache CXF, Metro/JAX-WS and Axis2 remain planned
adapters and are not yet presented as validated drop-in integrations.

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
