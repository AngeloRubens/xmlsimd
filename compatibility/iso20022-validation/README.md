# ISO 20022 / SEPA layered validation

The opt-in `iso20022-validation` profile implements only level 1 today. Run it with
`-f compatibility/pom.xml -Piso20022-validation -pl :simdxml-iso20022-validation -am test`.

External schemas are deliberately not downloaded yet. Every source must first have an
immutable artifact URL, exact version, SHA-256 and reviewed redistribution/use terms in
`schema-sources.tsv`. Entries containing `UNRESOLVED` or `REQUIRED_BEFORE_ENABLE` are a
hard block, not placeholders accepted by CI.

ISO 20022 XSD validity is not ISO certification. EPC explicitly describes its XSDs as
Technical Validation Subsets and not production schemas; XSD/TVS validity alone must
never be presented as SEPA scheme compliance. Business rules and dated positive/negative
fixtures remain separate level-4 gates.
