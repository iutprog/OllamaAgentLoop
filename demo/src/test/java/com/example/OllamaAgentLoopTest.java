package com.example;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

/**
 * Unit test for simple OllamaAgentLoop.
 */
public class OllamaAgentLoopTest 
{
    @Test
    public void trimHistoryPreservesOriginalUserMessage() {
        List<Object> messages = new ArrayList<>();
        JSONObject original = new JSONObject().put("role", "user").put("content", "original");
        messages.add(original);
        for (int i = 0; i < OllamaAgentLoop.MAX_HISTORY_MESSAGES; i++) {
            messages.add(new JSONObject().put("role", "user").put("content", "message-" + i));
        }

        OllamaAgentLoop.trimHistory(messages);

        assertEquals(OllamaAgentLoop.MAX_HISTORY_MESSAGES, messages.size());
        assertSame(original, messages.get(0));
    }

    @Test
    public void trimHistoryRemovesAssistantToolPairTogether() {
        List<Object> messages = new ArrayList<>();
        JSONObject original = new JSONObject().put("role", "user").put("content", "original");
        JSONObject assistant = new JSONObject()
                .put("role", "assistant")
                .put("tool_calls", new JSONArray().put(new JSONObject()));
        JSONObject tool = new JSONObject().put("role", "tool").put("content", "12");
        messages.add(original);
        messages.add(assistant);
        messages.add(tool);
        while (messages.size() <= OllamaAgentLoop.MAX_HISTORY_MESSAGES) {
            messages.add(new JSONObject().put("role", "user").put("content", "later"));
        }

        OllamaAgentLoop.trimHistory(messages);

        assertSame(original, messages.get(0));
        assertEquals("user", ((JSONObject) messages.get(1)).getString("role"));
        for (Object message : messages) {
            JSONObject json = (JSONObject) message;
            assertEquals(false, json.optString("role").equals("tool"));
        }
    }

    @Test
    public void trimHistoryRemovesTwoToolResponsesWithAssistantCallTogether() {
        List<Object> messages = new ArrayList<>();
        JSONObject original = new JSONObject().put("role", "user").put("content", "original");
        JSONObject assistant = new JSONObject()
                .put("role", "assistant")
                .put("tool_calls", new JSONArray()
                        .put(new JSONObject().put("id", "call-1"))
                        .put(new JSONObject().put("id", "call-2")));
        JSONObject firstTool = new JSONObject().put("role", "tool").put("content", "first result");
        JSONObject secondTool = new JSONObject().put("role", "tool").put("content", "second result");
        messages.add(original);
        messages.add(assistant);
        messages.add(firstTool);
        messages.add(secondTool);
        while (messages.size() <= OllamaAgentLoop.MAX_HISTORY_MESSAGES) {
            messages.add(new JSONObject().put("role", "user").put("content", "later"));
        }

        OllamaAgentLoop.trimHistory(messages);

        assertSame(original, messages.get(0));
        assertFalse(messages.contains(assistant));
        assertFalse(messages.contains(firstTool));
        assertFalse(messages.contains(secondTool));
    }
}
