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
 * Week 1 exercise: inspect a basic Ollama chat response without tool calls.
 *
 * This program demonstrates the first parts of an agent loop:
 *   1. create a conversation state containing the user's message
 *   2. send that state to the Ollama chat API
 *   3. parse and print the response, including its role and content
 *
 * No tools are sent to the model and no tool calls are executed. The request is
 * repeated until the hard cap, MAX_STEPS, is reached.
 *
 * Requires: org.json on the classpath.
 *   Download: https://repo1.maven.org/maven2/org/json/json/20240303/json-20240303.jar
 *   Run:      java -cp json-20240303.jar OllamaAgentLoop.java
 *             (Windows: use ; instead of : if you add more classpath entries)
 *
 * The raw response body and nested message object are printed so you can
 * inspect the JSON fields returned by your Ollama version.
 */
public class OllamaAgentLoopNoToolCall {

    //static final String OLLAMA_URL = "http://localhost:11434/api/chat"; // IPv4 localhost, for Intel Macs
    static final String OLLAMA_URL = "http://[::1]:11434/api/chat";  // IPv6 localhost, for M1/M2 Macs
    static final String MODEL = "llama3.2";
    static final int MAX_STEPS = 5; // the hard-cap stop condition

    public static void main(String[] args) throws Exception {
        HttpClient client = HttpClient.newHttpClient();

        // Step 1: state. This list is the entire memory of the agent.
        List<Object> messages = new ArrayList<>();
        messages.add(new JSONObject().put("role", "user").put("content", "What time is it right now?"));


        for (int step = 1; step <= MAX_STEPS; step++) {
            System.out.println("--- step " + step + " ---");

            // Step 2: call the model with current state + tools.
            JSONObject requestBody = new JSONObject()
                    .put("model", MODEL)
                    .put("messages", new JSONArray(messages))
                    .put("stream", false);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(OLLAMA_URL))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            // Uncomment while debugging to see the raw shape Ollama actually returns:
            System.out.println("------------------------ RESPONSE BODY --------------------------");
            System.out.println(response.body());
            System.out.println("----------------------- MESSAGE ---------------------------");
            JSONObject responseJson = new JSONObject(response.body());
            JSONObject message = responseJson.getJSONObject("message");
            System.out.println(message.toString(2));
            System.out.println("----------------------- ROLE ---------------------------");
            System.out.println(message.optString("role", "(no role)"));
            System.out.println("----------------------- CONTENT ---------------------------");
            System.out.println(message.optString("content", "(no content)"));
            System.out.println("===================================================");

        }

        System.out.println("Stopped: max iterations (" + MAX_STEPS + ") reached.");

    }

}
