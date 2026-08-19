#!/bin/sh
set -eu

JAVA_CMD=${JAVA_CMD:-java}
CLASSPATH_FILE=${CLASSPATH_FILE:-target/benchmark-classpath.txt}
RESULTS_FILE=${RESULTS_FILE:-target/benchmark-results.txt}
CP="target/classes:target/test-classes:$(cat "$CLASSPATH_FILE")"
FLAGS="-server -XX:ActiveProcessorCount=2 -Xms1500m -Xmx1500m -XX:+AlwaysPreTouch -XX:+UseParallelGC -XX:+DisableExplicitGC -XX:-UsePerfData"

# JDK 24 exposes Vector API as an incubator module; later JDKs may expose it
# without that module name. Keep the same benchmark usable across both.
VECTOR_FLAGS=""
if "$JAVA_CMD" --list-modules 2>/dev/null | grep -q '^jdk\.incubator\.vector@'; then
    VECTOR_FLAGS="--add-modules jdk.incubator.vector"
fi

mkdir -p "$(dirname "$RESULTS_FILE")"
: > "$RESULTS_FILE"
run() {
    JVM_ARGS=""
    while [ "$#" -gt 0 ] && [ "${1#-D}" != "$1" ]; do
        JVM_ARGS="$JVM_ARGS $1"
        shift
    done
    if [ "$#" -lt 1 ]; then
        echo "benchmark invocation has no main class" >&2
        exit 64
    fi
    MAIN_CLASS=$1
    shift
    printf 'command=%s %s\n' "$MAIN_CLASS" "$*" | tee -a "$RESULTS_FILE"
    # Each invocation is intentionally a new, sequential JVM.
    # JVM properties must precede -cp and the main class; application arguments follow it.
    "$JAVA_CMD" $FLAGS $VECTOR_FLAGS $JVM_ARGS -cp "$CP" "$MAIN_CLASS" "$@" | tee -a "$RESULTS_FILE"
}

printf 'java=%s\n' "$($JAVA_CMD -version 2>&1 | head -1)" | tee -a "$RESULTS_FILE"
printf 'cpu=%s\n' "$(uname -m)-$(getconf _NPROCESSORS_ONLN)cpus" | tee -a "$RESULTS_FILE"
sha256sum src/test/resources/soap/standard-soap11.xml \
    src/test/resources/healthcare/ihe-xcpd-soap12.xml \
    src/test/resources/payments/pain.001.001.09.xml | tee -a "$RESULTS_FILE"

for library in simdxml simdxml-reuse simdxml-bytes simdxml-direct jdk-stax woodstox jackson-woodstox jackson-jdk xerces-sax; do
    run org.simdxml.XmlLibraryBenchmark "$library" src/test/resources/soap/standard-soap11.xml 20000
done
for validation in strict none; do
    run -Dorg.simdxml.utf8.validation="$validation" org.simdxml.XmlLibraryBenchmark simdxml-bytes src/test/resources/soap/standard-soap11.xml 20000
    run -Dorg.simdxml.utf8.validation="$validation" org.simdxml.XmlLibraryBenchmark simdxml-direct src/test/resources/soap/standard-soap11.xml 20000
done
for mode in vertical-on vertical-off direct-vertical jaxb; do
    run org.simdxml.HealthcareVerticalBenchmark "$mode" src/test/resources/healthcare/ihe-xcpd-soap12.xml 20000
done
for mode in packed generic fixed auto; do
    run org.simdxml.PaymentVerticalBenchmark "$mode" src/test/resources/payments/pain.001.001.09.xml 100000
done
for mode in simd-unmarshal jaxb-unmarshal simd-marshal jaxb-marshal; do
    run org.simdxml.ComplexBindingBenchmark "$mode" 10000 16
done
