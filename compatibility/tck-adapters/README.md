# External compatibility corpus adapters

This directory contains reproducible adapters; it does not vendor third-party test suites.

## Jakarta JAXB source corpus on the Javax provider

The public Jakarta XML Binding TCK source can provide a useful, broad regression corpus for the
Java EE 8 provider. It cannot turn into Oracle's JAXB 2.3/JCP TCK by renaming packages. JAXB 2.3
and Jakarta XML Binding 4 differ in API surface and provider-discovery requirements, and an adapted
run is therefore reported only as an **unofficial simdxml compatibility run**.

Pin the upstream checkout, then stage a modified copy outside the repository:

```shell
git clone https://github.com/jakartaee/jaxb-tck.git target/upstream/jaxb-tck
git -C target/upstream/jaxb-tck checkout d13c23a68a1099668a073514b576f4c504be0e9d
compatibility/tck-adapters/jaxb-jakarta-to-javax.sh \
  target/upstream/jaxb-tck target/corpora/jaxb-tck-javax
```

The adapter preserves upstream licenses/notices, changes only JAXB/Activation API names and the
standard external-binding namespace, rejects residual Jakarta references, and records provenance.
It never edits the upstream checkout. `jaxb-javax-exclusions.txt` is the mandatory audit trail:
every non-applicable normative test or genuine implementation failure must be listed individually.

Tests common to both generations should run against both simdxml providers. Tests tied to Jakarta
4 discovery rules stay in the Jakarta run; JAXB 2.3 API signatures and legacy discovery behavior
must be tested from independently licensed Javax sources or newly authored provider-neutral tests.
Oracle/JCP binary TCK material must not be copied, transformed, or committed by this adapter.

The upstream source repository contains EPL-2.0 material and also an EFTL notice for packaged TCK
artifacts. Review each selected file's header and preserve `LICENSE.md`, `LICENSE_EFTL.md` and
`NOTICE`. Do not describe partial/adapted results as Jakarta certification or TCK compatibility.
