package com.smartaodi.dshandroid.runtime

import android.os.Process

data class RuntimeActionResult(
    val success: Boolean,
    val message: String,
)

class RuntimeController(
    private val shell: ShellExecutor = RootShell(timeoutSeconds = 30),
    private val appUid: Int = Process.myUid(),
) {
    suspend fun start(): RuntimeActionResult = result(shell.execute(startCommand(appUid)), "Host 已启动")

    suspend fun stop(): RuntimeActionResult = result(shell.execute(stopCommand(appUid)), "Host 已停止")

    private fun result(shellResult: ShellResult, successMessage: String): RuntimeActionResult {
        val status = parse(shellResult.output)["status"]
        val success = shellResult.exitCode == 0 && status in SUCCESS_STATUSES
        val detail = parse(shellResult.output)["detail"]
            ?: shellResult.output.lineSequence().lastOrNull { it.isNotBlank() }
        return RuntimeActionResult(
            success = success,
            message = if (success) successMessage else detail ?: "运行环境操作失败（${shellResult.exitCode}）",
        )
    }

    companion object {
        private const val ROOT = RuntimeProbe.RUNTIME_PATH

        internal fun startCommand(appUid: Int): String {
            require(appUid > 0) { "appUid must be positive" }
            return """
            if [ ! -x '$ROOT/runtime/control-android.sh' ]; then
              echo 'detail=Runtime controller is not installed'
              exit 1
            fi
            exec '$ROOT/runtime/control-android.sh' start $appUid
            """.trimIndent()
        }

        internal fun stopCommand(appUid: Int): String {
            require(appUid > 0) { "appUid must be positive" }
            return """
            if [ ! -x '$ROOT/runtime/control-android.sh' ]; then
              echo 'detail=Runtime controller is not installed'
              exit 1
            fi
            exec '$ROOT/runtime/control-android.sh' stop $appUid
            """.trimIndent()
        }

        private val SUCCESS_STATUSES = setOf("started", "stopped", "already-running", "already-stopped")

        internal fun parse(output: String): Map<String, String> = output
            .lineSequence()
            .mapNotNull { line ->
                val index = line.indexOf('=')
                if (index <= 0) null else line.take(index).trim() to line.drop(index + 1).trim()
            }
            .toMap()
    }
}
