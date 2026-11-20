package dev.llmbench

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Minimal harness. Works from the UI button or unattended via adb:
 *   adb shell am start -n dev.llmbench/.MainActivity \
 *       -e auto true -e backends CPU,GPU -e runs 3 -e max_tokens 512 -e model /data/local/tmp/llm/model.task
 * Results: <app external files>/results-<timestamp>.json, plus logcat tag LLMBENCH.
 * The script prints BENCH_DONE in logcat when finished.
 */
class MainActivity : Activity() {

    private lateinit var log: TextView
    private lateinit var runButton: Button
    private var running = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()

        runButton = Button(this).apply { text = "Run benchmark" }
        log = TextView(this).apply {
            textSize = 13f
            typeface = android.graphics.Typeface.MONOSPACE
            text = "Idle. Model: $DEFAULT_MODEL"
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            addView(runButton, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(log)
        }
        setContentView(ScrollView(this).apply { addView(column) })

        runButton.setOnClickListener { startBench(configFromIntent()) }

        if (intent.getBooleanExtra("auto", false)) {
            startBench(configFromIntent())
        }
    }

    private fun configFromIntent(): BenchConfig {
        val model = intent.getStringExtra("model") ?: DEFAULT_MODEL
        val backends = (intent.getStringExtra("backends") ?: "CPU,GPU")
            .split(",").map { it.trim().uppercase() }.filter { it.isNotEmpty() }
        return BenchConfig(
            modelPath = model,
            backends = backends,
            runsPerPrompt = intent.getIntExtra("runs", 3),
            maxTokens = intent.getIntExtra("max_tokens", 512),
        )
    }

    private fun startBench(cfg: BenchConfig) {
        if (running) return
        if (!File(cfg.modelPath).exists()) {
            append("Model not found: ${cfg.modelPath}")
            Log.e(TAG, "model not found: ${cfg.modelPath}")
            Log.i(TAG, "BENCH_DONE error=model_missing")
            return
        }
        running = true
        runButton.isEnabled = false
        append("Starting: backends=${cfg.backends} runs=${cfg.runsPerPrompt} max_tokens=${cfg.maxTokens}")
        Thread {
            try {
                val result = Bench(applicationContext, cfg) { msg -> append(msg) }.run()
                val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                val out = File(getExternalFilesDir(null), "results-$stamp.json")
                out.writeText(result.toString(2))
                Log.i(TAG, "RESULT_FILE ${out.absolutePath}")
                append("Saved: ${out.absolutePath}")
                printSummary(result.toString())
                Log.i(TAG, "BENCH_DONE ok")
            } catch (t: Throwable) {
                Log.e(TAG, "benchmark crashed", t)
                append("CRASH: ${t.message}")
                Log.i(TAG, "BENCH_DONE error=crash")
            } finally {
                runOnUiThread {
                    running = false
                    runButton.isEnabled = true
                }
            }
        }.start()
    }

    private fun printSummary(json: String) {
        val o = org.json.JSONObject(json)
        val configs = o.getJSONArray("configs")
        for (i in 0 until configs.length()) {
            val c = configs.getJSONObject(i)
            val backend = c.getString("backend")
            if (c.has("error")) {
                append("[$backend] ERROR ${c.getString("error")}")
                continue
            }
            val s = c.getJSONObject("summary")
            append(
                "[$backend] load=${c.getDouble("load_ms")}ms " +
                    "median TTFT=${s.getDouble("median_ttft_ms")}ms " +
                    "median decode=${s.getDouble("median_decode_tok_s")} tok/s " +
                    "peak PSS=${s.getDouble("max_peak_pss_mb")}MB"
            )
        }
    }

    private fun append(msg: String) {
        runOnUiThread { log.append("\n$msg") }
    }

    companion object {
        const val DEFAULT_MODEL = "/data/local/tmp/llm/model.task"
    }
}
