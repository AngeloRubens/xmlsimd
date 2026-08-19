# Healthcare XML validation gate

Run the original, dependency-free level-1 gate with:

```sh
mvn -f compatibility/pom.xml -Phealthcare-validation \
  -pl :simdxml-healthcare-validation -am test
```

This gate distinguishes SOAP transport from the payload family and compares simdxml's
generic and healthcare-optimized projections for HL7 v3, CDA and FHIR XML payloads.
HL7 v2 ER7 is not XML and is explicitly out of scope; `v2.xml` needs a separate profile.

No external healthcare corpus is downloaded. `capability-manifest.tsv` requires an exact
version, immutable URL, SHA-256 and verified license before automation is enabled. A pass
does not claim HL7, CDA, FHIR, IHE or clinical conformance.
