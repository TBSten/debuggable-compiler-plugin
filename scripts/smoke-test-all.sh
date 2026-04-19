#!/usr/bin/env bash
#
# smoke-test-all.sh — run smoke-test.sh for every Kotlin version in the support
#                     matrix and print a summary table of pass/fail results.
#
# Usage:
#   ./scripts/smoke-test-all.sh            # parallel (default)
#   ./scripts/smoke-test-all.sh --serial   # one version at a time (easier to debug)
#   ./scripts/smoke-test-all.sh --parallel N
#   DEBUGGABLE_PARALLEL=N ./scripts/smoke-test-all.sh
#
# Reads matrix from scripts/supported-kotlin-versions.txt. Exits 0 if all pass.
#
# Parallel mode design:
#   1. publishToMavenLocal once upfront (otherwise N workers would race on ~/.m2/)
#   2. rsync integration-test/cmp/ → .local/tmp/cmp-<version>/ per worker so
#      each has its own build/ and never sees another worker's class files
#   3. xargs -P N runs SMOKE_SKIP_PUBLISH=1 smoke-test.sh per version
#
# Default parallelism: min(nproc/2, 4) — enough to saturate a laptop without
# running out of heap (kotlinc needs ~2GB each).

set -u

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

# ───── Parse args ─────
parallel=""
while [[ $# -gt 0 ]]; do
    case "$1" in
        --serial) parallel=1; shift ;;
        --parallel) parallel="$2"; shift 2 ;;
        --parallel=*) parallel="${1#*=}"; shift ;;
        -h|--help)
            sed -n '3,15p' "$0"
            exit 0
            ;;
        *)
            echo "Unknown arg: $1" >&2
            exit 2
            ;;
    esac
done

if [[ -z "$parallel" ]]; then
    parallel="${DEBUGGABLE_PARALLEL:-}"
fi
if [[ -z "$parallel" ]]; then
    cpus=$(getconf _NPROCESSORS_ONLN 2>/dev/null || echo 4)
    parallel=$(( cpus / 2 ))
    [[ $parallel -lt 1 ]] && parallel=1
    [[ $parallel -gt 4 ]] && parallel=4
fi

# ───── Load versions ─────
VERSIONS=()
while IFS= read -r line; do
    VERSIONS+=("$line")
done < <(grep -vE '^[[:space:]]*(#|$)' scripts/supported-kotlin-versions.txt)

log_dir=".local/tmp"
mkdir -p "$log_dir"
summary_file="$log_dir/smoke-summary-$(date +%Y%m%d-%H%M%S).log"
status_dir="$log_dir/smoke-status-$$"
mkdir -p "$status_dir"

echo "=== smoke-test-all: $(date) ==="
echo "Parallelism: $parallel"
echo "Summary log: $summary_file"
echo

# ───── Shared publish once ─────
echo "── Publishing plugin artifacts to ~/.m2/ (shared across workers)"
publish_log="$log_dir/smoke-all-publish.log"
if ! ./gradlew publishToMavenLocal --no-daemon > "$publish_log" 2>&1; then
    echo "FAIL: publishToMavenLocal failed — see $publish_log" >&2
    tail -50 "$publish_log" >&2
    exit 1
fi
echo "  ok."
echo

# ───── Worker ─────
# Invoked per version via xargs. Uses a per-version cmp workdir so parallel
# builds don't trash each other's `build/`.
run_one() {
    local version="$1"
    local workdir=".local/tmp/cmp-$version"
    local kmp_workdir=".local/tmp/kmp-smoke-$version"
    local log_file="$log_dir/smoke-all-$version.log"

    # Preserve the `.gradle/` / `build/` between reruns of the same version so
    # incremental builds stay fast, but make sure the source tree is synced
    # from the canonical integration-test directories before each run.
    mkdir -p "$workdir" "$kmp_workdir"
    rsync -a --delete \
        --exclude 'build/' --exclude '.gradle/' --exclude '.kotlin/' \
        integration-test/cmp/ "$workdir/"
    rsync -a --delete \
        --exclude 'build/' --exclude '.gradle/' --exclude '.kotlin/' \
        integration-test/kmp-smoke/ "$kmp_workdir/"

    # smoke-test.sh's step 4 builds `integration-test/kmp-smoke/` — we need each
    # parallel worker to target its own copy. Swap the path via a symlink so the
    # canonical directory points at this worker's rsynced copy for the duration
    # of the step. (Cheap; only the symlink is flipped per worker.)
    # Simpler alternative: patch the script to accept a kmp workdir. Keeping
    # it simple: point a `SMOKE_KMP_WORKDIR` env var at the per-worker copy.
    SMOKE_SKIP_PUBLISH=1 SMOKE_KMP_WORKDIR="$kmp_workdir" \
        ./scripts/smoke-test.sh "$version" "$workdir" \
        > "$log_file" 2>&1
    local rc=$?
    echo "$rc" > "$status_dir/$version.rc"
    if [[ $rc -eq 0 ]]; then
        echo "  🟢 $version"
    else
        echo "  🔴 $version (see $log_file)"
    fi
    return $rc
}
export -f run_one
export log_dir status_dir

# ───── Run ─────
if [[ $parallel -eq 1 ]]; then
    echo "── Running ${#VERSIONS[@]} versions serially"
    for v in "${VERSIONS[@]}"; do
        run_one "$v"
    done
else
    echo "── Running ${#VERSIONS[@]} versions with -P $parallel"
    printf '%s\n' "${VERSIONS[@]}" | xargs -n 1 -P "$parallel" -I{} \
        bash -c 'run_one "$@"' _ {}
fi
echo

# ───── Collect results ─────
declare -a results
declare -a reasons
for version in "${VERSIONS[@]}"; do
    rc_file="$status_dir/$version.rc"
    rc=1
    [[ -f "$rc_file" ]] && rc=$(cat "$rc_file")
    if [[ "$rc" == "0" ]]; then
        results+=("🟢 $version")
        reasons+=("pass")
    else
        reason=$(grep -m1 -E "FAIL:|error:|Exception|Caused by:" \
            "$log_dir/smoke-all-$version.log" 2>/dev/null | head -c 200)
        [[ -z "$reason" ]] && reason="unknown error (rc=$rc)"
        results+=("🔴 $version")
        reasons+=("$reason")
    fi
done
rm -rf "$status_dir"

# ───── Summary ─────
{
    echo "=== Smoke Test Summary ==="
    echo "Date: $(date)"
    echo "Parallelism: $parallel"
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
