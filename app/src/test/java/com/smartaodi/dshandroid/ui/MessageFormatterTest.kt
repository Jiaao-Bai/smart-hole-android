package com.smartaodi.dshandroid.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageFormatterTest {
    @Test
    fun `keeps a plain response in one text block`() {
        val blocks = parseMessageBlocks("普通回复")

        assertEquals(1, blocks.size)
        assertTrue(blocks.single() is MessageBlock.Prose)
    }

    @Test
    fun `separates fenced code from surrounding prose`() {
        val blocks = parseMessageBlocks("先执行：\n```kotlin\nval answer = 42\n```\n完成。")

        assertEquals(3, blocks.size)
        assertEquals("先执行：", (blocks[0] as MessageBlock.Prose).inlines.single().text)
        assertTrue(blocks[1] is MessageBlock.CodeBlock)
        assertEquals("kotlin", (blocks[1] as MessageBlock.CodeBlock).language)
        assertEquals("val answer = 42", (blocks[1] as MessageBlock.CodeBlock).text)
        assertEquals("完成。", (blocks[2] as MessageBlock.Prose).inlines.single().text)
    }

    @Test
    fun `renders an unfinished streaming fence as code`() {
        val blocks = parseMessageBlocks("```sh\necho hello")

        assertTrue(blocks.single() is MessageBlock.CodeBlock)
        assertEquals("sh", (blocks.single() as MessageBlock.CodeBlock).language)
        assertEquals("echo hello", (blocks.single() as MessageBlock.CodeBlock).text)
    }

    @Test
    fun `parses headings emphasis lists and tables`() {
        val blocks = parseMessageBlocks(
            """## 标题

这是 **重点** 和 `code`。

- 第一项
- 第二项

| 项目 | 值 |
|---|---|
| Root | Magisk |
""".trimIndent(),
        )

        assertEquals(4, blocks.size)
        assertEquals(2, (blocks[0] as MessageBlock.Prose).headingLevel)
        val prose = blocks[1] as MessageBlock.Prose
        assertTrue(prose.inlines.any { it.bold && it.text == "重点" })
        assertTrue(prose.inlines.any { it.code && it.text == "code" })
        assertEquals(2, (blocks[2] as MessageBlock.ListBlock).items.size)
        assertEquals("项目", (blocks[3] as MessageBlock.Table).header[0].single().text)
    }
}
