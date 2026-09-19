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

    // -----------------------------------------------------------------
    // جلسة su دائمة (تجريبية، غير مُجرَّبة على جهاز حقيقي بعد).
    // لا تُستبدل بها run() الأصلية إطلاقًا - تُستخدم فقط عبر runFast()،
    // وتسقط تلقائيًا لاستدعاء run(Mode.ROOT, ...) الأصلية المضمونة عند
    // أي فشل أو استثناء غير متوقع. قفل صارم يمنع تنفيذ أمرين في نفس
    // اللحظة عبر نفس الجلسة (منعًا لاختلاط مخرجات الأوامر ببعضها).
    // -----------------------------------------------------------------
    private val fastLock = Any()
    private var fastProcess: Process? = null
    private var fastWriter: java.io.BufferedWriter? = null
    private var fastReader: java.io.BufferedReader? = null

    private fun isAlive(p: Process): Boolean = try {
        p.exitValue(); false
    } catch (e: IllegalThreadStateException) {
        true
    } catch (e: Exception) {
        false
    }

    private fun closeFastSessionLocked() {
        try { fastWriter?.close() } catch (e: Exception) { }
        try { fastReader?.close() } catch (e: Exception) { }
        try { fastProcess?.destroy() } catch (e: Exception) { }
        fastWriter = null
        fastReader = null
        fastProcess = null
    }

    private fun ensureFastSessionLocked(): Boolean {
        val existing = fastProcess
        if (existing != null && isAlive(existing)) return true
        closeFastSessionLocked()
        return try {
            val p = ProcessBuilder("su").redirectErrorStream(true).start()
            fastProcess = p
            fastWriter = p.outputStream.bufferedWriter()
            fastReader = p.inputStream.bufferedReader()
            true
        } catch (e: Exception) {
            closeFastSessionLocked()
            false
        }
    }

    /**
     * مكافئ run(Mode.ROOT, command) لكن عبر جلسة su دائمة بدل فتح عملية
     * su جديدة كل مرة (أسرع بكثير). عند أي فشل أو استثناء تُغلق الجلسة
     * وتسقط تلقائيًا لاستدعاء run(Mode.ROOT, command, timeoutMs) الأصلية.
     */
    fun runFast(command: String, timeoutMs: Long = 30_000): Result {
        synchronized(fastLock) {
            return try {
                if (!ensureFastSessionLocked()) return run(Mode.ROOT, command, timeoutMs)
                val writer = fastWriter
                val reader = fastReader
                if (writer == null || reader == null) return run(Mode.ROOT, command, timeoutMs)

                val marker = "L2SD_DONE_" + System.nanoTime()
                writer.write(command)
                writer.newLine()
                writer.write("echo ${marker}_\$?")
                writer.newLine()
                writer.flush()

                val sb = StringBuilder()
                var exitCode = -1
                var done = false
                val readerThread = Thread {
                    try {
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.startsWith(marker)) {
                                exitCode = line.removePrefix(marker + "_").trim().toIntOrNull() ?: -1
                                done = true
                                break
                            }
                            sb.appendLine(line)
                        }
                    } catch (e: Exception) { }
                }
                readerThread.start()
                readerThread.join(timeoutMs)
                if (readerThread.isAlive || !done) {
                    readerThread.interrupt()
                    closeFastSessionLocked()
                    return run(Mode.ROOT, command, timeoutMs)
                }
                Result(exitCode == 0, sb.toString().trim())
            } catch (e: Exception) {
                closeFastSessionLocked()
                run(Mode.ROOT, command, timeoutMs)
            }
        }
    }
}
