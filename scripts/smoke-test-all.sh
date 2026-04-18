#!/usr/bin/env bash
#
# smoke-test-all.sh — run smoke-test.sh for every Kotlin version in the support matrix
#                     and print a summary table of pass/fail results.
#
# Usage:
#   ./scripts/smoke-test-all.sh
#
# Reads matrix from the VERSIONS array below. Exits 0 if all pass, non-zero otherwise.

set -u

VERSIONS=(
    "2.3.20"
    "2.3.21-RC2"
    "2.4.0-Beta1"
    "2.3.10"
    "2.3.0"
    "2.2.21"
    "2.2.20"
    "2.2.10"
    "2.2.0"
    "2.1.21"
    "2.0.21"
)

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

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
