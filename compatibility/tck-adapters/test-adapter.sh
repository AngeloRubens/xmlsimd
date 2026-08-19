#!/usr/bin/env sh
# SPDX-License-Identifier: Apache-2.0
set -eu

adapter_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
fixture=$(mktemp -d)
output=$(mktemp -d)
trap 'rm -rf "$fixture" "$output"' EXIT HUP INT TERM

mkdir -p "$fixture/jaxb-tck/tests/sample"
printf '%s\n' 'EPL test fixture' > "$fixture/LICENSE.md"
printf '%s\n' 'NOTICE test fixture' > "$fixture/NOTICE"
cat > "$fixture/jaxb-tck/tests/sample/Model.java" <<'EOF'
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.activation.DataHandler;
EOF
cat > "$fixture/jaxb-tck/tests/sample/binding.xjb" <<'EOF'
<jaxb:bindings xmlns:jaxb="https://jakarta.ee/xml/ns/jaxb" version="3.0"/>
EOF
cat > "$fixture/pom.xml" <<'EOF'
<project><dependencies><dependency><groupId>jakarta.xml.bind</groupId><artifactId>jakarta.xml.bind-api</artifactId></dependency></dependencies></project>
EOF

"$adapter_dir/jaxb-jakarta-to-javax.sh" "$fixture" "$output"
rg -q 'import javax\.xml\.bind\.JAXBContext' "$output/jaxb-tck/tests/sample/Model.java"
rg -q 'import javax\.activation\.DataHandler' "$output/jaxb-tck/tests/sample/Model.java"
rg -q 'http://java.sun.com/xml/ns/jaxb' "$output/jaxb-tck/tests/sample/binding.xjb"
rg -q 'version="2.1"' "$output/jaxb-tck/tests/sample/binding.xjb"
rg -q '<groupId>javax.xml.bind</groupId><artifactId>jaxb-api</artifactId>' "$output/pom.xml"
test -f "$output/SIMDXML-JAVAX-ADAPTATION.txt"
test -f "$output/NOTICE"
echo 'JAXB Jakarta-to-Javax adapter smoke test passed'
