package com.smartaodi.dshandroid.features.workspace

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceFeatureTest {
    @Test
    fun `decodes workspace order membership and archive baseline`() {
        val directory = WorkspaceFeatureCodec.directory(
            JSONObject(
                """{"items":[{"workspaceId":"w1","path":"/data/local/code","title":"Code","sessionIds":["s2","s1"],"createdAt":"a","updatedAt":"b"}],"archivedSessionIds":["s3"]}""",
            ),
        )

        assertEquals("Code", directory.items.single().title)
        assertEquals(listOf("s2", "s1"), directory.items.single().sessionIds)
        assertTrue("s3" in directory.archivedSessionIds)
    }
}
