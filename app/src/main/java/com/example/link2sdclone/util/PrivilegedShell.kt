package com.example.link2sdclone.util

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

/** ينفّذ أوامر بصلاحية عليا: Root أولًا ثم Shizuku. كل الدوال تُستدعى خارج الخيط الرئيسي. */
object PrivilegedShell {

    enum class Mode { ROOT, SHIZUKU, SHIZUKU_NEEDS_PERMISSION, NONE }

    data class Result(val ok: Boolean, val output: String)

    fun detect(): Mode {
        if (rootAvailable()) return Mode.ROOT
        return try {
            when {
                !Shizuku.pingBinder() || Shizuku.isPreV11() -> Mode.NONE
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> Mode.SHIZUKU
                else -> Mode.SHIZUKU_NEEDS_PERMISSION
            }
        } catch (e: Throwable) {
            Mode.NONE
        }
    }

    fun requestShizuku(code: Int) {
        try { Shizuku.requestPermission(code) } catch (e: Throwable) { }
    }

    fun run(mode: Mode, command: String, timeoutMs: Long = 30_000): Result {
        return try {
            val p = when (mode) {
                Mode.ROOT -> ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
                Mode.SHIZUKU -> shizukuProcess(command)
                else -> return Result(false, "no access")
            }
            collect(p, timeoutMs)
        } catch (e: Exception) {
            Result(false, e.cause?.message ?: e.message ?: e.toString())
        }
    }

    /** null = لم يُفحص بعد. يُحدَّث من rootAvailable، ويُقرأ من الخيط الرئيسي دون حجب. */
    @Volatile
    var rootKnown: Boolean? = null
        private set

    private fun rootAvailable(): Boolean {
        val ok = try {
            val p = ProcessBuilder("su", "-c", "id").redirectErrorStream(true).start()
            collect(p, 10_000).output.contains("uid=0")
        } catch (e: Exception) {
            false
        }
        rootKnown = ok
        return ok
    }

    /** يفحص Root في خيط خلفي ويحفظ النتيجة. */
    fun warmUp() {
        Thread { rootAvailable() }.start()
    }

    // Shizuku.newProcess صار private في الإصدار 13، فنستدعيه بالانعكاس.
    private fun shizukuProcess(command: String): Process {
        val m = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        )
        m.isAccessible = true
        return m.invoke(null, arrayOf("sh", "-c", "{ $command; } 2>&1"), null, null) as Process
    }

    private fun collect(p: Process, timeoutMs: Long): Result {
        val sb = StringBuilder()
        val reader = Thread {
            try {
                p.inputStream.bufferedReader().forEachLine { sb.appendLine(it) }
            } catch (e: Exception) { }
        }
        reader.start()
        reader.join(timeoutMs)
        if (reader.isAlive) {
            p.destroy()
            return Result(false, "انتهت المهلة")
        }
        val code = try { p.waitFor() } catch (e: Exception) { -1 }
        return Result(code == 0, sb.toString().trim())
    }
}
