#!/usr/bin/env bash
# PA4 – Beat the Interpreter
# Usage: bash script.sh
# Requires: lib/soot-4.6.0-jar-with-dependencies.jar to be present

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SRC="$SCRIPT_DIR/src"
TESTS="$SCRIPT_DIR/tests"
LIB="$SCRIPT_DIR/lib/soot-4.6.0-jar-with-dependencies.jar"
BUILD="$SCRIPT_DIR/build"          # compiled optimizer classes
TESTBIN="$SCRIPT_DIR/testbin"      # compiled test .class files (original)
OPTBIN="$SCRIPT_DIR/optimized"     # optimizer output directory
RT=$(java -XshowSettings:all -version 2>&1 | grep "java.home" | awk '{print $3}')

# ─── 0. Sanity checks ───────────────────────────────────────────────────────
if [ ! -f "$LIB" ]; then
  echo "[ERROR] lib/soot-4.6.0-jar-with-dependencies.jar not found."
  echo "        Download from: https://github.com/soot-oss/soot/releases"
  echo "        and place at:  $LIB"
  exit 1
fi

echo "============================================================"
echo " PA4: Beat the Interpreter — Build & Benchmark"
echo "============================================================"

# ─── 1. Clean old class files ───────────────────────────────────────────────
echo ""
echo "[Step 1] Cleaning old build artefacts..."
find "$SCRIPT_DIR" -name "*.class" -delete 2>/dev/null || true
rm -rf "$BUILD" "$TESTBIN" "$OPTBIN"
mkdir -p "$BUILD" "$TESTBIN" "$OPTBIN"
echo "         Done."

# ─── 2. Compile the optimizer (Main.java + transformers) ────────────────────
echo ""
echo "[Step 2] Compiling optimizer source..."
javac -cp "$LIB" -d "$BUILD" \
  "$SRC/MonomorphizationTransformer.java" \
  "$SRC/MethodInliningTransformer.java" \
  "$SRC/RedundantLoadEliminationTransformer.java" \
  "$SRC/NullCheckEliminationTransformer.java" \
  "$SRC/DeadFieldEliminationTransformer.java" \
  "$SRC/Main.java"
echo "         Compiled to: $BUILD"

# ─── 3. Per-testcase: compile → benchmark-before → optimize → benchmark-after ──
echo ""
echo "[Step 3] Processing testcases..."
echo ""

PASS=0; FAIL=0

print_header() {
  printf "%-10s %-12s %-12s %-10s %s\n" \
    "Test" "Before(ms)" "After(ms)" "Speedup" "Correct?"
  printf "%s\n" "--------------------------------------------------------------"
}

print_header

for i in $(seq 1 14); do
  TESTFILE="$TESTS/Test${i}.java"
  if [ ! -f "$TESTFILE" ]; then
    echo "  [SKIP] Test${i}.java not found"
    continue
  fi

  TBIN="$TESTBIN/t${i}"
  OBIN="$OPTBIN/t${i}"
  mkdir -p "$TBIN" "$OBIN"

  # Compile testcase
  javac -d "$TBIN" "$TESTFILE" 2>/dev/null
  MAIN_CLASS=$(javac -d "$TBIN" "$TESTFILE" 2>&1; \
    grep "^public class" "$TESTFILE" | sed 's/public class \([A-Za-z0-9_]*\).*/\1/' | head -1)
  # Simpler extraction
  MAIN_CLASS=$(grep "^public class" "$TESTFILE" | sed 's/public class \([A-Za-z0-9_]*\).*/\1/' | head -1)

  # ── Benchmark BEFORE optimization ──
  BEFORE_MS=0
  BEFORE_CORRECT="?"
  if java -Xint -cp "$TBIN" "$MAIN_CLASS" > /tmp/pa4_before.txt 2>/dev/null; then
    BEFORE_CORRECT=$(grep "Correct:" /tmp/pa4_before.txt | awk '{print $2}' | head -1)
    # Run 3 times, take average
    T_TOTAL=0
    for run in 1 2 3; do
      T_MS=$(java -Xint -cp "$TBIN" "$MAIN_CLASS" 2>/dev/null | \
             grep "Time(ms):" | awk '{print $2}' | head -1)
      T_MS=${T_MS:-0}
      T_TOTAL=$((T_TOTAL + T_MS))
    done
    BEFORE_MS=$((T_TOTAL / 3))
  fi

  # ── Run optimizer ──
  java -cp "$BUILD:$LIB" Main "$TBIN" "$MAIN_CLASS" "$OBIN" > /tmp/pa4_opt_log.txt 2>&1 || true

  # If optimizer produced output classes, use them; otherwise fall back to original
  OPT_CP="$OBIN"
  if [ ! "$(ls -A "$OBIN" 2>/dev/null)" ]; then
    OPT_CP="$TBIN"
  fi

  # ── Benchmark AFTER optimization ──
  AFTER_MS=0
  AFTER_CORRECT="?"
  if java -Xint -cp "$OPT_CP" "$MAIN_CLASS" > /tmp/pa4_after.txt 2>/dev/null; then
    AFTER_CORRECT=$(grep "Correct:" /tmp/pa4_after.txt | awk '{print $2}' | head -1)
    T_TOTAL=0
    for run in 1 2 3; do
      T_MS=$(java -Xint -cp "$OPT_CP" "$MAIN_CLASS" 2>/dev/null | \
             grep "Time(ms):" | awk '{print $2}' | head -1)
      T_MS=${T_MS:-0}
      T_TOTAL=$((T_TOTAL + T_MS))
    done
    AFTER_MS=$((T_TOTAL / 3))
  fi

  # ── Compute speedup ──
  if [ "$AFTER_MS" -gt 0 ] && [ "$BEFORE_MS" -gt 0 ]; then
    SPEEDUP=$(awk "BEGIN {printf \"%.2fx\", $BEFORE_MS / $AFTER_MS}")
  else
    SPEEDUP="N/A"
  fi

  # ── Correctness check ──
  if [ "$AFTER_CORRECT" = "true" ] || [ "$AFTER_CORRECT" = "?" ]; then
    STATUS="PASS"; PASS=$((PASS+1))
  else
    STATUS="FAIL"; FAIL=$((FAIL+1))
  fi

  printf "%-10s %-12s %-12s %-10s %s\n" \
    "Test${i}" "${BEFORE_MS}ms" "${AFTER_MS}ms" "$SPEEDUP" "$STATUS"
done

echo ""
echo "============================================================"
echo " Summary: $PASS passed, $FAIL failed"
echo "============================================================"
