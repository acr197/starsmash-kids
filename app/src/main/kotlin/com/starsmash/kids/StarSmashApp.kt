package com.starsmash.kids

import android.app.Application
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Application subclass for StarSmash Kids.
 *
 * Installs a global uncaught-exception handler that writes the crash's stack
 * trace to `cacheDir/last_crash.txt` before letting the default handler do its
 * job. MainActivity reads that file on next launch and surfaces it to the
 * user, so a crash on one launch becomes a visible diagnostic on the next —
 * this is the only way to see what went wrong on a sideloaded APK without
 * adb/logcat access.
 */
class StarSmashApp : Application() {

    override fun onCreate() {
        super.onCreate()

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val body = buildString {
                    append("Thread: ").append(thread.name).append('\n')
                    append("Time: ").append(System.currentTimeMillis()).append('\n')
                    append(sw.toString())
                }
                File(cacheDir, LAST_CRASH_FILE).writeText(body)
                Log.e("StarSmashApp", "Uncaught exception written to ${LAST_CRASH_FILE}", throwable)
            } catch (_: Throwable) {
                // Never let the crash reporter itself crash the process.
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        const val LAST_CRASH_FILE = "last_crash.txt"
    }
}
