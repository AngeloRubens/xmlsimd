#!/usr/bin/env sh
# Put a benchmark text report in a readable, collapsible Markdown section.
set -eu

title=${1:-Benchmark results}
report=${2:?report path required}
summary=${GITHUB_STEP_SUMMARY:-}
if [ -z "$summary" ] || [ ! -f "$report" ]; then
    exit 0
fi

{
    printf '## %s\n\n' "$title"
    printf '<details><summary>Raw sequential measurements</summary>\n\n```text\n'
    cat "$report"
    printf '```\n\n</details>\n\n'
} >> "$summary"
