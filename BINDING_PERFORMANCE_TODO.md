# XML → Java Binding Performance TODO

Questo file è il passaggio di consegne per il lavoro sul binding. Va aggiornato dopo ogni modifica
significativa, indicando test e benchmark eseguiti.

## Riprendere da qui

Ultimo lavoro: branch `mtom-xop-and-binding-performance`, commit `290cb9e` (2026-08-27).
Suite verde: 90 test nel core, 59 nelle integrazioni.

I sette punti ancora aperti, in ordine di rendimento atteso:

| # | punto | dove |
|---|---|---|
| 1 | I namespace costano il **47%** del throughput: piano di risoluzione precompilato per i modelli a namespace singolo | sezione «Accesso al bean» |
| 2 | Il marshal alloca il **77% più di Jackson** su 11,5 KB: il buffer di uscita cresce a copie ripetute | sezione «Marshal» |
| 3 | Restano ~24 byte/elemento che il binder standard alloca in più del Direct a 256 book | sezione «`Object[]`, `Map` e `String` inutili» |
| 4 | `org.simdxml.utf8.vector.threshold`: misura non conclusiva, serve una macchina scarica | sezione «utf8.vector.threshold» |
| 5 | Tabella ASM/reflection/VarHandle da rifare: contaminata | sezione «Accesso al bean» |
| 6 | Binder specializzato generato per tipo: può attaccare al massimo il 38% del budget, rimandato dopo 1 e 2 | P1 — binding Direct |
| 7 | Vettorizzare il terminatore dei nomi XML: analizzato e rimandato, con motivazione | P1 — core Direct/FFM |

Tre punti aperti su MTOM sono in [MTOM_HANDOFF.md](MTOM_HANDOFF.md): tipi `Image`/`Source`,
swaRef su collezioni, riemissione di `xmime:contentType`.

## Obiettivo

Portare il binding XML → Java bean oltre Jackson XML in throughput e allocazioni, mantenendo la
semantica JAXB e il supporto ai backend SIMD/Vector e Direct/FFM del core.

**Raggiunto il 2026-08-23 per il binder standard** (+53,3% throughput e −36,2% allocazioni su
messaggio da 1.407 byte, JMH forked a macchina scarica). **Raggiunto anche per il Direct/FFM il
2026-08-27**: batte Jackson a tutte e tre le taglie misurate e alloca quanto il binder standard a 4
e 32 book, meno a 256.

Restano due divari misurati e non chiusi: **il marshal alloca il 77% più di Jackson** su 11,5 KB di
output, e **i namespace costano il 47% del throughput** rispetto al percorso unqualified.

## Stato attuale

- [x] `BindingPlan` precompilato e dispatch hash dei nomi locali.
- [x] Flyweight per testo/attributi e accesso lazy ai byte XML.
- [x] Accesso ASM/generated per bean pubblici con costruttore no-arg.
- [x] Percorso Direct/FFM tramite `DirectSimdXmlParser.bind(MemorySegment, Class)`.
- [x] Percorso Direct su `ByteBuffer`.
- [x] Riutilizzo del finder Vector/SWAR già presente nel core Direct.
- [x] Conversioni primitive Direct e gestione delle entity XML.
- [x] Test funzionali Direct/FFM e test di compatibilità.
- [x] Benchmark JMH con simdxml, simdxml-direct, JAXB RI e Jackson.
- [x] Benchmark Direct configurato con memoria nativa off-heap e fixture UTF-8 prevalidata.
- [x] Conversione a byte di `int`, `long`, `short`, `byte`, `boolean`, `float`, `double` condivisa fra
      binder standard e binder Direct, senza String intermedia.
- [x] MTOM/XOP e swaRef su byte, StAX e SAX, con flag precompilati sul `Property`.
- [x] Benchmark separati per stadio (`BindingStageBenchmark`), memoria del percorso Direct
      (`DirectMemoryBenchmark`), soglia dell'indice (`ThresholdBenchmark`), accesso al bean
      (`BeanAccessBenchmark`) e MTOM (`MtomBenchmark`).

## 2026-08-22 — correzioni al lavoro di ottimizzazione

Sei regressioni introdotte dall'ottimizzazione del binder, tutte assenti su `bccfbf8`, riprodotte e
corrette. Test: `BindingRegressionTest` (7 casi), suite completa 59 test verdi, compilata con
JDK 24 ed eseguita con JDK 25 (`mvn test -Djvm=.../jdk-25+36-jre/bin/java`).

| difetto | causa | fix |
|---|---|---|
| `<e abc='1' ab='2'/>` rifiutato come duplicato | `sameBytes` senza confronto di lunghezza sul nome già registrato | `SimdXmlStreamReader.openElement` |
| testo scalare con entity perso | il fast path raw consumava il primo evento e ripartiva dal secondo | `XmlBinder.readElementText(reader, prefix, pending)` |
| `<n>12<!--x-->34</n>` → 12 | il fast path convertiva solo il primo segmento | idem |
| scalare con figlio: errore sbagliato | evento consumato prima del controllo | idem |
| `xsi:nil`/`xsi:type` con prefisso ≠ `xsi` ignorati | lookup sul prefisso letterale + fallback legato a `hasNamespaceDeclarations()` del solo elemento corrente | `isNil`/`resolveXsiType` tornano al lookup expanded-name, con short-circuit su `NamespaceFrame.EMPTY` (più veloce dell'originale) |
| figlio con namespace bindato come unqualified | `readDirectBean` non controllava `childScope` | `readDirectBean` usa `children.get(elementName(...))` fuori dallo scope vuoto |

Due difetti di performance nello stesso lavoro:

- `XmlBinder` abilitava il dispatch hash solo se `localNameHash() >= 0`, ma l'hash è un FNV-1a grezzo
  negativo in circa metà dei nomi — inclusi `book` e `title` della fixture. Sostituito da
  `BindingCursor.hasByteNames()` + `BindingPlan.hashDispatch()`.
- `GeneratedAccessFactory.get()` era invocato **per istanza di bean** e senza cache negativa: 786 ns
  per ogni oggetto sui bean getter/setter e in modalità `varhandle`. Ora è un `ClassValue` con
  sentinella `UNAVAILABLE`, risolto una volta sola in `BindingPlan.generated`. La `ConcurrentHashMap`
  statica precedente teneva reference forti alle classi applicative (leak di classloader al redeploy).
  Aggiunta anche la verifica di visibilità del bean dal classloader di simdxml, che evita un
  `NoClassDefFoundError` al primo `write()` quando il bean sta in un loader figlio.

A/B loop sequenziale (JDK 25, 20k warmup, best of 3; indicativo, **non** JMH):

| fixture | `bccfbf8` | dopo i fix |
|---|---:|---:|
| catalog/book, campi pubblici, 32 book | 37.319 msg/s | **70.581** (+89%) |
| orders/order, getter/setter, 32 order | 19.243 msg/s | **23.399** (+22%) |

Prima dei fix il caso getter/setter era a 14.471 msg/s su JDK 24, cioè **in regressione** del 22%
rispetto a HEAD: era il costo della codegen ritentata per ogni oggetto.

## Confronto con Jackson: il benchmark era invalido

`XmlMapper` **non legge le annotazioni JAXB**: il modello del benchmark bindava **0 book su 32**.
Jackson veniva cronometrato mentre parsava il documento e ne buttava via il contenuto. Tutte le
cifre Jackson registrate prima del 2026-08-22, inclusi i 40.253 ops/s in `BENCHMARKS.md` e i
52–55k qui sotto, sono da buttare.

Corretto: il modello porta ora anche `@JacksonXmlProperty`/`@JacksonXmlElementWrapper`/
`@JacksonXmlText`, e `ObjectBindingGcBenchmark.verifyEquivalence()` fa fallire il run se un backend
non produce lo stesso grafo. Effetto immediato: Jackson passa da 9.352 a **11.392 byte/messaggio**.

### Allocazioni (deterministiche, riprodotte identiche su 3 run)

| implementazione | 4 book (191 B) | 32 book (1.407 B) | 256 book (11.575 B) |
|---|---:|---:|---:|
| **simdxml** | **928** | **7.264** | 76.369 |
| JAXB RI | 2.152 | 10.728 | 83.214 |
| Jackson XML | 3.936 | 11.392 | **74.913** |
| simdxml Direct/FFM | 2.232 | 16.184 | 131.698 |

simdxml alloca **−76,4%** rispetto a Jackson su messaggio piccolo e **−36,2%** su messaggio medio;
a 256 book Jackson è più magro dell'1,9%. Il Direct/FFM alloca 1,4–1,8× Jackson a ogni dimensione:
è lì il divario da chiudere.

### Throughput: sorpasso confermato a macchina scarica

Run del 2026-08-23 07:07, load average 0,29 prima dell'avvio (i run precedenti sono stati scartati:
un secondo carico portava l'errore JMH al 66% dello score). Errori fra 0,8% e 2,5%, intervalli
tutti disgiunti:

| implementazione | 4 book (191 B) | 32 book (1.407 B) | 256 book (11.575 B) |
|---|---:|---:|---:|
| **simdxml** | **459.806 ± 10.899** | **74.156 ± 1.133** | **9.787 ± 77** |
| simdxml Direct/FFM | 302.061 ± 7.517 | 43.238 ± 933 | 5.535 ± 338 |
| Jackson XML | 210.718 ± 4.789 | 48.382 ± 399 | 6.383 ± 159 |
| JAXB RI | 106.587 ± 3.186 | 33.119 ± 1.174 | 4.916 ± 139 |

simdxml supera Jackson di **+118,2%** sul messaggio da 4 book e di **+53,3%** sia a 32 che a 256
book; supera JAXB RI di **+331% / +124% / +99%**. Con le allocazioni della tabella sopra: 1,5× il
throughput di Jackson allocando il 36% in meno sul messaggio da 1.407 byte.

Il **Direct/FFM batte Jackson solo sul messaggio più piccolo** (+43,3% a 4 book) e perde sugli altri
due (−10,6% a 32, −13,3% a 256) allocando 1,4–1,8× Jackson ovunque. Il divario da chiudere è lì,
non nel binder standard.

Comando:

```shell
CP="target/classes:target/test-classes:$(cat /tmp/simdxml-jmh-cp)"
/home/lop/Scaricati/oracleLight/jdk-25+36-jre/bin/java \
  --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -cp "$CP" \
  org.openjdk.jmh.Main 'org.simdxml.ObjectBindingGcBenchmark.bind' -f 2 -prof gc -foe true \
  -p library=simdxml,simdxml-direct,jaxb-ri,jackson -p operation=unmarshal -p books=4,32,256 \
  -jvmArgsAppend "--add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED"
```

- [x] Comando eseguito a macchina scarica il 2026-08-23; throughput ed errore registrati sopra.
- [x] Verificare che `verifyEquivalence()` copra anche il caso `marshal` prima di misurarlo.
      → **2026-08-27**: fatto; il controllo ha trovato subito la radice sbagliata di Jackson.


### Senza Vector API il sorpasso regge lo stesso

JVM avviata **senza** `--add-modules jdk.incubator.vector`: `indexingStrategy()` = `unsafe-swar64`,
`memoryStrategy()` = `unsafe-native/adaptive-swar<=4096/memorysegment-swar64`.

| | 4 book | 32 book | 256 book |
|---|---:|---:|---:|
| simdxml senza Vector | 427.310 ± 56.956 | 71.566 ± 2.062 | 7.941 ± 152 |
| Jackson (controllo) | 210.506 ± 7.106 | 42.163 ± 2.184 | 5.800 ± 251 |
| **vantaggio** | **+103,0%** | **+69,7%** | **+36,9%** |

La Vector API contribuisce **solo sopra la soglia `tiny`**, dove l'indice viene davvero costruito:
+23,2% a 256 book (9.787 vs 7.941). A 4 e 32 book le due configurazioni eseguono lo stesso codice.

`gc.alloc.rate.norm` identico al byte fra le due configurazioni (7.264,098 vs 7.264,094 B/op):
flyweight e riuso stanno nel reader/binder, non nel backend di stage-1.

Il **Direct/FFM va più veloce senza Vector** (45.128 vs 43.238 a 32 book; 5.773 vs 5.535 a 256): il
finder vettoriale a 16 byte su `MemorySegment` non ripaga l'overhead rispetto allo SWAR.

- [x] Rivedere `VectorDirectByteFinder`: oggi `memorysegment-vector-16` è più lento di
      `memorysegment-swar64` su tutte e tre le dimensioni misurate.
      → **2026-08-27**: srotolamento corretto e default allineato alle misure.


## La stage-1 SIMD è spenta sui messaggi tipici — ma attivarla non paga

`SimdXmlParser:156` — `if (length <= tinyDocumentThreshold) structurals.useDirectSearch();` con
soglia default **4096 byte** — fa degenerare `StructuralIndex.next()` in un `for` scalare e non
costruisce mai l'indice. La fixture object-binding a 32 book misura **1.407 byte**: tutte le misure
di binding fatte finora girano **senza SIMD**. Stessa cosa sul Direct: `DirectSimdXmlParser:77`
seleziona `scalarFinder` sotto 4096 byte (`memoryStrategy()` = `adaptive-swar<=4096/...`) e
`org.simdxml.direct.index.threshold` ha default `Long.MAX_VALUE`.

Misura JDK 25 con `-Dorg.simdxml.tiny.threshold=0` (indice sempre costruito), stesso binder:

| fixture | default (stage1 OFF sotto 4 KB) | indice sempre attivo | indexer scalar-swar64 |
|---|---:|---:|---:|
| 16 book, 703 B | 62.571 msg/s | **116.050** (+85%) | 101.757 |
| 32 book, 1.407 B | 31.638 msg/s | **62.619** (+98%) | 59.744 |
| 128 book, 5.687 B | 19.158 msg/s | 17.231 (−10%) | 15.340 |
| 512 book, 23.351 B | 4.479 msg/s | 3.857 (−14%) | 3.857 |

> **Correzione.** La tabella qui sopra viene da un loop sequenziale con warmup insufficiente
> (~6k iterazioni contro le ~300k di JMH) e **non regge**. Ripetendo lo stesso confronto con JMH
> forked, `-Dorg.simdxml.tiny.threshold=0 -Dorg.simdxml.indexer=vector -Dorg.simdxml.direct.strategy=vector`
> dà simdxml **66.628 ± 1.322 msg/s** contro **69.118 ± 2.833** con i default: costruire l'indice
> per un messaggio da 1.407 byte **non si ripaga**, esattamente come assume la soglia da 4.096 byte.
> Regola 2 del file applicata a me stesso: un harness non-JMH non basta per una conclusione.

Resta vero il **fatto** — sotto 4 KB il binding non esegue codice SIMD, in nessuno dei tre punti
(`tiny.threshold`, `utf8.vector.threshold`, `adaptive-swar<=4096` del Direct) — ma la conseguenza
non è una perdita: il vantaggio sul binding viene dal binder, non dalla tokenizzazione. La stage-1
SIMD si ripaga sui documenti grandi (le righe Wiki in MiB/s), non sui messaggi JAXB piccoli.

- [x] Misurare comunque la soglia su SOAP 882 B / HL7 1.809 B / payment 427 B con JMH, per
      verificare se esiste una forma di documento in cui l'indice conviene sotto i 4 KB.
      → **2026-08-27**: nessuna delle tre forme ripaga l'indice: la soglia 4096 regge.

- [ ] `org.simdxml.utf8.vector.threshold` ha lo stesso default 4096: misurarlo separatamente, perché
      la validazione UTF-8 STRICT gira su ogni unmarshal.
      → **2026-08-27**: misurato, ma **non conclusivo**: le tre fixture danno tre direzioni diverse e
      due errori superano il 15% dello score. Resta aperto, serve una macchina davvero scarica.

## 2026-08-23 — ottimizzazione Direct/FFM e percorso caldo

Misure JDK 25, 3 fork, 5 warmup + 8 iterazioni, macchina scarica (verificata: gli altri run sono
stati scartati perché un Tomcat di un'altra sessione teneva la CPU all'11%).

### Direct/FFM: portato sul pavimento allocativo

| | prima | dopo | pavimento (grafo nudo) |
|---|---:|---:|---:|
| 4 book | 2.232 B | **928 B** | 928 B |
| 32 book | 16.184 B | **7.264 B** | 7.264 B |
| 256 book | 131.672 B | **59.776 B** | 59.776 B |

Throughput 43.238 → 53.636 msg/s a 32 book (+24%), 5.535 → 7.163 a 256 (+29%). Ora il Direct batte
Jackson a ogni taglia: +85,3% / +38,6% / +25,9%.

Interventi: `decodeUtf8` copia in uno scratch riusato e fa una sola `new String` invece della catena
`asSlice → ByteBuffer → CharBuffer → char[] → String`; `decodeXmlText` cerca `&` sui byte ed espande
le entity byte-wise; `Frame` in stack ad array poolato al posto di `ArrayDeque`; binder poolato nel
parser; nome del root in `ClassValue`; offset del local name risolto una volta per reset invece di
quattro scansioni per nome.

### Percorso caldo del binder standard: +2,0% / +3,9%

Profilo JFR (campioni foglia): `scanName` era il singolo frame più caldo al 16,1%, e
`localNameEquals(String)` + `asciiEquals` + `sameBytes` valevano il 14,6% in confronti String↔byte.
Ora `scanName` calcola **una sola catena FNV** (l'hash del local name è differito ai nomi qualificati,
che sono l'eccezione) e i confronti passano da `xmlName()` a `xmlNameBytes()` con `Arrays.equals`
vettorizzato: i due frame sono spariti dal profilo, sostituiti da `ArraysSupport.mismatch`.

A/B con le sole quattro modifiche annullate, 3 fork, macchina scarica:

| | prima | dopo | |
|---|---:|---:|---|
| 4 book | 487.429 ± 7.066 | **506.272 ± 3.157** | +3,9% |
| 32 book | 77.166 ± 518 | **78.709 ± 223** | +2,0% |

### Bug di correttezza: entity rotte sopra i 4 KB

`SimdXmlStreamReader.next()` sondava `index.next(start, markup, '&')` per rilevare l'entity e poi
`decode()` rifaceva la stessa query, ma `entityCursor` è monodirezionale: la seconda risposta era
"nessuna entity" e il testo usciva **non decodificato**. Colpiva ogni documento sopra
`tiny.threshold`, cioè il percorso indicizzato usato in produzione. Non è una regressione del lavoro
di ottimizzazione: si riproduce identico sul codice precedente con `-Dorg.simdxml.tiny.threshold=0`.
Corretto facendo scandire a `decode` il proprio intervallo; test in
`BindingRegressionTest.entitiesAreExpandedAboveTheStructuralIndexThreshold`.

### Due ipotesi misurate e scartate

- **Fondere validazione UTF-8 e indicizzazione in una passata**: implementata e rimossa. Con 3 fork
  puliti la validazione risulta di fatto gratis (66.449 msg/s con validazione vs 65.885 senza),
  quindi non c'è passata da risparmiare.
- **Abbassare `org.simdxml.tiny.threshold`**: il directSearch vince sotto soglia a ogni taglia
  misurata — +9,7% a 1.407 B, +10,0% a 2.815 B, +19,9% a 703 B. La soglia da 4096 resta.

Su entrambe avevo tratto la conclusione opposta da un run contaminato: vale la regola 2 del file,
un harness rumoroso non basta per una decisione. Includere sempre una configurazione di controllo.

- [x] `VectorDirectByteFinder` resta più lento di `memorysegment-swar64` a tutte le taglie: rivederlo
      o cambiarne il default.
      → **2026-08-27**: confermato dopo la riscrittura; `auto` sceglie SWAR sotto i 256 bit.

- [x] `skipSpace` è ancora il 5-6% dei campioni foglia; non ancora attaccato.
      → **2026-08-27**: ora 1,0% sul Direct e assente sullo standard.

- [x] `Property.decimalInt` usa `Math.addExact`/`multiplyExact` per cifra: valutare moltiplicazione
      semplice con un solo controllo di range finale.
      → **2026-08-27**: riscritto: accumulo in long, un solo controllo di range.


## 2026-08-27 — chiusura dei punti aperti

Tutte le misure di questa sezione: JDK 25 (`/home/lop/Scaricati/oracleLight/jdk-25+36-jre`),
JMH `-f 2`, 5 warmup + 8 iterazioni da 1 s, `-XX:+UseG1GC -XX:ActiveProcessorCount=2`, fixture
identiche, `-prof gc` dove indicato.

> **Contaminazione da dichiarare.** Durante i run un server Liberty di un'altra sessione ha tenuto
> circa il 37% di un core su 4. Le colonne `gc.alloc.rate.norm` sono deterministiche (errore
> ±0,001 B/op) e restano confrontabili con le misure precedenti; i `ops/s` di questa sezione vanno
> letti con quella riserva, per la regola 2 del file. I confronti interni a un singolo run — che
> sono quelli su cui si basano le conclusioni sotto — non ne risentono, perché il carico è lo stesso
> per tutte le configurazioni confrontate.

### Il sorpasso su unmarshal regge, e le allocazioni non sono regredite

| implementazione | 4 book (191 B) | 32 book (1.407 B) | 256 book (11.575 B) |
|---|---:|---:|---:|
| **simdxml** | **480.622 ± 9.104** | **77.259 ± 986** | **9.576 ± 249** |
| simdxml Direct/FFM | 387.289 ± 34.078 | 57.174 ± 1.813 | 6.447 ± 257 |
| Jackson XML | 216.540 ± 2.262 | 47.009 ± 2.172 | 5.982 ± 153 |
| JAXB RI | 116.922 ± 3.690 | 32.930 ± 1.105 | 4.807 ± 266 |

simdxml supera Jackson di **+122% / +64% / +60%**. Allocazioni (`B/op`, deterministiche):

| implementazione | 4 book | 32 book | 256 book |
|---|---:|---:|---:|
| **simdxml** | **928,014** | **7.264,090** | 76.368,732 → **68.144,663** |
| simdxml Direct/FFM | 928,019 | 7.264,126 | **61.849,116** |
| Jackson XML | 3.936,032 | 11.392,149 | 74.913,181 |
| JAXB RI | 2.152,071 | 10.728,267 | 83.225,843 |

I valori del binder standard sono **identici al byte** a quelli registrati il 2026-08-23: nessuna
delle modifiche di questa sessione ha aumentato le allocazioni. Il Direct/FFM è ora **sotto Jackson
anche a 256 book** (−17,4%), non solo alle taglie piccole.

Una differenza rispetto alla tabella del 2026-08-23: il Direct a 256 book misura 61.849 B/op contro
i 59.776 registrati come "pavimento". **Non è dovuto alle modifiche di questa sessione**: disattivando
il percorso a slice ritenuta introdotto qui il valore resta 61.848,97 contro 61.849,03 (A/B nello
stesso binario). Va anche detto che la vecchia annotazione "59.776 = pavimento del grafo nudo" è
incoerente con i 76.369 che lo stesso file registra per il binder standard, che costruisce lo stesso
grafo: uno dei due numeri non è un pavimento. Resta da chiarire.

### Marshal: verificato per la prima volta, e Jackson vince sui messaggi grandi

`ObjectBindingGcBenchmark.verifyEquivalence()` ora copre anche `marshal`: quello che ogni backend
scrive viene riletto da un lettore JAXB RI indipendente e confrontato con il grafo sorgente.

**Il controllo ha trovato subito un difetto**: Jackson scriveva la radice come `<Catalog>` invece di
`<catalog>`, perché `XmlMapper` ignora `@XmlRootElement`. Aggiunto `@JacksonXmlRootElement` al
modello. È lo stesso tipo di invalidità che nel 2026-08-22 aveva reso inutilizzabili tutte le cifre
Jackson: senza il controllo sul marshal sarebbe passata inosservata.

| implementazione | 4 book | 32 book | 256 book |
|---|---:|---:|---:|
| **simdxml** | **783.771 ± 6.663** | **117.891 ± 1.885** | 12.385 ± 107 |
| Jackson XML | 498.538 ± 5.612 | 102.089 ± 1.415 | **13.188 ± 160** |
| JAXB RI | 320.477 ± 8.632 | 52.052 ± 634 | 6.421 ± 83 |

| B/op | 4 book | 32 book | 256 book |
|---|---:|---:|---:|
| **simdxml** | **1.504,009** | 6.128,059 | 76.312,566 |
| Jackson XML | 1.872,014 | **5.104,068** | **43.072,171** |
| JAXB RI | 5.080,022 | 26.560,134 | 202.033,090 |

simdxml vince nettamente sui messaggi piccoli (+57% a 4 book) ma **perde su throughput a 256 book
(−6,1%) e alloca il 77% in più di Jackson** alla stessa taglia. Gli intervalli sono disgiunti e gli
errori sotto l'1,7%: è un risultato, non rumore.

- [ ] **Prossimo obiettivo del marshal**: 76.313 B/op contro i 43.072 di Jackson su 11,5 KB di
      output significa che il buffer di uscita viene fatto crescere a copie ripetute. Misurare
      `Utf8XmlWriter` isolato e valutare una stima iniziale della capacità dal grafo, o una catena
      di segmenti invece di un singolo array raddoppiato.

### Parser-only, binder-only, end-to-end: la tokenizzazione domina

`BindingStageBenchmark` separa i tre stadi (P0 del file). `bindPretokenized` fa girare il binder su
eventi registrati una sola volta in `@Setup`; gli eventi portano String, quindi è il costo del binder
**senza** tokenizzazione e senza il fast path a byte grezzi — un pavimento, non una sottrazione.

| stadio | 4 book | 32 book | 256 book |
|---|---:|---:|---:|
| `tokenize` | 887.433 ± 8.221 | 158.308 ± 1.097 | 20.383 ± 351 |
| `bindPretokenized` | 1.858.821 ± 20.804 | 256.719 ± 7.439 | 30.403 ± 277 |
| `endToEnd` | 499.505 ± 5.638 | 77.450 ± 530 | 9.405 ± 135 |

In tempo per operazione: a 32 book la tokenizzazione costa 6.317 ns e il binding 3.895 ns, contro
12.912 ns end-to-end. I due stadi spiegano il 79% dell'end-to-end e si dividono **62% tokenizzazione
/ 38% binding**; a 256 book diventa 60/40. Il 20% non spiegato è l'interleaving: end-to-end il binder
tira un evento alla volta, senza i nomi già materializzati che `bindPretokenized` riceve gratis.

Conseguenza per il punto "valutare un binder specializzato generato per tipo": **può attaccare al
massimo il 40% del budget**, e solo la parte non già coperta dal piano precompilato. Il margine più
grande resta nella tokenizzazione.

### La soglia da 4.096 byte regge anche sulle forme di messaggio reali

`ThresholdBenchmark`, fixture di produzione del repository, `threshold=4096` (indice mai costruito,
default) contro `threshold=0` (indice sempre costruito), stesso JVM e stessi byte:

| fixture | byte | default (4096) | indice sempre | differenza |
|---|---:|---:|---:|---:|
| `soap/standard-soap11.xml` | 882 | **304.459 ± 3.214** | 233.380 ± 2.406 | **+30,5%** |
| `healthcare/ihe-xcpd-soap12.xml` | 1.809 | **150.017 ± 1.393** | 123.099 ± 2.072 | **+21,9%** |
| `payments/pain.001.001.09.xml` | 427 | **554.941 ± 7.510** | 418.519 ± 3.473 | **+32,6%** |

Nessuna delle tre forme — attribute-heavy, namespace-heavy, profondamente annidata — ripaga l'indice
sotto i 4 KB. **La soglia da 4.096 è confermata**; il punto aperto è chiuso.

Per rendere possibile questa misura senza un JVM per valore, `SimdXmlParserBuilder` ha ora
`withTinyDocumentThreshold(int)`, che sovrascrive `-Dorg.simdxml.tiny.threshold`.

### Memoria del percorso Direct: il segmento nativo è quello giusto

`DirectMemoryBenchmark`, stesso documento e stesso binder, `content=text`:

| | 4 book | 32 book | 256 book |
|---|---:|---:|---:|
| heap `MemorySegment` | 361.377 ± 19.771 | 49.330 ± 4.388 | 6.420 ± 124 |
| native `MemorySegment` | **424.049 ± 27.246** | **62.480 ± 3.548** | 7.132 ± 411 |
| `ByteBuffer.allocateDirect` | 416.004 ± 30.245 | 59.041 ± 4.489 | **7.457 ± 558** |

Il segmento heap è il **15-21% più lento** alle taglie piccole: non può usare il percorso indirizzo
Unsafe. Il `ByteBuffer` diretto, avvolto da `MemorySegment.ofBuffer`, è indistinguibile dal segmento
nativo. Il benchmark stampa la strategia risolta a ogni run, quindi il dato è auto-etichettante.

### Il binder Direct non decodifica più il testo che non serve

`append` conservava una String per ogni segmento di testo, anche quando la destinazione era un
`int`. Ora conserva i **bounds** del primo segmento — il segmento sottostante vive per tutto il
documento, solo il flyweight viene resettato — e converte dai byte a END con la stessa
`Property.convert(XmlRawValue)` che usa il fast path del binder standard. La String si materializza
solo se arriva un secondo segmento, o se il tipo la richiede.

A/B nello stesso binario (`-f 2`, 4 warmup + 6 iterazioni, `content=numeric`: quattro elementi
`int`/`long`/`double`/`boolean` per riga, che è la forma per cui la modifica esiste):

| | percorso disattivato | attivo | differenza |
|---|---:|---:|---:|
| 32 righe, B/op | 11.160,404 | **3.224,318** | **−71,1%** |
| 32 righe, ops/s | 18.028 ± 1.527 | **22.805 ± 1.907** | **+26,5%** |
| 256 righe, B/op | 92.603,090 | **29.114,579** | **−68,6%** |
| 256 righe, ops/s | 2.345 ± 75 | **2.827 ± 315** | **+20,5%** |

Intervalli disgiunti in entrambi i casi. Su `content=text` (la fixture catalog, il cui testo è una
String) la modifica è **neutra al byte**: 61.849,03 contro 61.848,97 B/op. È un guadagno puro dove
serve e nessun costo dove non serve.

Nello stesso lavoro sono stati corretti tre difetti che il nuovo test `DirectBindingEquivalenceTest`
ha fatto emergere:

- **CDATA con `&` letterale rompeva il binder Direct.** `append` chiamava `decodeXmlText()` anche
  sugli eventi CDATA, dove `&` non introduce un riferimento: `<![CDATA[a & b]]>` falliva con
  "Unclosed entity reference". Il difetto **precede** questa sessione: la vecchia `append` faceva la
  stessa chiamata incondizionata. Ora il binder distingue TEXT da CDATA.
- **Testo con entity finiva nel percorso numerico a byte.** `<i>&#52;2</i>` andava a `decimalInt`
  sui byte grezzi, che non espande le entity. Il segmento ritenuto è ora marcato come convertibile
  solo se non contiene `&`, esattamente come fa il binder standard.
- **Un solo nome di attributo non-ASCII disattivava tutti gli attributi.** `BindingPlan.findAttribute`
  restituiva `null` quando la dispatch a hash non era disponibile, invece di ricadere sul lookup per
  nome; il binder Direct interpretava quel `null` come "attributo sconosciuto" e lo scartava in
  silenzio. Aggiunta la mappa `localAttributes` di fallback.

### Conversioni numeriche dai byte

- **`decimalInt` faceva una divisione intera per cifra.** Il commento diceva "avoid
  Math.addExact/multiplyExact for common case", ma il controllo di overflow scritto a mano
  (`(temp - digit) / 10 != value`) è più costoso degli intrinsic che voleva evitare. Ora
  l'accumulo avviene in un `long` con moltiplicazione semplice e **un solo** controllo di range
  finale: fino a 18 cifre un `long` non può traboccare. Oltre le 18 cifre — in pratica solo zeri
  iniziali — si ricade sul parser JDK. Copertura in `BindingRegressionTest`: `Integer.MAX_VALUE`,
  `MIN_VALUE`, `-0`, zeri iniziali, overflow, cifre non valide, su percorso raw, entity e attributo.
- **`float`/`double` si convertono dai byte.** Solo la forma dimostrabilmente corretta: al più 18
  cifre significative, significando ≤ 2^53, esponente decimale entro ±22 — le condizioni sotto cui
  una singola divisione per una potenza di dieci esatta è il risultato correttamente arrotondato.
  Notazione esponenziale, `INF` e `NaN` ricadono sul parser JDK. `float` è il `double` castato, che
  è già ciò che faceva il percorso a String. `DecimalConversionTest` confronta 4.000 decimali casuali
  bit a bit con `Double.parseDouble` e 500 casi fra binder standard e binder Direct.
- **`<b>yes</b>` bindava silenziosamente a `false`.** Il fast path booleano rispondeva `false` per
  ogni forma diversa da `true`/`1`, mentre il percorso a String rifiuta. Ora entrambi rifiutano.
  Difetto preesistente, trovato scrivendo il test di equivalenza.
- `rawConvertible` include ora `double` e `float`, quindi anche il binder standard li converte senza
  costruire una String.

### `scanName` leggeva ogni byte due volte

In `SimdXmlStreamReader.scanName` e in `DirectSimdXmlParser.scanName` il ciclo era
`do { hash(b(p++)) } while (p < end && namePart(b(p)))`: ogni byte veniva caricato una volta per
l'hash e una seconda volta come condizione del giro successivo. Il byte è ora tenuto in un locale e
il cursore resta in un registro per tutta la scansione.

Sul punto "vectorizzare la ricerca del termine dei nomi XML": **non implementato, e con una ragione**.
Il terminatore di un nome XML è un test di appartenenza a un insieme, non la ricerca di un byte
specifico, e la catena FNV resta comunque sequenziale — una passata vettoriale andrebbe *aggiunta*
al ciclo di hash, non sostituita. Sui nomi tipici (3-10 byte) un vettore da 16 byte è tutto
overhead. Va tenuto aperto solo insieme a un cambio di schema di hashing.

### `same` confronta a parole, senza assumere l'endianess

`DirectSimdXmlParser.same` confrontava byte per byte a ogni tag di chiusura. Ora confronta 8 byte
per volta con `getLong`: due parole lette allo stesso modo sono uguali esattamente negli stessi casi
qualunque sia l'ordine dei byte — l'**uguaglianza**, a differenza dell'ordinamento, non richiede
alcuna assunzione sull'endianess. Il punto del file chiedeva proprio questo. `entity()` non riscandisce
più il riferimento contro cinque letterali: una dispatch sulla lunghezza e un solo confronto.

### `VectorDirectByteFinder`: due difetti, e un default allineato alle misure

Il ciclo srotolato testava `anyTrue()` **dopo ogni vettore**: quattro rami dipendenti dai dati per
iterazione, che è esattamente ciò che uno srotolamento dovrebbe evitare. Ora le quattro maschere
vengono messe in OR e il ramo è uno solo per blocco; la ricerca della corsia gira al più una volta
per chiamata.

Resta il fatto misurato due volte nel file: su una species da 128 bit ogni vettore copre 16 byte
contro gli 8 dello SWAR, mentre `fromMemorySegment` paga l'accesso controllato che
`DirectUnsafeAccess.getLong` evita. Il default `auto` **seleziona quindi lo SWAR quando la species
è sotto i 256 bit** (`VectorDirectByteFinder.beatsSwar()`); `-Dorg.simdxml.direct.strategy=vector`
lo forza comunque, ed è ciò che usa l'A/B. Il punto non è più aperto: è una decisione documentata.

`DirectByteFinderEquivalenceTest` confronta scalar/SWAR e Vector con un riferimento indipendente su
ogni offset da 0 a 40 e ogni lunghezza fino a 512, su 64 configurazioni di contenuto, più il caso
"target assente" su ogni sottointervallo — il punto "test di equivalenza scalar, SWAR e Vector su
offset e lunghezze diverse".

### `Object[]`, `Map` e `String` inutili nel common path unqualified

Il modo più netto di rispondere è il confronto fra i due binder: **a 4 e 32 book il binder standard
e il binder Direct/FFM allocano lo stesso numero di byte** (928,014 contro 928,019; 7.264,090 contro
7.264,126). Il Direct non condivide con lo standard né il reader, né `NamespaceFrame`, né la mappa
degli attributi, né le String dei nomi: qualunque allocazione superflua del percorso standard
comparirebbe come differenza. Non compare.

A 256 book però divergono: 76.369 contro 61.849, cioè **circa 57 byte per elemento in più** sul
binder standard. Il punto resta aperto lì, e solo lì.

- [x] Individuare i byte/elemento che il binder standard alloca in più del Direct a 256 book.
      → **2026-08-27**: 32 dei 57 erano l'iteratore di `directBean`, ora precalcolato. Restano circa
      24 byte per elemento, ancora da attribuire.

### Il finder vettoriale resta più lento dello SWAR anche dopo la riscrittura

A/B con `-Dorg.simdxml.direct.strategy=auto` (SWAR) contro `=vector`, `-f 2`, segmento nativo:

| fixture | SWAR-64 | vector-16 | |
|---|---:|---:|---|
| 32 righe, text | **61.902 ± 5.581** | 42.870 ± 13.122 | vector molto peggio, ma errore ampio |
| 256 righe, text | **7.897 ± 306** | 7.171 ± 228 | **SWAR +10,1%**, intervalli disgiunti |
| 32 righe, numeric | 22.757 ± 1.688 | 22.504 ± 1.526 | pari |
| 256 righe, numeric | 2.726 ± 367 | 2.641 ± 320 | pari |

Correggere lo srotolamento non è bastato: su una species da 128 bit il vantaggio di larghezza (16
byte contro 8) non copre il costo dell'accesso controllato a `MemorySegment`. La scelta di far
selezionare lo SWAR al default sotto i 256 bit è quindi **misurata**, non assunta.

### `org.simdxml.utf8.vector.threshold`: misura non conclusiva

Un fork group per valore (la soglia è letta una volta in uno `static`):

| fixture | default (4096) | vector sempre (0) |
|---|---:|---:|
| `soap/standard-soap11.xml` | 299.188 ± 5.274 | 180.578 ± 27.075 |
| `healthcare/ihe-xcpd-soap12.xml` | 147.897 ± 2.386 | **162.380 ± 2.745** |
| `payments/pain.001.001.09.xml` | 467.225 ± **110.876** | **588.292 ± 5.449** |

**Non si conclude nulla da questi numeri.** Le tre fixture danno tre direzioni diverse e due errori
sono fuori scala (15% e 24% dello score): è la firma della contaminazione dichiarata sopra, non un
effetto della soglia. Per la regola 2 il punto resta aperto.

- [ ] Rimisurare `org.simdxml.utf8.vector.threshold` a macchina davvero scarica, con una
      configurazione di controllo nello stesso run.

### Accesso al bean: ASM, reflection, VarHandle, e il costo dei namespace

`BeanAccessBenchmark`, 32 righe, un fork group per modalità
(`-Dorg.simdxml.binding.generated`, `-Dorg.simdxml.binding.access`). `GeneratedAccessFactory` ha ora
un interruttore per l'A/B; il benchmark stampa a ogni run quale accesso ha davvero risolto.

| forma | ASM generato | reflection | VarHandle |
|---|---:|---:|---:|
| `fields` (campi pubblici) | 36.078 ± 4.073 | 34.660 ± 535 | 30.226 ± 616 |
| `beans` (getter/setter) | 21.420 ± 4.698 | 23.569 ± 9.118 | 37.251 ± 611 |
| `arrays` (`int[]`) | 147.836 ± 3.854 | 108.793 ± 32.440 | 153.583 ± 6.107 |
| `wrapped` (`@XmlElementWrapper`) | 35.655 ± 5.009 | 20.996 ± 7.232 | 29.946 ± 729 |
| `namespaced` | 19.041 ± 1.829 | 20.968 ± 938 | 18.915 ± 469 |

**Buona parte di questa tabella è contaminata** e va letta con la riserva dichiarata: diversi errori
valgono il 20-30% dello score, e la riga `beans` è la prova che qualcosa non torna — quella forma usa
`ReflectionMethodAccess` in tutte e tre le modalità, quindi le tre celle *devono* essere uguali e non
lo sono. Quel 60% di differenza è rumore, non segnale.

Sopravvivono due letture:

1. **VarHandle è più lento della reflection sull'accesso ai campi**: 30.226 ± 616 contro
   34.660 ± 535, intervalli disgiunti ed errori sotto il 2%. Conferma il commento già presente in
   `XmlBindingMetadata.fieldAccess` — `auto` fa bene a non scegliere VarHandle.
2. **I namespace costano circa metà del throughput**: a parità di modalità e di numero di righe,
   `fields` fa 36.078 e `namespaced` 19.041, cioè **−47%**. È il prezzo della risoluzione di scope
   per elemento, che il percorso unqualified salta con lo short-circuit su `NamespaceFrame.EMPTY`.
   Il livello `@XmlElementWrapper`, per confronto, costa circa l'1%.

- [ ] Rifare la tabella completa a macchina scarica: solo le due letture sopra hanno errore
      abbastanza stretto da reggere.
- [ ] I namespace a −47% sono il candidato più grosso rimasto sul binder standard, più del binder
      specializzato per tipo: vale un piano di risoluzione precompilato per i modelli a namespace
      singolo, che sono la maggioranza dei modelli SOAP.

### Profilo dei due binder

`-prof jfr` non funziona su questa macchina: il profiler JMH cerca `jcmd`, che nella JRE 25 non c'è.
Usato il sampler integrato `-prof stack:lines=1;top=30;period=1`, 1 fork, 5 warmup + 12 iterazioni,
256 book. Percentuali normalizzate sui campioni RUNNABLE; metà di quelli è
`<stack is empty, everything is filtered?>`, cioè codice inlinato.

**Direct/FFM** (`DirectMemoryBenchmark`, segmento nativo):

| frame | % dei RUNNABLE non vuoti |
|---|---:|
| `DirectXmlByteSlice.local` | **10,8%** |
| `DirectXmlByteSlice.decodeXmlText` | 7,0% |
| `DirectXmlByteSlice.contains` | 5,2% |
| `ScalarDirectByteFinder.find` | 3,8% |
| `DirectXmlByteSlice.localNameHash` | 2,8% |
| `DirectXmlByteSlice.localEqualsAscii` | 2,5% |
| `DirectSimdXmlParser.scanName` | 2,4% |
| `DirectSimdXmlParser.same` | 1,9% |
| `Property.decimalInt` | 1,6% |
| `BindingPlan.findAttribute` | 1,4% |
| `DirectSimdXmlParser.skipSpace` | 1,0% |

**Binder standard** (`BindingStageBenchmark.endToEnd`):

| frame | % dei RUNNABLE non vuoti |
|---|---:|
| `XmlBinder.readDirectBean` | 15,1% |
| `SimdXmlStreamReader.openElement` | 8,1% |
| `String.decodeUTF8_UTF16` | 6,0% |
| `XmlBinder.directBean` | **5,9%** |
| `StructuralIndex.addMask` | 3,6% |
| `Property.decimalInt` | 2,4% |
| `SimdXmlStreamReader.byteRangeEquals` | 2,1% |

Due cose vanno notate subito. La prima: **`scanName` non compare più** nel profilo del binder
standard, dove era il singolo frame più caldo al 16,1%. La seconda: `skipSpace`, che il file teneva
aperto al 5-6%, è all'1,0% sul percorso Direct e non compare affatto su quello standard. Il punto è
chiuso: non è più dove sta il tempo.

- [x] `skipSpace` non è più un bersaglio: 1,0% dei campioni sul Direct, assente sullo standard.

### Tre ottimizzazioni prese dal profilo

Il profilo ha indicato tre frame che rifanno lavoro già fatto, e sono state corrette tutte e tre.

1. **`XmlBinder.directBean` era un predicato valutato per elemento**, 5,9% dei campioni. Scorreva
   tutte le proprietà del tipo per rispondere a una domanda che dipende solo dal tipo. Ora è
   `BindingPlan.directBeanCapable()`, calcolato una volta per classe; al chiamante resta l'unica
   condizione che varia davvero, `scope == NamespaceFrame.EMPTY`.
2. **`DirectXmlByteSlice.local` era il frame più caldo del percorso Direct**, 10,8%. Scandiva il nome
   in cerca di `:` a ogni reset — e fino in fondo, perché cerca l'**ultimo** due punti. Ma
   `DirectSimdXmlParser.scanName` percorre già ogni byte del nome: ora registra lì la posizione e la
   passa alla slice con un `reset` a cinque argomenti. Il colon costa zero.
3. **`DirectXmlByteSlice.contains` al 5,2%** era il costo del controllo `&` introdotto in questa
   sessione — ma `validateEntities` aveva appena fatto la stessa scansione su quello stesso
   intervallo. Ora la restituisce e la slice la riceve come hint; `decodeXmlText` la usa per saltare
   del tutto la ricerca quando non ci sono entity. Il CDATA è escluso dal ramo a byte: un `&` dentro
   una sezione CDATA è letterale, e nessuna conversione che potrebbe espanderlo deve vedere quel
   segmento — cosa che un test ha subito verificato.

Misura dopo le tre modifiche, stesso comando e stessa macchina della tabella iniziale:

| | prima | dopo | |
|---|---:|---:|---|
| simdxml 4 book | 480.622 ± 9.104 | **519.590 ± 12.382** | +8,1% |
| simdxml 32 book | 77.259 ± 986 | **82.331 ± 1.026** | +6,6% |
| simdxml 256 book | 9.576 ± 249 | **10.570 ± 199** | +10,4% |
| Direct 4 book | 387.289 ± 34.078 | **448.189 ± 18.367** | +15,7% |
| Direct 32 book | 57.174 ± 1.813 | **65.647 ± 262** | +14,8% |
| Direct 256 book | 6.447 ± 257 | **7.795 ± 60** | +20,9% |

Sei configurazioni su sei nella stessa direzione, con errori stretti; il carico esterno era presente
in entrambi i run, quindi le percentuali vanno prese come indicazione, non come cifra definitiva.

**Il dato che invece non ammette riserve è deterministico**: il binder standard a 256 book passa da
**76.368,663 a 68.144,663 B/op, −10,8%**, e la causa è nota esattamente. `plan.properties` è una
`Collections.unmodifiableList`, il cui `iterator()` alloca un wrapper a ogni chiamata: il predicato
`directBean` ne creava **uno per elemento**. 257 elementi × 32 byte = 8.224 byte, che è esattamente
la differenza misurata. A 4 e 32 book il valore è invariato al byte, perché lì il documento non
supera la soglia dove il costo si vede.

Questo risponde anche al punto P2 "verificare che nel common path unqualified non vengano creati
`Object[]`, `Map` o `String` inutili": **ce n'era uno**, era un iteratore, ed è sparito. Il divario
residuo verso il binder Direct a 256 book scende da 14.520 a **6.296 byte** (circa 24 byte per
elemento).

## Risultati da non confondere

I benchmark JMH finora sono stati eseguiti non-forked (`-f 0`), quindi sono diagnostici e non
adatti a una conclusione definitiva. Valori osservati:

| Backend | Throughput osservato |
|---|---:|
| Jackson XML | 52–55k ops/s |
| simdxml standard | ~2k ops/s nel benchmark object-binding corrente |
| simdxml Direct/FFM | ~1.7–1.8k ops/s, con run rumorosi |

Non dichiarare ancora che simdxml supera Jackson. Servono fork JVM validi, fixture identiche,
`-prof gc` e almeno 5 warmup / 8 measurement per fork.

**Aggiornamento 2026-08-23**: i valori Jackson di questa tabella sono invalidi — Jackson non stava
bindando nulla. Corretto il benchmark e rimisurato a macchina scarica: il sorpasso del binder
standard è **dimostrato** (+53% a 32 book, +118% a 4 book). Vedi le due sezioni sopra.

## P0 — benchmark affidabile

- [x] Benchmark JMH forked su JDK 25 (`-f 2`), eseguito il 2026-08-22.
- [x] Separare parser-only, binder-only su eventi pretokenizzati ed end-to-end XML → bean.
      → **2026-08-27**: `BindingStageBenchmark`: 62% tokenizzazione / 38% binding.

- [x] `ObjectBindingGcBenchmark.verifyEquivalence()` verifica in `@Setup` che i quattro backend
      producano lo stesso grafo; il benchmark fallisce se divergono.
- [x] Registrati `ops/s`, `gc.alloc.rate.norm`, `gc.count`, `gc.time` ed errore JMH.
- [x] Eseguire input piccoli (1–4 book), medi (32 book) e grandi (256+ book).
      → **2026-08-27**: 4 / 32 / 256 in ogni tabella.


## P1 — core Direct/FFM

- [x] Profilare `DirectSimdXmlParser` con JFR/async-profiler separando `validateUtf8`, `findByte`,
      `scanName`, parsing attributi, callback e decode UTF-8.
      → **2026-08-27**: fatto con `-prof stack`; `-prof jfr` non funziona senza `jcmd`.

- [ ] Vectorizzare la ricerca del termine dei nomi XML, non solo la ricerca di `<` e `&`.
      → **2026-08-27**: analizzato e **non implementato**, con motivazione. Il terminatore di un nome
      XML è un test di appartenenza a un insieme, non la ricerca di un byte, e la catena FNV resta
      sequenziale: una passata vettoriale andrebbe *aggiunta* al ciclo di hash, non sostituita. Sui
      nomi tipici (3-10 byte) un vettore da 16 byte è tutto overhead. Da riaprire solo insieme a un
      cambio di schema di hashing. Nel frattempo `scanName` non legge più ogni byte due volte.
- [x] Ridurre le chiamate `DirectUnsafeAccess.getByte` nel loop di `scanName` usando blocchi
      word/vector con fallback sui bordi.
      → **2026-08-27**: ogni byte veniva letto due volte; ora una.

- [x] Ottimizzare `same` e `ascii` senza assumere endianess non documentata.
      → **2026-08-27**: `same` confronta a parole; `ascii` sostituito da una dispatch sulla lunghezza.

- [x] Misurare heap `MemorySegment`, native `MemorySegment` e `ByteBuffer.allocateDirect`.
      → **2026-08-27**: `DirectMemoryBenchmark`: heap 15-21% più lento.

- [x] Verificare e registrare il finder effettivo Vector/SWAR su JRE 25.
      → **2026-08-27**: ogni benchmark stampa la strategia risolta.

- [x] Aggiungere test di equivalenza scalar, SWAR e Vector su offset e lunghezze diverse.
      → **2026-08-27**: `DirectByteFinderEquivalenceTest`.


## P1 — binding Direct

- [x] Ridurre le allocazioni di `DirectXmlBeanBinder` senza introdurre pooling che peggiori il JIT.
      → **2026-08-27**: −71% su testo numerico grazie alla slice ritenuta, senza pooling aggiuntivo;
      neutro al byte sul testo String.
- [x] Valutare stack array-based al posto di `ArrayDeque` solo dopo benchmark isolato.
      → già fatto il 2026-08-23 (`Frame` in stack ad array poolato); nessun `ArrayDeque` rimasto nel
      binder Direct.
- [x] Conservare una slice raw per testo semplice a segmento singolo e decodificare una sola volta a END.
      → **2026-08-27**: −71% allocazioni su testo numerico.

- [x] Implementare parsing Direct di `float`/`double` e numeri grandi senza conversione intermedia a
      String, con test di overflow e whitespace XML.
      → **2026-08-27**: solo la forma dimostrabilmente correttamente arrotondata, resto al parser JDK.

- [x] Aggiungere fast path per `String`, `int`, `long`, `boolean` e liste omogenee.
      → **2026-08-27**: più `double`/`float`; `rawConvertible` esteso.

- [x] Precompilare anche la tabella degli attributi per hash/byte name, evitando il doppio loop attuale.
      → **2026-08-27**: già presente; corretto il fallback che scartava gli attributi in silenzio.

- [ ] Valutare un binder specializzato generato per tipo, con fallback al binder generico JAXB.
      → **2026-08-27**: ora c'è il dato per decidere. `BindingStageBenchmark` divide il costo in
      **62% tokenizzazione / 38% binding** (60/40 a 256 book): un binder generato per tipo può
      attaccare al massimo quel 38%, e solo la parte non già coperta dal piano precompilato. I due
      candidati misurati sono più grossi: i namespace costano il **47%** del throughput, e il marshal
      alloca il **77% più di Jackson** a 11,5 KB. Rimandato dopo quelli.
- [x] Documentare che il percorso Direct corrente è namespace-free; il binder standard resta il
      percorso per namespace, wrapper e modelli JAXB complessi.
      → **2026-08-27**: javadoc di `DirectSimdXmlParser` e `ARCHITECTURE.md`.


## P2 — binder standard

- [x] Ripetere benchmark del binder standard con JRE 25 forked: il risultato ~2k ops/s va isolato
      dal costo di `SimdJaxbContext`, parser, reflection e fixture.
      → **2026-08-27**: rifatto; il vecchio ~2k ops/s era il conteggio a oggetti.

- [x] Confrontare ASM/generated, reflection e VarHandle con benchmark separati.
      → **2026-08-27**: `BeanAccessBenchmark`; solo la riga `fields` ha errore abbastanza stretto.

- [x] Verificare che nel common path unqualified non vengano creati `Object[]`, `Map` o `String` inutili.
      → **2026-08-27**: identico al byte al binder Direct a 4 e 32 book; aperto a 256.

- [x] Misurare field access, property access, liste, array primitivi, wrapper e namespace.
      → **2026-08-27**: i namespace costano il 47%.


## MTOM/XOP JAXB — stato e handoff

- [x] Bridge framework-neutral `AttachmentMarshaller`/`AttachmentUnmarshaller` per Jakarta e `javax`.
- [x] `byte[]` MTOM con overload JAXB offset/length, senza copia nel core.
- [x] `DataHandler` passato direttamente all'overload JAXB quando disponibile.
- [x] Fallback Base64 inline quando il marshaller non abilita XOP.
- [x] Precompilare `@XmlMimeType`, `@XmlInlineBinaryData` e `@XmlAttachmentRef` nel `Property` model.
      → **2026-08-27**: fatto, più `binary()`.

- [x] Completare il consumo XOP su byte/StAX/SAX e verificare il content-id senza riallocazioni.
      → **2026-08-27**: fatto su tutti e tre; sette difetti corretti, elencati in
      [MTOM_HANDOFF.md](MTOM_HANDOFF.md).
- [x] Benchmark JMH separati per inline, MTOM `byte[]`, MTOM `DataHandler` e unmarshal.
      → **2026-08-27**: `MtomBenchmark`. Allocazioni MTOM **costanti** nella dimensione del payload
      (1.248 B/op a 1 KB e a 64 KB) contro 517.403 B/op dell'inline a 64 KB.

Il dettaglio operativo per la ripresa è in [MTOM_HANDOFF.md](MTOM_HANDOFF.md).

## Test obbligatori

```shell
JAVA_HOME=/home/lop/jdk-24.0.2+12 /home/lop/Scaricati/apache-maven-3.9.0/bin/mvn -q test
JAVA_HOME=/home/lop/jdk-24.0.2+12 /home/lop/Scaricati/apache-maven-3.9.0/bin/mvn -q install -DskipTests
JAVA_HOME=/home/lop/jdk-24.0.2+12 /home/lop/Scaricati/apache-maven-3.9.0/bin/mvn -f integrations/pom.xml test
```

Al 2026-08-27: **90 test nel core** e 59 nelle integrazioni (31 Jakarta, 22 javax, 1 Axis2, 5
WebLogic), tutti verdi. Test aggiunti in questa sessione:

| test | copre |
|---|---|
| `DecimalConversionTest` | 4.000 decimali casuali confrontati bit a bit con `Double.parseDouble`, limiti di `int`/`long`, booleani, accordo fra binder standard e Direct |
| `DirectBindingEquivalenceTest` | il binder Direct contro quello standard su entity, CDATA, testo diviso, attributi, liste, contenuto misto, su segmento heap, nativo e `ByteBuffer` diretto |
| `DirectByteFinderEquivalenceTest` | scalar/SWAR e Vector contro un riferimento indipendente, su ogni offset e lunghezza |
| `BindingRegressionTest` (esteso) | limiti decimali su percorso raw, entity, attributo e lista |
| `SimdMtomTest` (Jakarta e javax) | MTOM, swaRef, `@XmlMimeType`, `@XmlInlineBinaryData`, fallback base64, su byte, StAX e SAX |

JRE 25 disponibile:

```text
/home/lop/Scaricati/oracleLight/jdk-25+36-jre/bin/java
```

Il classpath JMH sta in `/tmp` e si perde al riavvio; rigenerarlo con:

```shell
JAVA_HOME=/home/lop/jdk-24.0.2+12 /home/lop/Scaricati/apache-maven-3.9.0/bin/mvn -q test-compile \
  dependency:build-classpath -Dmdep.outputFile=/tmp/simdxml-jmh-cp -Dmdep.includeScope=test
```

Benchmark diagnostico:

```shell
CP="target/classes:target/test-classes:$(cat /tmp/simdxml-jmh-cp)"
/home/lop/Scaricati/oracleLight/jdk-25+36-jre/bin/java \
  --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED \
  -cp "$CP" org.openjdk.jmh.Main \
  'org.simdxml.ObjectBindingGcBenchmark.bind' -prof gc \
  -p library=simdxml,simdxml-direct,jaxb-ri,jackson \
  -p operation=unmarshal -p books=32
```

Per un risultato pubblicabile usare almeno `-f 2` e verificare che i fork partano correttamente.

## Regole di lavoro

1. Non rimuovere le ottimizzazioni marcate `[x]` senza benchmark A/B e motivazione.
2. Non usare risultati `-f 0` per dichiarare un sorpasso di Jackson.
3. Ogni modifica al core Direct deve avere test scalar/SWAR/Vector o motivare perché non è applicabile.
4. Ogni modifica al binding deve verificare entity, CDATA, attributi, liste e bean generated.
5. Aggiornare questo file con risultato, comando, JDK, fork, warmup, measurement e fixture.
