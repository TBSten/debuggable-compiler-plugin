#!/usr/bin/env bash
#
# smoke-test-all.sh — run smoke-test.sh for every Kotlin version in the support matrix
#                     and print a summary table of pass/fail results.
#
# Usage:
#   ./scripts/smoke-test-all.sh
#
# Reads matrix from scripts/supported-kotlin-versions.txt. Exits 0 if all pass.

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
summary_file="$log_dir/smoke-summary-$(date +%Y%m%d-%H%M%S).log"

declare -a results
declare -a reasons

echo "=== smoke-test-all: $(date) ==="
echo "Summary log: $summary_file"
echo

for version in "${VERSIONS[@]}"; do
    echo "────── Testing Kotlin $version ──────"
    ./scripts/smoke-test.sh "$version" > "$log_dir/smoke-all-$version.log" 2>&1
    status=$?
    if [[ $status -eq 0 ]]; then
        result="🟢 $version"
        reason="pass"
    else
        # try to extract a useful error line
        reason=$(grep -m1 -E "FAIL:|error:|Exception|Caused by:" "$log_dir/smoke-all-$version.log" | head -c 200 || echo "unknown error")
        result="🔴 $version"
    fi
    results+=("$result")
    reasons+=("$reason")
    echo "  -> $result"
    echo
done

{
    echo "=== Smoke Test Summary ==="
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
    echo "Detailed logs: $log_dir/smoke-all-*.log"
} | tee -a "$summary_file"

# Exit non-zero if any failure
fail_count=0
for r in "${results[@]}"; do
    [[ "${r:0:1}" != "🟢" ]] && fail_count=$((fail_count + 1))
done
exit $fail_count
