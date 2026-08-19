# Local release and push procedure

This project does not contain a remote URL or credentials. Configure those only in your local
Git client. The commands below prepare and publish the first technical-preview tag without
rewriting history or staging generated build output.

## One-time repository setup

Run these commands from the project root. If this directory is only a working-tree export and
does not contain `.git`, initialize it first:

```shell
git init -b main                                      # only when .git is absent
git remote -v
git remote add origin https://github.com/<ACCOUNT>/<REPOSITORY>.git   # only if origin is absent
git fetch origin
git switch main
```

If the destination repository already has a `main` branch, use `git switch main` and integrate
the files normally. Do not use `git push --force` for the initial publication.

## Pre-push checks

```shell
mvn test
mvn -f compatibility/pom.xml -Piso20022-validation \
  -pl :simdxml-iso20022-validation -am test
mvn -f compatibility/pom.xml -Phealthcare-validation \
  -pl :simdxml-healthcare-validation -am test
mvn -f integrations/pom.xml -pl :simdxml-server-smoke-javaee8 -am verify -Ptomcat-it
mvn -f integrations/pom.xml -pl :simdxml-server-smoke-javaee8 -am verify -Popenliberty-it
```

The last two commands are sequential and download their runtimes into `target`; those files are
ignored by Git. Run the longer W3C/JAXB/benchmark profiles in GitHub Actions or manually when the
required external artifacts are available.

## Review and tag

```shell
git status --short --untracked-files=all
git diff --check
git diff --stat
git add -A
git diff --cached --check
git commit -m "Release simdxml-java 0.1.0-alpha"
git tag -a v0.1.0-alpha -m "simdxml-java technical preview"
git push -u origin main
git push origin v0.1.0-alpha
```

Before `git add -A`, verify that no credentials, downloaded datasets, `target/` files, dumps or
benchmark result files are staged. The `.gitignore` and `.gitattributes` files are part of the
release preparation.

## GitHub post-push checks

After pushing, wait for the required Actions to complete in this order:

1. core/provider and Java EE 8 smoke workflows;
2. external compatibility kits;
3. scheduled/manual CXF, ISO/healthcare and server profiles;
4. sequential performance matrix and uploaded raw benchmark artifacts.

The release is a technical preview. Publish benchmark results with their dataset digest, JVM
flags, backend, validation mode and checksum. Do not describe the tag as JAXB, GlassFish, SEPA,
HL7 or Jakarta TCK certification.
