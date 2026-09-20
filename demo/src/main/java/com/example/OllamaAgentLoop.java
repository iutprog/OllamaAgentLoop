package com.example;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Week 1 exercise: the naive agent loop, nothing hidden.
 *
 * Mechanism this implements (same 5 steps from the tutoring session):
 *   1. state  = growing list of messages (this IS the agent's memory)
 *   2. call the model, passing state + available tools
 *   3. parse response: tool call, or final answer?
 *   4. if tool call -> YOUR code executes it (the model never touches real systems)
 *   5. append the result as an observation, loop back to step 2
 *
 * Stop conditions implemented here:
 *   - default: model returns a message with no tool_calls -> print answer, return
 *   - hard cap: MAX_STEPS, enforced by code regardless of what the model wants
 *
 * Requires: org.json on the classpath.
 *   Download: https://repo1.maven.org/maven2/org/json/json/20240303/json-20240303.jar
 *   Run:      java -cp json-20240303.jar OllamaAgentLoop.java
 *             (Windows: use ; instead of : if you add more classpath entries)
 *
 * NOTE: I have not run this against your exact Ollama version. If the "tool"
 * role message format below doesn't match what your Ollama build expects,
 * print response.body() raw on the first call and compare field names before
 * assuming the loop logic is wrong — that's the actual debugging skill this
 * exercise is meant to build.
 */
public class OllamaAgentLoop {

    static final String OLLAMA_URL = "http://127.0.0.1:11436/api/chat";
    static final String MODEL = "llama3.2:latest";
    static final int MAX_STEPS = 5; // the hard-cap stop condition
    static final int MAX_HISTORY_MESSAGES = 20;

    public static void main(String[] args) throws Exception {
        HttpClient client = HttpClient.newHttpClient();

        // Step 1: state. This list is the entire memory of the agent.
        List<Object> messages = new ArrayList<>();
        messages.add(new JSONObject().put("role", "user").put("content", "What is 7 plus 5?"));

        // The tools this agent is allowed to use.
        JSONArray tools = new JSONArray().put(new JSONObject()
                .put("type", "function")
                .put("function", new JSONObject()
                        .put("name", "get_current_time")
                        .put("description", "Get the current local date and time")
                        .put("parameters", new JSONObject()
                                .put("type", "object")
                                .put("properties", new JSONObject())
                                .put("required", new JSONArray()))))
                        .put(new JSONObject()
                            .put("type", "function")
                            .put("function", new JSONObject()
                                .put("name", "add_numbers")
                                .put("description", "Add two numbers together")
                                .put("parameters", new JSONObject()
                                    .put("type", "object")
                                    .put("properties", new JSONObject()
                                        .put("a", new JSONObject().put("type", "number"))
                                        .put("b", new JSONObject().put("type", "number")))
                                    .put("required", new JSONArray().put("a").put("b")))));

        for (int step = 1; step <= MAX_STEPS; step++) { 
            System.out.println("--- step " + step + " ---");

            trimHistory(messages);

            // Step 2: call the model with current state + tools.
            JSONObject requestBody = new JSONObject()
                    .put("model", MODEL)
                    .put("messages", new JSONArray(messages))
                    .put("tools", tools)
                    .put("stream", false);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(OLLAMA_URL))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            JSONObject responseJson = new JSONObject(response.body());
            if (response.statusCode() < 200 || response.statusCode() >= 300 || !responseJson.has("message")) {
                String error = responseJson.optString("error", response.body());
                throw new IllegalStateException("Ollama request failed (HTTP "
                        + response.statusCode() + "): " + error);
            }
            JSONObject message = responseJson.getJSONObject("message");

            // Step 3: parse — tool call, or final answer?
            boolean wantsTool = message.has("tool_calls")
                    && message.get("tool_calls") != JSONObject.NULL
                    && message.getJSONArray("tool_calls").length() > 0;

            if (wantsTool) {
                messages.add(message); // keep the assistant's tool-request in history

                JSONArray toolCalls = message.getJSONArray("tool_calls");
                for (int i = 0; i < toolCalls.length(); i++) {
                    JSONObject call = toolCalls.getJSONObject(i);
                    String toolName = call.getJSONObject("function").getString("name");

                    // Step 4: YOUR code executes the tool. The model cannot do this itself.
                    JSONObject arguments = call.getJSONObject("function").optJSONObject("arguments");
                    String result = executeTool(toolName, arguments);
                    System.out.println("executed tool: " + toolName + " -> " + result);

                    // Step 5: feed the observation back in.
                    messages.add(new JSONObject().put("role", "tool").put("content", result));
                }
                // loop continues — model sees the tool result on the next call
            } else {
                System.out.println("Final answer: " + message.optString("content", "(no content)"));
                return; // default stop condition
            }
        }

        System.out.println("Stopped: max iterations (" + MAX_STEPS + ") reached.");
    }

    static void trimHistory(List<Object> messages) {
        while (messages.size() > MAX_HISTORY_MESSAGES && messages.size() > 1) {
            int end = 2;
            JSONObject oldest = (JSONObject) messages.get(1);
            if ("assistant".equals(oldest.optString("role"))
                    && oldest.optJSONArray("tool_calls") != null
                    && oldest.optJSONArray("tool_calls").length() > 0) {
                end = 2;
                while (end < messages.size()
                        && "tool".equals(((JSONObject) messages.get(end)).optString("role"))) {
                    end++;
                }
            }
            messages.subList(1, Math.min(end, messages.size())).clear();
        }
    }

    static String executeTool(String name, JSONObject arguments) {
        if (name.equals("get_current_time")) {
            return LocalDateTime.now().toString();
        }

        if (name.equals("add_numbers")) {
            if (arguments == null) {
                return "Error: add_numbers requires arguments a and b";
            }
            return Double.toString(addNumbers(arguments.getDouble("a"), arguments.getDouble("b")));
        }

        return "Error: unknown tool " + name;
    }

    static double addNumbers(double a, double b) {
        return a + b;
    }
}
