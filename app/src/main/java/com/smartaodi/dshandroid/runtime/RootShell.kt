package com.smartaodi.dshandroid.runtime

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

interface ShellExecutor {
    suspend fun execute(command: String): ShellResult
}

class RootShell(
    private val suBinary: String = "su",
    private val timeoutSeconds: Long = 15,
) : ShellExecutor {
    override suspend fun execute(command: String): ShellResult = withContext(Dispatchers.IO) {
        val process = try {
            ProcessBuilder(suBinary, "-c", command)
                .redirectErrorStream(true)
                .start()
        } catch (error: Exception) {
            return@withContext ShellResult(
                exitCode = -1,
                output = error.message ?: error.javaClass.simpleName,
            )
        }

        try {
            val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return@withContext ShellResult(
                    exitCode = -1,
                    output = "Root authorization request timed out",
                    timedOut = true,
                )
            }
            ShellResult(
                exitCode = process.exitValue(),
                output = process.inputStream.bufferedReader().use { it.readText() }.trim(),
            )
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
    }
}
