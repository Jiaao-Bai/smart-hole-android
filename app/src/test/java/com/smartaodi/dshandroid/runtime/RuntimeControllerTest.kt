package com.smartaodi.dshandroid.runtime

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeControllerTest {
    @Test
    fun startAcceptsAlreadyRunning() = runBlocking {
        val controller = RuntimeController(FakeShell(ShellResult(0, "status=already-running")), appUid = 12345)

        assertTrue(controller.start().success)
    }

    @Test
    fun stopRejectsUnrecognizedProcess() = runBlocking {
        val controller = RuntimeController(
            FakeShell(ShellResult(1, "detail=Refusing to stop an unrecognized process")),
            appUid = 12345,
        )

        val result = controller.stop()
        assertFalse(result.success)
        assertTrue(result.message.contains("unrecognized"))
    }

    @Test
    fun commandsUseCanonicalRuntimeLayout() {
        val start = RuntimeController.startCommand(12345)
        val stop = RuntimeController.stopCommand(12345)
        assertTrue(start.contains("/data/adb/dsh-android/runtime/control-android.sh"))
        assertTrue(start.contains("runtime/control-android.sh"))
        assertTrue(start.contains("start 12345"))
        assertTrue(stop.contains("stop 12345"))
    }

    private class FakeShell(private val result: ShellResult) : ShellExecutor {
        override suspend fun execute(command: String): ShellResult = result
    }
}
