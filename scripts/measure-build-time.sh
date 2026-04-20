#!/usr/bin/env bash
#
# measure-build-time.sh — measure clean-build wall time for
#   integration-test/cmp with `debuggable.enabled=true` vs `enabled=false`,
#   using Gradle `--profile`. Saves reports under `.local/tmp/`.
#
# Related: task-417. Manual perf baseline only — NOT wired into CI.
#
# Usage:
#   ./scripts/measure-build-time.sh [kotlin-version] [cmp-workdir]
#
# Defaults: kotlin-version=2.3.20, cmp-workdir=integration-test/cmp.

set -euo pipefail

kotlin_version="${1:-2.3.20}"
cmp_workdir="${2:-integration-test/cmp}"

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

if [[ ! -d "$cmp_workdir" ]]; then
    echo "FAIL: cmp workdir not found: $cmp_workdir" >&2
    exit 1
fi

ts="$(date +%Y%m%d-%H%M%S)"
log_dir=".local/tmp"
mkdir -p "$log_dir"

echo "=== [measure-build-time] Kotlin $kotlin_version ==="

echo
echo "--- Step 1: publishToMavenLocal ---"
./gradlew publishToMavenLocal --no-daemon \
    > "$log_dir/measure-$ts.publish.log" 2>&1

run_variant() {
    local enabled="$1"
    local label="enabled-$enabled"
    local report_dir="$cmp_workdir/build/reports/profile"
    local out_dir="$log_dir/profile-$label-$ts"

    echo
    echo "--- Variant: $label ---"
    (
        cd "$cmp_workdir"
        ./gradlew clean \
            --no-daemon \
            > "$repo_root/$log_dir/measure-$ts.$label.clean.log" 2>&1
        ./gradlew assemble \
            "-Pintegration.kotlin=$kotlin_version" \
            "-Pdebuggable.enabled=$enabled" \
            --profile \
            --no-daemon \
            --rerun-tasks \
            > "$repo_root/$log_dir/measure-$ts.$label.build.log" 2>&1
    )
    mkdir -p "$out_dir"
    if [[ -d "$report_dir" ]]; then
        cp -R "$report_dir/." "$out_dir/"
        echo "Profile report: $out_dir"
    else
        echo "WARN: no profile report directory found at $report_dir"
    fi
}

run_variant true
run_variant false

echo
echo "=== [measure-build-time] DONE ==="
echo "Compare the two HTML reports under $log_dir/profile-enabled-*-$ts/"
