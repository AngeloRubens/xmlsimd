#!/usr/bin/env sh
# SPDX-License-Identifier: Apache-2.0
# Stages an EPL-2.0 Jakarta XML Binding TCK source checkout as an unofficial
# JAXB 2.3/Javax compatibility corpus. It does not create an Oracle/JCP TCK.
set -eu

if [ "$#" -ne 2 ]; then
    echo "usage: $0 <jakarta-jaxb-tck-checkout> <empty-output-directory>" >&2
    exit 64
fi

source_dir=$1
output_dir=$2

if [ ! -f "$source_dir/LICENSE.md" ] || [ ! -d "$source_dir/jaxb-tck/tests" ]; then
    echo "source is not a Jakarta XML Binding TCK source checkout" >&2
    exit 65
fi
if [ -e "$output_dir" ] && [ "$(find "$output_dir" -mindepth 1 -print -quit 2>/dev/null)" ]; then
    echo "output directory must be absent or empty: $output_dir" >&2
    exit 73
fi

mkdir -p "$output_dir"
find "$source_dir" -mindepth 1 -maxdepth 1 ! -name '.git' \
    -exec cp -R {} "$output_dir"/ \;

# Keep this list deliberately narrow. Package/API names and the JAXB binding
# namespace changed; XML application namespaces and javax.xml.namespace did not.
find "$output_dir" -type f \( \
    -name '*.java' -o -name '*.xml' -o -name '*.xsd' -o -name '*.xjb' -o \
    -name '*.properties' -o -name '*.mk' -o -name '*.jtx' -o -name '*.jte' -o \
    -name '*.sh' -o -name 'pom.xml' \
    \) -exec perl -pi -e '
        s#(<artifactId>)jakarta\.xml\.bind-api(</artifactId>)#$1jaxb-api$2#g;
        s#(<artifactId>)jakarta\.activation-api(</artifactId>)#$1javax.activation-api$2#g;
        s#(<groupId>)jakarta\.xml\.bind(</groupId>)#$1javax.xml.bind$2#g;
        s#(<groupId>)jakarta\.activation(</groupId>)#$1javax.activation$2#g;
        s/jakarta\.xml\.bind/javax.xml.bind/g;
        s/jakarta\.activation/javax.activation/g;
        s#https://jakarta\.ee/xml/ns/jaxb#http://java.sun.com/xml/ns/jaxb#g;
    ' {} +

# Version 3.0 belongs to Jakarta's external-binding schema. Limit this rewrite
# to binding customisation documents so application schemas are untouched.
find "$output_dir" -type f -name '*.xjb' -exec perl -pi -e \
    's/version="3\.0"/version="2.1"/g; s/jaxb:version="3\.0"/jaxb:version="2.1"/g' {} +

# A Jakarta API signature file cannot be reinterpreted as a JAXB 2.3 signature
# test. The Javax signature must come from the licensed JAXB 2.3 API artifact.
signature="$output_dir/jaxb-tck/tests/api/signaturetest/sig/jakarta.xml.bind.sig"
if [ -f "$signature" ]; then
    mv "$signature" "$signature.NOT_APPLICABLE_TO_JAVAX"
fi

revision=unknown
if command -v git >/dev/null 2>&1 && git -C "$source_dir" rev-parse HEAD >/dev/null 2>&1; then
    revision=$(git -C "$source_dir" rev-parse HEAD)
fi
cat > "$output_dir/SIMDXML-JAVAX-ADAPTATION.txt" <<EOF
This directory is an unofficial JAXB 2.3/Javax compatibility corpus generated
from Jakarta XML Binding TCK source revision $revision.

It is a modified work, not the Oracle/JCP JAXB TCK and not evidence of an
official compatibility or certification claim. Upstream copyright, license and
notices remain applicable. See simdxml's tck-adapters README and exclusions.
EOF

residual=$(find "$output_dir/jaxb-tck/tests" -type f \( \
    -name '*.java' -o -name '*.xml' -o -name '*.xsd' -o -name '*.xjb' -o \
    -name '*.properties' \) -exec grep -lE 'jakarta\.xml\.bind|jakarta\.activation' {} + \
    2>/dev/null || true)
if [ -n "$residual" ]; then
    echo "unconverted Jakarta API references remain:" >&2
    echo "$residual" >&2
    exit 1
fi

echo "staged unofficial Javax corpus in $output_dir"
