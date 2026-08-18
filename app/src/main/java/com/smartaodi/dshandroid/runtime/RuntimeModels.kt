package com.smartaodi.dshandroid.runtime

data class RuntimeProbe(
    val rootAvailable: Boolean,
    val rootDetail: String,
    val rootProvider: String,
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val apiLevel: Int,
    val deviceAbi: String,
    val selinuxMode: String,
    val runtimeInstalled: Boolean,
    val nodeInstalled: Boolean,
    val harnessInstalled: Boolean,
    val androidPluginInstalled: Boolean,
    val dshVersion: String?,
    val hostRunning: Boolean,
    val runtimePath: String = RUNTIME_PATH,
) {
    companion object {
        const val RUNTIME_PATH = "/data/adb/dsh-android"

        fun loading() = RuntimeProbe(
            rootAvailable = false,
            rootDetail = "正在请求 Root…",
            rootProvider = "检测中",
            manufacturer = BuildInfo.UNKNOWN,
            model = BuildInfo.UNKNOWN,
            androidVersion = BuildInfo.UNKNOWN,
            apiLevel = 0,
            deviceAbi = "检测中",
            selinuxMode = "检测中",
            runtimeInstalled = false,
            nodeInstalled = false,
            harnessInstalled = false,
            androidPluginInstalled = false,
            dshVersion = null,
            hostRunning = false,
        )
    }
}

private object BuildInfo {
    const val UNKNOWN = "检测中"
}

data class ShellResult(
    val exitCode: Int,
    val output: String,
    val timedOut: Boolean = false,
)
