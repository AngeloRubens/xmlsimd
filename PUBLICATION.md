# Public technical-preview checklist

This file is the release gate for publishing simdxml-java and presenting it to GlassFish, CXF,
Metro or application-server teams. It prevents a fast local result from being mistaken for a
portable compatibility or certification claim.

## What can be stated now

- The core has a forward-only byte-oriented parser with Vector, SWAR, Unsafe and VarHandle paths.
- The Jakarta and Javax JAXB provider smoke suites run sequentially on the local reactor.
- The current local suite reports 75 passing reactor tests, plus opt-in W3C, vertical, ISO and
  healthcare gates; exact counts and revisions belong in the CI artifacts.
- Preliminary local measurements show the flyweight and vertical paths can outperform the selected
  JDK/Woodstox baselines for the tested XML shapes. The measurements are not universal claims.
- The project includes opt-in integration profiles for Java EE 8/Open Liberty/Tomcat and Jakarta
  Quarkus/CXF paths. A runtime-specific pass is not an application-server certification.

## What must not be claimed yet

- “JAXB-compatible” without naming the tested API generation and supported method subset.
- “GlassFish/WebLogic drop-in replacement” or any TCK certification.
- “SEPA-valid”, “HL7-compliant” or “clinically valid” from XML/XSD parsing alone.
- “Faster than Woodstox/Jackson” without the exact dataset, backend, validation mode, JVM and
  checksum being published with the result.

## Before the first public release tag

- [ ] Run the complete sequential Maven reactor on the release commit.
- [ ] Run the Java 8 linkage/provider gate and the supported LTS matrix available in CI.
- [ ] Run Jakarta and Javax provider-neutral tests with pinned JAXB RI reference versions.
- [ ] Run W3C representative, vertical, healthcare and ISO level-one profiles; publish manifests.
- [ ] Run CXF/Metro/Open Liberty/Tomcat smoke profiles in CI and upload raw reports.
- [ ] Record benchmark host CPU, OS, JDK build, GC, `Xms/Xmx`, active processors, warmups,
  iterations, input SHA-256 and checksum. Execute library comparisons in separate JVMs.
- [ ] Finish or explicitly mark unsupported JAXB areas: listeners/callbacks, MTOM/XOP, advanced
  mixed content, DOM overloads and remaining `xsi:type` provider paths.
- [ ] Review every external corpus license and preserve NOTICE/provenance. Do not vendor EFTL or
  Oracle/JCP material without the applicable permission.
- [ ] Generate a release note containing known limitations and a reproducibility command block.

## Suggested presentation order

1. Explain the two-stage byte-oriented design and why XML grammar state remains in stage 2.
2. Show the same XML through generic, flyweight, vertical and validation-enabled paths.
3. Show preliminary throughput/latency tables with checksums and commands.
4. Demonstrate unchanged JAXB discovery on one Jakarta and one Java EE 8 runtime.
5. Show compatibility gaps and the next test gates instead of implying certification.

## Reproducibility rule

Every published number must link to [BENCHMARKS.md](BENCHMARKS.md), identify the exact command and
input digest, and state whether UTF-8 validation and vertical discovery were enabled. A benchmark
without those fields is an internal observation, not a public result.
