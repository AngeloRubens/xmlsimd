# Provider-neutral compatibility gates

These gates exercise standard JAXB contracts through the public API and simdxml provider.
They are inspired by the functional areas covered by the Eclipse JAXB RI tests, but they
do not copy RI implementation-specific assertions or claim to run the complete RI suite.

Reference baselines are pinned to the provider versions used by the integration modules:

* Jakarta: Eclipse JAXB RI `4.0.9`, tag/release `4.0.9`
* Javax: Eclipse JAXB RI `2.3.9`, tag/release `2.3.9`
* upstream: `https://github.com/eclipse-ee4j/jaxb-ri`
* upstream license: Eclipse Distribution License 1.0 (`LICENSE.md`)

The exact provider-neutral coverage and exclusions are recorded in
`jaxb-ri-manifest.tsv`. The simdxml tests are original project tests under this
repository's license; no upstream test source is vendored.

Run both API generations sequentially:

```sh
JAVA_HOME=/path/to/jdk MVN=/path/to/mvn \
  compatibility/provider-neutral-kits/run-jaxb-ri-neutral.sh
```

This is an engineering compatibility run, not an Eclipse JAXB RI certification or a
Jakarta Compatibility Test Suite result.
