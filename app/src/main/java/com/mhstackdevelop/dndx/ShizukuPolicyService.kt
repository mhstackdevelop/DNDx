package com.mhstackdevelop.dndx

import androidx.annotation.Keep
import java.io.IOException

@Keep
class ShizukuPolicyService : IShizukuDndService.Stub() {
    override fun grantDndPolicyAccess(): String {
        return try {
            val process = ProcessBuilder(
                "cmd",
                "notification",
                "allow_dnd",
                BuildConfig.APPLICATION_ID
            ).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()
            if (exitCode == 0) "OK" else output.ifBlank {
                "command exited with $exitCode"
            }
        } catch (exception: IOException) {
            "Could not run Android's notification command: ${exception.message ?: "I/O error"}"
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            "Android's notification command was interrupted."
        } catch (exception: SecurityException) {
            "Android denied the notification access command: " +
                (exception.message ?: "permission denied")
        }
    }

    override fun destroy() {
        System.exit(0)
    }
}
