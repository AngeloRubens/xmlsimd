# W3C XML Test Suite runner

This opt-in module runs the W3C XML Conformance Test Suite release `20130923` as
an **unofficial engineering compatibility corpus**. Passing this runner, especially
when capabilities are excluded, is not a W3C conformance or certification claim.

The archive is downloaded from the W3C at build time and is not committed to this
repository:

* URL: `https://www.w3.org/XML/Test/xmlts20130923.tar.gz`
* SHA-256: `9b61db9f5dbffa545f4b8d78422167083a8568c59bd1129f94138f936cf6fc1f`
* Release page: `https://www.w3.org/XML/Test/`
* W3C copyright/license information: `https://www.w3.org/copyright/software-license-2023/`

The individual contributions in the historical suite retain their upstream notices.
See the comments and readme files inside the downloaded archive. The archive is not
redistributed by simdxml; review W3C and contributor terms before redistributing it.

Run sequentially in one test JVM:

```sh
JAVA_HOME=/path/to/jdk mvn -f compatibility/pom.xml \
  -Pw3c-xml-kit -pl :simdxml-w3c-xml-kit -am test
```

The runner writes `target/w3c-xmlts-results.tsv` and
`target/w3c-xmlts-summary.txt`. Oracle JDK StAX is checked as a non-validating XML
processor. Simdxml is checked only for its declared secure core profile: XML 1.0,
UTF-8/ASCII input, no DTD or external entity, and no namespace-conformance claim.
Other tests are counted by an explicit `UNSUPPORTED_*` capability reason.
Known failures inside the selected core profile are never silently excluded: their W3C
IDs live in `src/test/resources/w3c-xmlts-known-gaps.txt`, appear as `KNOWN_GAP_*` in
the TSV, and are counted in the summary. Any new failure breaks the build.
