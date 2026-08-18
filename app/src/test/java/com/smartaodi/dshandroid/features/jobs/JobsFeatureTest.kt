package com.smartaodi.dshandroid.features.jobs

import com.smartaodi.dshandroid.protocol.DshServerEvent
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class JobsFeatureTest {
    @Test
    fun `decodes complete jobs snapshot without enumerating plugin kinds`() {
        val items = JobsFeatureCodec.snapshot(
            DshServerEvent(
                "rpc",
                "session/jobs",
                JSONObject("""{"sessionId":"s1","jobs":[{"id":"future-plugin-1","kind":"future-plugin","label":"long task","status":"running","startedAt":42}]}"""),
            ),
            "s1",
        )!!

        assertEquals("future-plugin", items.single().kind)
        assertEquals(1, JobsFeatureState(items).activeCount)
    }
}
