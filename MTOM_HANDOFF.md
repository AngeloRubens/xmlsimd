# Handoff tecnico MTOM/XOP

## Stato corrente

Il lavoro MTOM/XOP JAXB è **completo** nel core, con bridge indipendente dal framework, su tutti e
tre i percorsi (byte, StAX, SAX) e su entrambi i provider (Jakarta e `javax`).

File principali:

- `src/main/java/org/simdxml/XmlAttachmentHandler.java`: contratto interno senza dipendenze JAXB.
- `src/main/java/org/simdxml/SimdMarshaller.java`: emissione `xop:Include` sul writer a byte e sul
  writer StAX (che copre anche SAX ed eventi), swaRef, fallback Base64 inline.
- `src/main/java/org/simdxml/SimdUnmarshaller.java`: configurazione dell'attachment handler,
  propagata anche al binder SAX.
- `src/main/java/org/simdxml/XmlBinder.java`: `readAttachment` per XOP e swaRef.
- `src/main/java/org/simdxml/SaxObjectBinderHandler.java`: consumo di `<xop:Include/>` sul push parser.
- `integrations/simdxml-jaxb-provider/.../JakartaAttachmentBridge.java`: bridge Jakarta JAXB.
- `integrations/simdxml-jaxb-javax-provider/.../JavaxAttachmentBridge.java`: bridge JAXB javax.

## Difetti trovati e corretti

Sette difetti reali, tutti coperti da test:

| difetto | effetto | dove |
|---|---|---|
| l'elemento della proprietà non veniva mai chiuso | XML non valido: `<payload>…</xop:Include>` senza `</payload>` | `SimdMarshaller.writeAttachment` |
| il writer StAX non emetteva `<xop:Include>` | scriveva `<payload xop:href="cid:…"/>`: nessun elemento XOP | `SimdMarshaller.writeStaxAttachment` |
| `getAttachmentAsDataHandler` riceveva il **content type** invece del content-id | attachment mai risolto come DataHandler | `JakartaAttachmentBridge.unmarshaller` |
| `fromBytes` restituiva un `DataHandler` anche per una proprietà `byte[]` | `ClassCastException` in binding | idem |
| `<xop:Include/>` lasciava il `</payload>` non consumato | disallineamento dello stack: l'elemento successivo bindava sul frame sbagliato | `XmlBinder.readProperty` |
| `xmlns:xop` dichiarato **sull'elemento `xop:Include` stesso** non veniva risolto | `Unbound XML namespace prefix: xop` su ogni documento prodotto dal writer StAX | `XmlBinder.readAttachment` |
| `getUnmarshallerHandler()` non riceveva l'attachment handler | il percorso SAX ignorava del tutto MTOM | `SimdJakartaUnmarshaller` (entrambi i provider) |

## Decisioni di performance

- `byte[]` usa direttamente l'array originale e l'overload JAXB `addMtomAttachment(byte[], offset, length, ...)`.
- `DataHandler` usa direttamente `addMtomAttachment(DataHandler, ...)`, evitando la materializzazione in `byte[]`.
- `@XmlMimeType`, `@XmlInlineBinaryData` e `@XmlAttachmentRef` sono **flag precompilati** su
  `XmlBindingMetadata.Property`, insieme a `binary()`, che riconosce `byte[]` e `DataHandler` **per
  nome di classe**: il core non linka Jakarta/Javax Activation e il marshal loop non fa alcuna
  lettura di annotazioni.
- `writeAttachment` è ora gated su `property.binary()`: le proprietà non binarie non entrano più nel
  percorso attachment a ogni elemento.
- Il binding resta precompilato; nessuna reflection nel loop caldo.

## Misure JMH (JDK 25, 2 fork, 5 warmup + 8 iterazioni, `-prof gc`)

`MtomBenchmark`, bridge neutro rispetto al provider, store di attachment in memoria:

| modo | 1 KB ops/s | 64 KB ops/s | 1 KB B/op | 64 KB B/op |
|---|---:|---:|---:|---:|
| base64 inline | 213.179 ± 4.543 | 2.432 ± 72 | 7.384 | 517.403 |
| MTOM `byte[]` | 1.874.166 ± 116.074 | 1.951.467 ± 30.306 | **1.248** | **1.248** |
| MTOM DataHandler | **2.050.940 ± 24.615** | **2.078.723 ± 37.851** | **1.248** | **1.248** |
| unmarshal XOP | 554.377 ± 9.913 | 551.210 ± 12.379 | 1.280 | 1.268 |

Il risultato che conta: **throughput e allocazioni MTOM sono costanti nella dimensione del payload**
(1.248 B/op a 1 KB e a 64 KB), mentre l'inline base64 sale a 517.403 B/op e crolla a 2.432 ops/s.
È la dimostrazione diretta che il core non copia mai il payload. L'overload DataHandler è il 9%
(1 KB) / 6% (64 KB) più veloce del percorso `byte[]`, a parità di allocazioni: vale la pena averlo.

## Test

- `integrations/simdxml-jaxb-provider/.../SimdMtomTest.java` — 16 test: round-trip `byte[]` e
  `DataHandler`, `@XmlMimeType` fino al provider, `@XmlInlineBinaryData`, fallback base64 senza XOP,
  swaRef, content-id sconosciuto, href malformato, elemento fratello dopo l'attachment, e i percorsi
  StAX (lettura e scrittura), SAX (lettura e scrittura).
- `integrations/simdxml-jaxb-javax-provider/.../SimdMtomTest.java` — 10 test, sorgente Java 8.

Comando:

```bash
JAVA_HOME=/home/lop/jdk-24.0.2+12 /home/lop/Scaricati/apache-maven-3.9.0/bin/mvn -q install -DskipTests
JAVA_HOME=/home/lop/jdk-24.0.2+12 /home/lop/Scaricati/apache-maven-3.9.0/bin/mvn -f integrations/pom.xml test
```

## Lavoro ancora aperto

1. `Image` e `Source` come tipi MTOM: oggi `Property.binary()` riconosce solo `byte[]` e
   `DataHandler`. Serve decidere se il core può dipendere da `javax.imageio`/`javax.xml.transform`
   per la conversione, o se anche questi vanno delegati al bridge.
2. swaRef con collezioni (`List<DataHandler>`): il percorso attuale è per singolo valore.
3. `@XmlMimeType` non viene riemesso come `xmime:contentType` sull'elemento in marshal.

Non introdurre dipendenze CXF, Metro o provider JAXB specifici: usare esclusivamente le API standard
e Activation standard già previste dai POM.
