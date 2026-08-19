#!/usr/bin/env sh
set -eu

: "${JAVA_HOME:?Set JAVA_HOME to the JDK used for the compatibility run}"
MVN=${MVN:-mvn}
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)

# One Maven reactor, no parallel builder; each provider's single reusable test JVM runs
# after the preceding module has completed.
exec "$MVN" -f "$ROOT/integrations/pom.xml" \
  -pl :simdxml-jaxb-provider,:simdxml-jaxb-javax-provider -am \
  -DskipTests=false -Dsurefire.parallel=none -T1 test
