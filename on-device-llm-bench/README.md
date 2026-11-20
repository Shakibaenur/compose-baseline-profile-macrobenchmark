# On-device LLM benchmark (MediaPipe LLM Inference API, Android)

A small Android harness that loads a quantized Gemma `.task` model with the
MediaPipe LLM Inference API and measures, per backend (CPU vs GPU):

- **Load time** and app PSS before/after loading
- **Time-to-first-token (TTFT)**: call → first streamed chunk (includes prefill)
- **Decode speed (tok/s)**: output tokens ÷ time from first to last chunk.
  Token counts come from `sizeInTokens()` on the full output, so this is approximate.
- **Peak memory**: max total PSS of the app process, sampled every 50 ms during generation

Warm-up is excluded from the results. The first GPU call compiles shaders, which would inflate TTFT.

> **Status: harness built, numbers not yet measured.** The results table below is empty
> until the benchmark runs on a device with a model file. See *Results*.

## Important caveats

- **MediaPipe LLM Inference API is in maintenance-only mode.** Google recommends
  LiteRT-LM for new work. This project uses MediaPipe because it was the planned route.
  A LiteRT-LM port is the natural follow-up.
- MediaPipe's docs say the API is tuned for high-end phones (e.g. Pixel 8, Galaxy S23+)
  and does not reliably run on emulators. Numbers from an emulator are not valid for the
  GPU path. Use a physical device for the results.

## Layout

```
app/src/main/java/dev/llmbench/Bench.kt        benchmark loop + metrics
app/src/main/java/dev/llmbench/MainActivity.kt  UI button + unattended (adb) entry point
scripts/run_bench.sh                           push model, run both backends, pull JSON
docs/screenshots/                              app screenshots
```

## Build

Requires Android SDK (platform 36), JDK 17. The debug build works with AGP 8.13.0,
Kotlin 2.2.20, Gradle 9.3.1, and `tasks-genai:0.10.35`.

```bash
./gradlew :app:assembleDebug
```

Build output goes to `~/Library/Caches/llm-bench/` (set in `build.gradle.kts`).
This is needed when the project sits on an exFAT/external volume, which writes `._*`
files that break D8.

## Model

Download a Gemma 3 1B `.task` file yourself (the Hugging Face repo requires accepting the
Gemma license while logged in):
<https://huggingface.co/litert-community/Gemma3-1B-IT>

Then push it to the device:

```bash
adb shell mkdir -p /data/local/tmp/llm
adb push gemma3-1b-it-int4.task /data/local/tmp/llm/model.task
```

## Run

```bash
scripts/run_bench.sh /path/to/model.task
```

The script installs the APK, runs CPU and GPU, waits for `BENCH_DONE` in logcat, and pulls
the results JSON. It writes to `results/`.

## Results

| Device | SoC | Android | Backend | Load (ms) | Median TTFT (ms) | Median decode (tok/s) | Peak PSS (MB) |
|---|---|---|---|---|---|---|---|
| _TBD_ | _TBD_ | _TBD_ | CPU | | | | |
| _TBD_ | _TBD_ | _TBD_ | GPU | | | | |

Model: _TBD_ (quantization _TBD_), max_tokens 512, 3 prompts × 3 runs per backend.

## Interpretation

_To be written after the measurements. Report what was observed, including if GPU was not
used or failed to initialise._

## Screenshots

_Pending: the emulator's display showed a blank frame while the view hierarchy was correct
(button and status text present), so no screenshot is included yet._
