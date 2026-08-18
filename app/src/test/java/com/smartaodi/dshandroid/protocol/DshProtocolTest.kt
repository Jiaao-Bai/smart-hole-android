package com.smartaodi.dshandroid.protocol

import com.smartaodi.dshandroid.features.CoreSessionFeatureRegistry
import com.smartaodi.dshandroid.features.SessionFeatureContext
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DshProtocolTest {
    @Test
    fun `builds official client request envelope`() {
        val request = DshProtocol.request(
            method = "host.describe",
            payload = JSONObject(),
            rpcId = "rpc-1",
        )
        val json = JSONObject(request.json)

        assertEquals("client-request", json.getString("type"))
        assertEquals("rpc-1", json.getString("rpcId"))
        assertEquals("host.describe", json.getString("method"))
        assertEquals(0, json.getJSONObject("payload").length())
    }

    @Test
    fun `parses host description response`() {
        val result = DshProtocol.response(
            expectedRpcId = "rpc-2",
            body = """
                {
                  "type":"server-response",
                  "rpcId":"rpc-2",
                  "result":{"ok":true,"value":{
                    "version":"0.0.1",
                    "cwd":"/workspace",
                    "provider":"deepseek-official",
                    "model":"deepseek-v4-flash",
                    "attachedSessions":2,
                    "canOpenPath":false
                  }}
                }
            """.trimIndent(),
        ) as DshRpcResult.Success

        val host = DshProtocol.hostDescription(result.value)
        assertEquals("0.0.1", host.version)
        assertEquals("/workspace", host.cwd)
        assertEquals(2, host.attachedSessions)
    }

    @Test
    fun `parses model directory and selection`() {
        val directory = DshProtocol.modelDirectory(
            JSONObject(
                """{"current":{"provider":"deepseek-official","model":"deepseek-v4-flash","reasoningEffort":"high"},"routable":true,"groups":[{"id":"deepseek-official","name":"DeepSeek","models":[{"id":"deepseek-v4-flash","name":"DeepSeek V4 Flash","reasoning":{"efforts":[{"id":"off","name":"Off"},{"id":"high","name":"High"}],"defaultEffort":"high"}}]}]}""",
            ),
        )

        assertTrue(directory.routable)
        assertEquals("deepseek-v4-flash", directory.current?.model)
        assertEquals(listOf("off", "high"), directory.groups.single().models.single().efforts.map { it.id })
    }

    @Test
    fun `folds effective plan mode from session projection`() {
        val sessions = DshProtocol.sessions(
            JSONObject(
                """{"items":[{"sessionId":"session-plan","updatedAt":1,"running":false,"blank":false,"agentPreset":"android","projections":{"values":{"plan":{"active":false,"pending":true}}}}]}""",
            ),
            agentPreset = "android",
        )

        assertTrue(sessions.single().planMode)
    }

    @Test
    fun `parses generic future event without knowing its body`() {
        val event = DshProtocol.serverEvent(
            """
                {
                  "type":"server-request",
                  "rpcId":"event-1",
                  "method":"session/future-event",
                  "payload":{"type":"session/future-event","newField":{"nested":true}}
                }
            """.trimIndent(),
        )

        assertEquals("session/future-event", event.method)
        assertTrue(event.payload.getJSONObject("newField").getBoolean("nested"))
    }

    @Test
    fun `parses pending approval and question requests`() {
        val approval = DshProtocol.pendingInteraction(
            DshProtocol.serverEvent(
                """{"type":"server-request","rpcId":"approve-rpc","method":"approval/requested","payload":{"type":"approval/requested","sessionId":"session-1","approvalId":"approval-1","toolName":"bash","reason":"需要完整访问"}}""",
            ),
        ) as PendingInteraction.Approval
        assertEquals("approval-1", approval.approvalId)
        assertEquals("需要完整访问", approval.reason)

        val question = DshProtocol.pendingInteraction(
            DshProtocol.serverEvent(
                """{"type":"server-request","rpcId":"question-rpc","method":"question/requested","payload":{"type":"question/requested","sessionId":"session-1","questions":[{"id":"q1","header":"范围","question":"检查哪里？","options":[{"label":"系统","description":"系统范围"},{"label":"应用"}],"multiSelect":true}]}}""",
            ),
        ) as PendingInteraction.Question
        assertEquals("检查哪里？", question.questions.single().question)
        assertTrue(question.questions.single().multiSelect)
        assertEquals(listOf("系统", "应用"), question.questions.single().options.map { it.label })
    }

    @Test
    fun `builds accepted interaction response envelopes`() {
        val body = JSONObject(
            DshProtocol.clientResponse(
                "question-rpc",
                JSONObject().put("sessionId", "session-1"),
            ),
        )
        assertEquals("client-response", body.getString("type"))
        assertEquals("question-rpc", body.getString("rpcId"))
        assertTrue(body.getJSONObject("result").getBoolean("ok"))
        assertTrue(DshProtocol.responseAccepted("""{"accepted":true}"""))
    }

    @Test
    fun `builds cancelled question response`() {
        val body = JSONObject(DshProtocol.cancelledClientResponse("question-rpc"))
        val result = body.getJSONObject("result")
        assertEquals(false, result.getBoolean("ok"))
        assertEquals("cancelled", result.getJSONObject("error").getString("code"))
    }

    @Test
    fun `folds finalized user and assistant history`() {
        val history = DshProtocol.history(
            JSONObject(
                """{
                  "events":[
                    {"event":{"type":"user/message","seq":1,"data":{"role":"user","content":[{"type":"text","text":"你好"}]}}},
                    {"event":{"type":"assistant/message","seq":4,"data":{"message":{"role":"assistant","content":[{"type":"text","text":"你好，Android"}]}}}}
                  ],
                  "hasMore":false
                }""",
            ),
        )

        assertEquals(listOf(ChatRole.User, ChatRole.Assistant), history.messages.map { it.role })
        assertEquals(listOf("你好", "你好，Android"), history.messages.map { it.text })
        assertEquals(1L, history.oldestSeq)
    }

    @Test
    fun `retains finalized reasoning separately from visible answer`() {
        val history = DshProtocol.history(
            JSONObject(
                """{"events":[{"event":{"type":"assistant/message","seq":7,"data":{"message":{"content":[{"type":"reasoning","text":"内部推理"},{"type":"text","text":"最终答案"}]}}}}]}""",
            ),
        )
        assertEquals("内部推理", history.messages.single().reasoning)
        assertEquals("最终答案", history.messages.single().text)
    }

    @Test
    fun `hides internal runtime context from visible history`() {
        val history = DshProtocol.history(
            JSONObject(
                """{
                  "events":[
                    {"event":{"type":"user/message","seq":1,"data":{"content":[{"type":"text","text":"Current runtime context. This snapshot supersedes earlier runtime-context snapshots.\\n\\nCurrent DSH file policy: danger-full-access."}]}}},
                    {"event":{"type":"user/message","seq":2,"data":{"content":[{"type":"text","text":"真实问题"}]}}}
                  ]
                }""",
            ),
        )

        assertEquals(listOf("真实问题"), history.messages.map { it.text })
    }

    @Test
    fun `hides plugin sourced user messages independent of their wording`() {
        val history = DshProtocol.history(
            JSONObject(
                """{
                  "events":[
                    {"event":{"type":"user/message","seq":1,"data":{"source":{"kind":"plugin","plugin":"policy"},"content":[{"type":"text","text":"A future injected message with different wording"}]}}},
                    {"event":{"type":"user/message","seq":2,"data":{"source":{"kind":"user","rpcId":"rpc-1"},"content":[{"type":"text","text":"保留我的问题"}]}}}
                  ]
                }""",
            ),
        )

        assertEquals(listOf("保留我的问题"), history.messages.map { it.text })
    }

    @Test
    fun `does not render assistant tool call heads as chat messages`() {
        val message = DshProtocol.finalizedMessage(
            JSONObject(
                """{"type":"assistant/message","seq":3,"data":{"message":{"content":[{"type":"tool-call","name":"android_system"}]}}}""",
            ),
        )

        assertEquals(null, message)
    }

    @Test
    fun `folds a tool call and result into one turn activity`() {
        val history = DshProtocol.history(
            JSONObject(
                """{
                  "events":[
                    {"event":{"type":"tool/call","seq":3,"data":{"turn":1,"step":1,"callId":"call-1","name":"bash","arguments":"{\"command\":\"id\",\"description\":\"Inspect identity\"}"}}},
                    {"event":{"type":"tool/result","seq":4,"data":{"turn":1,"step":1,"message":{"source":{"kind":"tool","callId":"call-1"},"content":[{"type":"tool-result","toolCallId":"call-1","content":[{"type":"text","text":"uid=0"}],"isError":false}]}}}},
                    {"event":{"type":"tool/call","seq":5,"data":{"turn":1,"step":1,"callId":"call-1","name":"bash","arguments":"{\"command\":\"id\",\"description\":\"Inspect identity\"}"}}},
                    {"event":{"type":"turn/end","seq":6,"data":{"turn":1,"reason":{"kind":"completed"}}}}
                  ]
                }""",
            ),
        )

        assertEquals(1, history.messages.size)
        val activity = history.messages.single().toolActivity
        assertEquals(false, activity?.running)
        assertEquals("Inspect identity", activity?.calls?.single()?.summary)
        assertEquals("uid=0", activity?.calls?.single()?.output)
        assertEquals(ToolCallState.Success, activity?.calls?.single()?.state)
    }

    @Test
    fun `consumes official plugin tool presentations and projection baseline`() {
        val history = DshProtocol.history(
            JSONObject(
                """{
                  "events":[
                    {
                      "event":{"type":"tool/call","seq":3,"data":{"turn":1,"step":1,"callId":"call-1","name":"bash","arguments":"{\"command\":\"id\"}"}},
                      "view":{"for":"call","view":{"card":"terminal","title":"id","description":"Inspect identity","cwd":"/data/local/tmp"}}
                    },
                    {
                      "event":{"type":"tool/result","seq":4,"data":{"turn":1,"step":1,"message":{"source":{"kind":"tool","callId":"call-1"},"content":[{"type":"tool-result","content":[{"type":"text","text":"uid=0"}],"isError":false}]}}},
                      "view":{"for":"result","view":{"card":"terminal","output":"uid=0","exitCode":0}}
                    }
                  ],
                  "hasMore":false,
                  "projections":{"asOfSeq":4,"values":{"title":"Root audit","goal":{"roundsStarted":1}}}
                }""",
            ),
        )

        val call = history.messages.single().toolActivity?.calls?.single()
        assertEquals("terminal", call?.callPresentation?.card)
        assertEquals("Inspect identity", call?.summary)
        assertEquals("/data/local/tmp", call?.callPresentation?.cwd)
        assertEquals("uid=0", call?.resultPresentation?.output)
        assertEquals(0, call?.resultPresentation?.exitCode)
        assertEquals(4L, history.projections?.values?.get("goal")?.seq)
        assertEquals("{\"roundsStarted\":1}", history.projections?.values?.get("goal")?.json)
    }

    @Test
    fun `preserves an unknown future tool card for generic fallback`() {
        val event = JSONObject(
            """{"type":"tool/call","seq":1,"data":{"turn":2,"step":1,"callId":"x","name":"future_tool","arguments":"{}"}}""",
        )
        val view = JSONObject(
            """{"for":"call","view":{"card":"map","title":"Device map","nodes":[1,2,3]}}""",
        )

        val messages = DshProtocol.foldConversationEvent(emptyList(), event, view)
        val presentation = messages.single().toolActivity?.calls?.single()?.callPresentation

        assertEquals("map", presentation?.card)
        assertEquals("Device map", presentation?.title)
        assertTrue(presentation?.rawJson?.contains("nodes") == true)
    }

    @Test
    fun `projection store ignores stale frames per key`() {
        val baseline = SessionProjectionSnapshot(
            asOfSeq = 10,
            values = mapOf("goal" to ProjectionValue(10, "{\"roundsStarted\":1}")),
        )

        val stale = baseline.updated("goal", "{\"roundsStarted\":0}", 9)
        val fresh = stale.updated("goal", "{\"roundsStarted\":2}", 11)

        assertEquals("{\"roundsStarted\":1}", stale.values.getValue("goal").json)
        assertEquals("{\"roundsStarted\":2}", fresh.values.getValue("goal").json)
        assertEquals(11L, fresh.asOfSeq)
    }

    @Test
    fun `decodes goal todos and plan from generic projections`() {
        val snapshot = DshSurfaceAdapter.projectionSnapshot(
            JSONObject(
                """{"asOfSeq":8,"values":{"goal":{"goal":{"id":"g1","revision":1,"objective":"Ship native surface","phase":"active","maxGoalRounds":32},"roundsStarted":3,"createdAt":1,"updatedAt":2},"todos":[{"content":"Consume presentations","status":"completed"},{"content":"Render projections","status":"in_progress"}],"plan":{"active":false,"pending":true}}}""",
            ),
        )

        val features = CoreSessionFeatureRegistry.decode(
            SessionFeatureContext("session-1", snapshot, emptyList()),
        )

        assertEquals("Ship native surface", features.work?.goal?.objective)
        assertEquals(3, features.work?.goal?.roundsStarted)
        assertEquals(listOf("completed", "in_progress"), features.work?.todos?.map { it.status })
        assertEquals(true, features.work?.plan?.enabled)
    }

    @Test(expected = DshProtocolException::class)
    fun `rejects an unadapted DSH wire version`() {
        DshSurfaceAdapter.requireCompatibleDshVersion("0.1.0-rc.8")
    }

    @Test(expected = DshProtocolException::class)
    fun `rejects an unadapted Host API wire version`() {
        DshSurfaceAdapter.requireCompatibleHostApiVersion("0.0.2")
    }

    @Test
    fun `groups multiple real calls in the same turn`() {
        val first = JSONObject(
            """{"type":"tool/call","seq":1,"data":{"turn":7,"step":1,"callId":"a","name":"bash","arguments":"{\"command\":\"one\"}"}}""",
        )
        val second = JSONObject(
            """{"type":"tool/call","seq":2,"data":{"turn":7,"step":2,"callId":"b","name":"android_system","arguments":"{\"operation\":\"device_info\"}"}}""",
        )

        val messages = DshProtocol.foldConversationEvent(
            DshProtocol.foldConversationEvent(emptyList(), first),
            second,
        )

        assertEquals(1, messages.size)
        assertEquals(listOf("a", "b"), messages.single().toolActivity?.calls?.map { it.callId })
    }

    @Test
    fun `extracts model failure from turn end`() {
        val event = JSONObject(
            """{"type":"turn/end","data":{"turn":1,"reason":{"kind":"error","error":{"code":"MISSING_CREDENTIAL","message":"API key missing"}}}}""",
        )

        assertEquals("API key missing", DshProtocol.turnError(event))
    }

    @Test
    fun `selects most recently updated session`() {
        val latest = DshProtocol.latestSessionId(
            JSONObject(
                """{"items":[
                  {"sessionId":"older","updatedAt":10,"running":false,"blank":false},
                  {"sessionId":"newer","updatedAt":20,"running":false,"blank":true}
                ]}""",
            ),
        )

        assertEquals("newer", latest)
    }

    @Test
    fun `resumes only the Android agent preset`() {
        val latest = DshProtocol.latestSessionId(
            JSONObject(
                """{"items":[
                  {"sessionId":"android-older","updatedAt":10,"agentPreset":"android"},
                  {"sessionId":"desktop-newer","updatedAt":30,"agentPreset":"standard"},
                  {"sessionId":"android-newer","updatedAt":20,"agentPreset":"android"}
                ]}""",
            ),
            agentPreset = "android",
        )

        assertEquals("android-newer", latest)
    }

    @Test
    fun `parses titled root Android sessions for navigation`() {
        val sessions = DshProtocol.sessions(
            JSONObject(
                """{"items":[
                  {"sessionId":"older","updatedAt":10,"running":false,"blank":false,"agentPreset":"android","cwd":"/work/a","projections":{"values":{"title":"旧对话"}}},
                  {"sessionId":"child","updatedAt":40,"running":false,"blank":false,"agentPreset":"android","origin":"subagent","projections":{"values":{"title":"子任务"}}},
                  {"sessionId":"desktop","updatedAt":50,"running":false,"blank":false,"agentPreset":"standard"},
                  {"sessionId":"newer","updatedAt":30,"running":true,"blank":true,"agentPreset":"android","cwd":"/work/b","projections":{"values":{"title":null}}}
                ]}""",
            ),
            agentPreset = "android",
        )

        assertEquals(listOf("child", "newer", "older"), sessions.map { it.sessionId })
        assertEquals("子任务", sessions.first().title)
        assertEquals("subagent", sessions.first().origin)
        assertTrue(sessions[1].blank)
    }

    @Test(expected = DshProtocolException::class)
    fun `rejects mismatched rpc id`() {
        DshProtocol.response(
            expectedRpcId = "wanted",
            body = """{"type":"server-response","rpcId":"other","result":{"ok":true,"value":{}}}""",
        )
    }
}
