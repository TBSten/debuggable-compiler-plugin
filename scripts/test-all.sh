#!/usr/bin/env bash
#
# test-all.sh — run `:debuggable-compiler:test` against every supported Kotlin
#               compiler version by varying `-Ptest.kotlin=X`.
#
# The test module's build.gradle.kts maps each Kotlin version to a matching
# kctfork release (see the `kctforkForKotlin` block). For versions without a
# kctfork release (e.g. 2.3.21-RC2, 2.4.0-Beta1) the closest compatible kctfork
# is used; load errors bubble up as test failures in the summary.
#
# Usage:
#   ./scripts/test-all.sh
#
# Exits 0 if every version passes, non-zero otherwise.

set -u

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

# Load versions from the SSOT file, stripping comments and blank lines.
# Portable to macOS bash 3.2 (which lacks `mapfile`).
VERSIONS=()
while IFS= read -r line; do
    VERSIONS+=("$line")
done < <(grep -vE '^[[:space:]]*(#|$)' scripts/supported-kotlin-versions.txt)

log_dir=".local/tmp"
mkdir -p "$log_dir"
summary_file="$log_dir/test-summary-$(date +%Y%m%d-%H%M%S).log"

declare -a results
declare -a reasons

echo "=== test-all: $(date) ==="
echo "Summary log: $summary_file"
echo

for version in "${VERSIONS[@]}"; do
    echo "────── :debuggable-compiler:test on Kotlin $version ──────"
    log_file="$log_dir/test-all-$version.log"
    ./gradlew :debuggable-compiler:test \
        "-Ptest.kotlin=$version" \
        --no-daemon \
        --rerun-tasks \
        > "$log_file" 2>&1
    status=$?
    if [[ $status -eq 0 ]]; then
        result="🟢 $version"
        reason="pass"
    else
        # Try to surface a useful reason from the log.
        reason=$(grep -m1 -E "FAILED|error:|Exception" "$log_file" | head -c 200 || echo "unknown error")
        result="🔴 $version"
    fi
    results+=("$result")
    reasons+=("$reason")
    echo "  -> $result"
    echo
done

{
    echo "=== Test-all Summary ==="
    echo "Date: $(date)"
    echo
    printf "%-15s %-8s %s\n" "Version" "Status" "Reason / first error"
    printf "%-15s %-8s %s\n" "-------" "------" "--------------------"
    for i in "${!VERSIONS[@]}"; do
        version="${VERSIONS[$i]}"
        result="${results[$i]}"
        reason="${reasons[$i]}"
        status_icon="${result:0:1}"
        printf "%-15s %-8s %s\n" "$version" "$status_icon" "$reason"
    done
    echo
    echo "Detailed logs: $log_dir/test-all-*.log"
} | tee -a "$summary_file"

fail_count=0
for r in "${results[@]}"; do
    [[ "${r:0:1}" != "🟢" ]] && fail_count=$((fail_count + 1))
done
exit $fail_count
