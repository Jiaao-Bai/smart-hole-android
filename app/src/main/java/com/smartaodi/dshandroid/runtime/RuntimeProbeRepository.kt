package com.smartaodi.dshandroid.runtime

import android.os.Build

class RuntimeProbeRepository(
    private val rootShell: RootShell = RootShell(),
) {
    suspend fun probe(): RuntimeProbe {
        val shell = rootShell.execute(PROBE_COMMAND)
        val values = parseProbeOutput(shell.output)
        val rooted = shell.exitCode == 0 && values["uid"] == "0"
        val detail = when {
            shell.timedOut -> "Root 授权超时"
            rooted -> "已授权（uid 0）"
            shell.output.isNotBlank() -> shell.output.lineSequence().first()
            else -> "未获得 root"
        }

        return RuntimeProbe(
            rootAvailable = rooted,
            rootDetail = detail,
            rootProvider = values["root_provider"] ?: "未知 su provider",
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            androidVersion = Build.VERSION.RELEASE,
            apiLevel = Build.VERSION.SDK_INT,
            deviceAbi = Build.SUPPORTED_ABIS.joinToString(),
            selinuxMode = values["selinux"] ?: "未知",
            runtimeInstalled = values["runtime"] == "yes",
            nodeInstalled = values["node"] == "yes",
            harnessInstalled = values["harness"] == "yes",
            androidPluginInstalled = values["android_plugin"] == "yes",
            dshVersion = values["dsh_version"]?.takeIf { it.isNotBlank() },
            hostRunning = values["host"] == "yes",
        )
    }

    companion object {
        private val PROBE_COMMAND = """
            set +e
            printf 'uid=%s\n' "$(id -u 2>/dev/null)"
            printf 'selinux=%s\n' "$(getenforce 2>/dev/null)"
            if [ -x /data/adb/ap/bin/apd ] || command -v apd >/dev/null 2>&1; then
              echo 'root_provider=APatch'
            elif [ -x /data/adb/ksu/bin/ksud ] || command -v ksud >/dev/null 2>&1; then
              echo 'root_provider=KernelSU'
            elif command -v magisk >/dev/null 2>&1; then
              echo 'root_provider=Magisk'
            else
              echo 'root_provider=Generic su'
            fi
            [ -f '${RuntimeProbe.RUNTIME_PATH}/runtime/manifest.json' ] && echo 'runtime=yes' || echo 'runtime=no'
            [ -x '${RuntimeProbe.RUNTIME_PATH}/runtime/bin/node' ] && echo 'node=yes' || echo 'node=no'
            [ -f '${RuntimeProbe.RUNTIME_PATH}/runtime/node_modules/@deepseek-ai/dsh/package.json' ] && echo 'harness=yes' || echo 'harness=no'
            [ -f '${RuntimeProbe.RUNTIME_PATH}/runtime/node_modules/dsh-plugin-android/package.json' ] && echo 'android_plugin=yes' || echo 'android_plugin=no'
            runtime_manifest='${RuntimeProbe.RUNTIME_PATH}/runtime/manifest.json'
            if [ -r "${'$'}runtime_manifest" ]; then
              dsh_version="${'$'}(awk -F'"' '/"dshVersion"/ { print ${'$'}4; exit }' "${'$'}runtime_manifest")"
              printf 'dsh_version=%s\n' "${'$'}dsh_version"
            fi
            if [ -r '${RuntimeProbe.RUNTIME_PATH}/state/host.pid' ]; then
              host_pid="$(cat '${RuntimeProbe.RUNTIME_PATH}/state/host.pid' 2>/dev/null)"
              kill -0 "${'$'}host_pid" >/dev/null 2>&1 && echo 'host=yes' || echo 'host=no'
            else
              echo 'host=no'
            fi
        """.trimIndent()

        internal fun parseProbeOutput(output: String): Map<String, String> = output
            .lineSequence()
            .mapNotNull { line ->
                val split = line.indexOf('=')
                if (split <= 0) null
                else line.substring(0, split).trim() to line.substring(split + 1).trim()
            }
            .toMap()
    }
}
