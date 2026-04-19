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
#   ./scripts/test-all.sh            # parallel (default)
#   ./scripts/test-all.sh --serial   # one version at a time
#   ./scripts/test-all.sh --parallel N
#   DEBUGGABLE_PARALLEL=N ./scripts/test-all.sh
#
# Exits 0 if every version passes, non-zero otherwise.
#
# Parallel-safety note: each worker runs gradle in a rsync'd copy of the repo
# under `.local/tmp/test-worker-<version>/` so `debuggable-compiler/build/`
# (which holds the test results) does not collide across versions. The shared
# `GRADLE_USER_HOME` is kept — its dependency cache is file-lock-coordinated
# across processes, and sharing it avoids re-downloading the compiler embeds.

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
            sed -n '3,22p' "$0"
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
summary_file="$log_dir/test-summary-$(date +%Y%m%d-%H%M%S).log"
status_dir="$log_dir/test-status-$$"
mkdir -p "$status_dir"

echo "=== test-all: $(date) ==="
echo "Parallelism: $parallel"
echo "Summary log: $summary_file"
echo

# ───── Worker ─────
# For parallel safety each version runs gradle in its own rsync'd copy of the
# repo. .git/build/.gradle/.local are excluded so the copy is small (~<10 MB).
run_one() {
    local version="$1"
    local worker_dir=".local/tmp/test-worker-$version"
    local log_file="$log_dir/test-all-$version.log"

    mkdir -p "$worker_dir"
    rsync -a --delete \
        --exclude '.git/' \
        --exclude '.local/' \
        --exclude 'build/' \
        --exclude '.gradle/' \
        --exclude '.kotlin/' \
        --exclude '.idea/' \
        --exclude 'node_modules/' \
        ./ "$worker_dir/"

    (
        cd "$worker_dir"
        ./gradlew :debuggable-compiler:test \
            "-Ptest.kotlin=$version" \
            --no-daemon \
            --rerun-tasks
    ) > "$log_file" 2>&1
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
        reason=$(grep -m1 -E "FAILED|error:|Exception" \
            "$log_dir/test-all-$version.log" 2>/dev/null | head -c 200)
        [[ -z "$reason" ]] && reason="unknown error (rc=$rc)"
        results+=("🔴 $version")
        reasons+=("$reason")
    fi
done
rm -rf "$status_dir"

# ───── Summary ─────
{
    echo "=== Test-all Summary ==="
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
    echo "Detailed logs: $log_dir/test-all-*.log"
} | tee -a "$summary_file"

fail_count=0
for r in "${results[@]}"; do
    [[ "${r:0:1}" != "🟢" ]] && fail_count=$((fail_count + 1))
done
exit $fail_count
