package com.smartaodi.dshandroid.features.commands

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Test

class CommandsFeatureTest {
    @Test
    fun `decodes generated remote result and filters slash candidates`() {
        val items = CommandsFeatureCodec.directory(
            JSONArray(
                """[{"name":"compact","description":"压缩上下文"},{"name":"goal","description":"设置目标","input":{"hint":"<objective>"}}]""",
            ),
        )
        val state = CommandsFeatureState(items)

        assertEquals(listOf("compact"), state.matching("/co").map { it.name })
        assertEquals("<objective>", state.matching("/g").single().inputHint)
        assertEquals(emptyList<CommandDescriptor>(), state.matching("/goal task"))
    }
}
