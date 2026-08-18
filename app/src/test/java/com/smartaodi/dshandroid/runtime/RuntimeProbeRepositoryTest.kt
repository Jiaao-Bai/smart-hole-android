package com.smartaodi.dshandroid.runtime

import org.junit.Assert.assertEquals
import org.junit.Test

class RuntimeProbeRepositoryTest {
    @Test
    fun `parses only key value probe lines`() {
        val parsed = RuntimeProbeRepository.parseProbeOutput(
            """
                magisk: unrelated diagnostic
                uid=0
                selinux=Enforcing
                root_provider=KernelSU
                runtime=no
                dsh_version=0.1.0-rc.7
            """.trimIndent(),
        )

        assertEquals("0", parsed["uid"])
        assertEquals("Enforcing", parsed["selinux"])
        assertEquals("KernelSU", parsed["root_provider"])
        assertEquals("no", parsed["runtime"])
        assertEquals("0.1.0-rc.7", parsed["dsh_version"])
        assertEquals(5, parsed.size)
    }
}
