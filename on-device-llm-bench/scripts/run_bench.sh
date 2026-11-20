#!/usr/bin/env bash
# Usage: scripts/run_bench.sh /path/to/model.task [runs] [max_tokens]
set -euo pipefail
MODEL_LOCAL="${1:?model .task path}"
RUNS="${2:-3}"
MAXTOK="${3:-512}"
ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
APK="$HOME/Library/Caches/llm-bench/app/outputs/apk/debug/app-debug.apk"
DEVICE_MODEL=/data/local/tmp/llm/model.task
OUT="$(cd "$(dirname "$0")/.." && pwd)/results"
mkdir -p "$OUT"

"$ADB" install -r "$APK" >/dev/null
"$ADB" shell mkdir -p /data/local/tmp/llm
"$ADB" push "$MODEL_LOCAL" "$DEVICE_MODEL" >/dev/null
"$ADB" logcat -c
"$ADB" shell am force-stop dev.llmbench
"$ADB" shell am start -n dev.llmbench/.MainActivity \
  -e auto true -e backends CPU,GPU -e runs "$RUNS" -e max_tokens "$MAXTOK" -e model "$DEVICE_MODEL" >/dev/null

echo "Waiting for BENCH_DONE..."
until "$ADB" logcat -d -s LLMBENCH:I | grep -q BENCH_DONE; do sleep 10; done
"$ADB" logcat -d -s LLMBENCH:I > "$OUT/logcat.txt"
REMOTE=$("$ADB" logcat -d -s LLMBENCH:I | grep -o 'RESULT_FILE .*' | tail -1 | cut -d' ' -f2-)
"$ADB" pull "$REMOTE" "$OUT/" 2>/dev/null || echo "Result file not found; see $OUT/logcat.txt"
echo "Done. Results in $OUT"
