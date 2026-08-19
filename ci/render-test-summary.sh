#!/usr/bin/env sh
# Render Maven Surefire reports as a compact GitHub Actions Markdown summary.
set -eu

title=${1:-Test results}
summary=${GITHUB_STEP_SUMMARY:-}
if [ -z "$summary" ]; then
    echo "GITHUB_STEP_SUMMARY is not set; nothing to render" >&2
    exit 0
fi

value() {
    file=$1
    name=$2
    sed -n "s/.*${name}=\"\([0-9][0-9]*\)\".*/\1/p" "$file" | head -n 1
}

printf '## %s\n\n' "$title" >> "$summary"
printf '| Suite | Tests | Failures | Errors | Skipped |\n|---|---:|---:|---:|---:|\n' >> "$summary"
count=0
for report in $(find . -type f -path '*/target/surefire-reports/TEST-*.xml' -print 2>/dev/null | sort); do
    tests=$(value "$report" tests); failures=$(value "$report" failures)
    errors=$(value "$report" errors); skipped=$(value "$report" skipped)
    tests=${tests:-0}; failures=${failures:-0}; errors=${errors:-0}; skipped=${skipped:-0}
    suite=$(basename "$report" .xml | sed 's/^TEST-//')
    printf '| `%s` | %s | %s | %s | %s |\n' "$suite" "$tests" "$failures" "$errors" "$skipped" >> "$summary"
    count=$((count + 1))
done
if [ "$count" -eq 0 ]; then
    printf '| No Surefire XML reports found | — | — | — | — |\n' >> "$summary"
fi
printf '\n' >> "$summary"
