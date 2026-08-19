# Simdxml vertical compatibility kit

This is a project-specific semantic A/B gate, independent from the W3C XML suite and
the JAXB RI provider-neutral gates. `VerticalCompatibilityKitTest` compares accelerated,
generic, fixed-profile and automatic-discovery results over identical fixtures. It also
checks builder/JVM-property selection and a two-thread shared parser run.

`verticalization-inventory.tsv` is the source of truth for coverage. Enum values or names
without a fixture are reported as gaps and must not be advertised as tested support.
