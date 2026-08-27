package com.example;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Week 1 exercise: call a simple add_numbers tool from an Ollama agent loop.
 *
 * This program demonstrates the basic tool-calling agent loop:
 *   1. create a conversation state containing the user's message
 *   2. tell the model which tool it may call
 *   3. execute the requested tool in Java
 *   4. send the tool result back to the model
 *
 * The model can request add_numbers(a, b), but the model does not perform the
 * calculation. Java executes the tool and returns the result to the model.
 *
 * Requires: org.json on the classpath.
 *   Download: https://repo1.maven.org/maven2/org/json/json/20240303/json-20240303.jar
 *   Run:      java -cp json-20240303.jar OllamaAgentLoop.java
 *             (Windows: use ; instead of : if you add more classpath entries)
 *
 * The raw response body is printed so you can inspect the JSON fields returned
 * by your Ollama version.
 */
public class OllamaAgentLoopCallToolAddNumbers {

    //static final String OLLAMA_URL = "http://localhost:11434/api/chat"; // IPv4 localhost, for Intel Macs
    static final String OLLAMA_URL = "http://[::1]:11434/api/chat";  // IPv6 localhost, for M1/M2 Macs
    static final String MODEL = "llama3.2";
    static final int MAX_STEPS = 5; // the hard-cap stop condition

    public static void main(String[] args) throws Exception {
        HttpClient client = HttpClient.newHttpClient();

        // Step 1: state. This list is the entire memory of the agent.
        List<Object> messages = new ArrayList<>();
        messages.add(new JSONObject().put("role", "user").put("content", "Please add 7 and 5."));

        // Describe the tool to Ollama. This tells the model the tool's name,
        // purpose, and the two numbers that the Java method expects.
        JSONArray tools = new JSONArray().put(new JSONObject()
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

            // Print the response so the model's tool call can be inspected.
            System.out.println("------------------------ RESPONSE BODY --------------------------");
            System.out.println(response.body());
            JSONObject responseJson = new JSONObject(response.body());
            JSONObject message = responseJson.getJSONObject("message");

            if (message.has("tool_calls")) {
                // Keep the assistant's request in the conversation history.
                messages.add(message);
                JSONArray toolCalls = message.getJSONArray("tool_calls");

                for (int i = 0; i < toolCalls.length(); i++) {
                    JSONObject call = toolCalls.getJSONObject(i);
                    JSONObject function = call.getJSONObject("function");
                    String toolName = function.getString("name");
                    JSONObject arguments = function.getJSONObject("arguments");

                    // The model supplies the arguments; Java performs the real calculation.
                    double result = addNumbers(arguments.getDouble("a"), arguments.getDouble("b"));
                    System.out.println("Executed " + toolName + ": " + result);

                    // Give the tool result back so the model can produce its final answer.
                    messages.add(new JSONObject()
                            .put("role", "tool")
                            .put("content", Double.toString(result)));
                }
            } else {
                System.out.println("Final answer: " + message.optString("content", "(no content)"));
                return;
            }
        }

        System.out.println("Stopped: max iterations (" + MAX_STEPS + ") reached.");

    }

    static double addNumbers(double a, double b) {
        return a + b;
    }
}
