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
 * Week 1 exercise: a simple Ollama agent loop with two tools.
 *
 * The program demonstrates this tool-calling flow:
 *   1. store the user's request in the conversation state
 *   2. send the state and two available tools to Ollama
 *   3. read the model's requested tool and its arguments
 *   4. execute the tool in Java
 *   5. send the tool result back to Ollama as a new message
 *
 * The available tools are get_current_time and add_numbers(a, b). The model
 * only requests a tool; Java performs the actual operation. The loop stops
 * when Ollama returns a normal answer or when MAX_STEPS is reached.
 *
 * Requires: org.json on the classpath.
 *   Download: https://repo1.maven.org/maven2/org/json/json/20240303/json-20240303.jar
 *   Run:      java -cp json-20240303.jar OllamaAgentLoop.java
 *             (Windows: use ; instead of : if you add more classpath entries)
 *
 * The conversation state contains the original user message, the assistant's
 * tool request, and the tool result so Ollama can produce the final answer.
 */
public class OllamaAgentLoopCallGetTimeAndAddNumbers {

    //static final String OLLAMA_URL = "http://localhost:11434/api/chat"; // IPv4 localhost, for Intel Macs
    static final String OLLAMA_URL = "http://[::1]:11434/api/chat";  // IPv6 localhost, for M1/M2 Macs
    static final String MODEL = "llama3.2";
    static final int MAX_STEPS = 5; // the hard-cap stop condition

    public static void main(String[] args) throws Exception {
        HttpClient client = HttpClient.newHttpClient();

        // Step 1: state. This list is the entire memory of the agent.
        List<Object> messages = new ArrayList<>();
        messages.add(new JSONObject().put("role", "user")
            .put("content", "What time is it right now, and what is 7 plus 5?"));

        // The tools this agent is allowed to use. The model can choose one or both.
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

            // Uncomment while debugging to see the raw shape Ollama actually returns:
            // System.out.println(response.body());

            JSONObject responseJson = new JSONObject(response.body());
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
                    JSONObject function = call.getJSONObject("function");
                    String toolName = function.getString("name");
                    JSONObject arguments = function.optJSONObject("arguments");

                    // Step 4: Java executes the requested tool; the model only requests it.
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

    static String executeTool(String name, JSONObject arguments) {
        if (name.equals("get_current_time")) {
            return LocalDateTime.now().toString();
        }
        if (name.equals("add_numbers")) {
            double a = arguments.getDouble("a");
            double b = arguments.getDouble("b");
            return Double.toString(a + b);
        }

        return "Error: unknown tool " + name;
    }
}
