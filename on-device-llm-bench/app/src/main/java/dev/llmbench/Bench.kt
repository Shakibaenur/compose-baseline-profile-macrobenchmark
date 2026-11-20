package dev.llmbench

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Debug
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

const val TAG = "LLMBENCH"

val PROMPTS = listOf(
    "Explain what a hash map is in two sentences.",
    "Write a short haiku about waiting for a build to finish.",
    "List five practical tips for reducing Android app startup time.",
)

class BenchConfig(
    val modelPath: String,
    val backends: List<String>, // "CPU", "GPU"
    val runsPerPrompt: Int,
    val maxTokens: Int,
)

/**
 * Runs each backend in turn: load -> warm-up -> prompts x runs, streaming.
 * Metrics per generation:
 *  - ttft_ms:  call -> first streamed chunk (time-to-first-token, includes prefill)
 *  - decode_tok_s: output tokens / (last chunk - first chunk). Tokens are counted by
 *    re-tokenising the full output with sizeInTokens(), so it's an approximation.
 *  - peak_pss_mb: max total PSS of the app process sampled every 50 ms during generation.
 */
class Bench(
    private val ctx: Context,
    private val cfg: BenchConfig,
    private val progress: (String) -> Unit,
) {

    fun run(): JSONObject {
        val root = JSONObject()
        root.put("started_at_ms", System.currentTimeMillis())
        root.put("device", deviceInfo())
        root.put("model", modelInfo())
        root.put("max_tokens", cfg.maxTokens)
        root.put("runs_per_prompt", cfg.runsPerPrompt)
        root.put("prompts", JSONArray(PROMPTS))
        val configs = JSONArray()
        for (b in cfg.backends) {
            configs.put(runBackend(b))
        }
        root.put("configs", configs)
        root.put("finished_at_ms", System.currentTimeMillis())
        return root
    }

    private fun runBackend(backend: String): JSONObject {
        val out = JSONObject().put("backend", backend)
        progress("[$backend] loading model…")
        val pssBefore = pssMb()
        val t0 = System.nanoTime()
        val llm: LlmInference
        try {
            val opts = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(cfg.modelPath)
                .setMaxTokens(cfg.maxTokens)
                .setPreferredBackend(
                    if (backend == "GPU") LlmInference.Backend.GPU else LlmInference.Backend.CPU
                )
                .build()
            llm = LlmInference.createFromOptions(ctx, opts)
        } catch (t: Throwable) {
            Log.e(TAG, "[$backend] load failed", t)
            progress("[$backend] load FAILED: ${t.javaClass.simpleName}: ${t.message}")
            return out.put("error", "load: ${t.javaClass.simpleName}: ${t.message}")
        }
        val loadMs = (System.nanoTime() - t0) / 1e6
        val pssLoaded = pssMb()
        out.put("load_ms", round1(loadMs))
        out.put("pss_before_load_mb", round1(pssBefore))
        out.put("pss_after_load_mb", round1(pssLoaded))
        Log.i(TAG, "[$backend] loaded in ${round1(loadMs)} ms, pss ${round1(pssLoaded)} MB")

        val runs = JSONArray()
        try {
            // Warm-up: not recorded. First call on GPU compiles shaders, which would skew TTFT.
            progress("[$backend] warm-up…")
            generate(llm, "Say hello in one word.", pssLoaded)

            var n = 0
            val total = PROMPTS.size * cfg.runsPerPrompt
            for ((pi, prompt) in PROMPTS.withIndex()) {
                for (r in 1..cfg.runsPerPrompt) {
                    n++
                    progress("[$backend] run $n/$total (prompt ${pi + 1}, rep $r)")
                    val g = generate(llm, prompt, pssLoaded)
                    val obj = JSONObject()
                        .put("prompt_index", pi)
                        .put("rep", r)
                        .put("ttft_ms", round1(g.ttftMs))
                        .put("decode_ms", round1(g.decodeMs))
                        .put("output_tokens", g.tokens)
                        .put("decode_tok_s", round2(g.decodeTokPerSec))
                        .put("total_ms", round1(g.totalMs))
                        .put("peak_pss_mb", round1(g.peakPssMb))
                        .put("output_chars", g.text.length)
                    runs.put(obj)
                    Log.i(
                        TAG,
                        "[$backend] p$pi r$r ttft=${round1(g.ttftMs)}ms decode=${round2(g.decodeTokPerSec)}tok/s " +
                            "tokens=${g.tokens} peakPss=${round1(g.peakPssMb)}MB"
                    )
                }
            }
            out.put("runs", runs)
            out.put("summary", summarise(runs))
        } catch (t: Throwable) {
            Log.e(TAG, "[$backend] generation failed", t)
            out.put("runs", runs)
            out.put("error", "generate: ${t.javaClass.simpleName}: ${t.message}")
        } finally {
            try {
                llm.close()
            } catch (_: Throwable) {
            }
        }
        return out
    }

    private class Gen(
        val ttftMs: Double,
        val decodeMs: Double,
        val totalMs: Double,
        val tokens: Int,
        val decodeTokPerSec: Double,
        val peakPssMb: Double,
        val text: String,
    )

    private fun generate(llm: LlmInference, prompt: String, basePssMb: Double): Gen {
        val peakKb = AtomicLong(0)
        val sampling = AtomicBoolean(true)
        val sampler = Thread {
            val mi = Debug.MemoryInfo()
            while (sampling.get()) {
                Debug.getMemoryInfo(mi)
                peakKb.accumulateAndGet(mi.totalPss.toLong()) { a, b -> maxOf(a, b) }
                try {
                    Thread.sleep(50)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }
        sampler.start()

        val text = StringBuilder()
        val start = System.nanoTime()
        val firstAt = AtomicLong(0)
        val lastAt = AtomicLong(0)
        try {
            val future = llm.generateResponseAsync(prompt) { partial, done ->
                firstAt.compareAndSet(0L, System.nanoTime())
                synchronized(text) { text.append(partial) }
                if (done) lastAt.set(System.nanoTime())
            }
            future.get(240, TimeUnit.SECONDS)
        } finally {
            sampling.set(false)
            sampler.join(1000)
        }
        val end = System.nanoTime()
        if (lastAt.get() == 0L) lastAt.set(end)
        val first = if (firstAt.get() == 0L) end else firstAt.get()

        val fullText = synchronized(text) { text.toString() }
        val tokens = try {
            llm.sizeInTokens(fullText)
        } catch (_: Throwable) {
            0
        }
        val decodeSec = (lastAt.get() - first) / 1e9
        val decodeTokPerSec = if (decodeSec > 0 && tokens > 0) tokens / decodeSec else 0.0
        return Gen(
            ttftMs = (first - start) / 1e6,
            decodeMs = (lastAt.get() - first) / 1e6,
            totalMs = (end - start) / 1e6,
            tokens = tokens,
            decodeTokPerSec = decodeTokPerSec,
            peakPssMb = peakKb.get() / 1024.0,
            text = fullText,
        )
    }

    private fun summarise(runs: JSONArray): JSONObject {
        val ttft = mutableListOf<Double>()
        val tps = mutableListOf<Double>()
        var peak = 0.0
        for (i in 0 until runs.length()) {
            val r = runs.getJSONObject(i)
            ttft += r.getDouble("ttft_ms")
            tps += r.getDouble("decode_tok_s")
            peak = maxOf(peak, r.getDouble("peak_pss_mb"))
        }
        return JSONObject()
            .put("n", runs.length())
            .put("median_ttft_ms", round1(median(ttft)))
            .put("median_decode_tok_s", round2(median(tps)))
            .put("max_peak_pss_mb", round1(peak))
    }

    private fun deviceInfo(): JSONObject {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val soc = if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL else Build.HARDWARE
        return JSONObject()
            .put("manufacturer", Build.MANUFACTURER)
            .put("model", Build.MODEL)
            .put("soc", soc)
            .put("hardware", Build.HARDWARE)
            .put("android", Build.VERSION.RELEASE)
            .put("sdk", Build.VERSION.SDK_INT)
            .put("ram_total_mb", mi.totalMem / (1024 * 1024))
            .put("is_emulator", Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk_gphone"))
    }

    private fun modelInfo(): JSONObject {
        val f = File(cfg.modelPath)
        return JSONObject()
            .put("file", f.name)
            .put("size_mb", round1(f.length() / (1024.0 * 1024.0)))
    }

    private fun pssMb(): Double {
        val mi = Debug.MemoryInfo()
        Debug.getMemoryInfo(mi)
        return mi.totalPss / 1024.0
    }

    private fun median(xs: List<Double>): Double {
        if (xs.isEmpty()) return 0.0
        val s = xs.sorted()
        val m = s.size / 2
        return if (s.size % 2 == 0) (s[m - 1] + s[m]) / 2 else s[m]
    }

    private fun round1(x: Double) = Math.round(x * 10) / 10.0
    private fun round2(x: Double) = Math.round(x * 100) / 100.0
}
