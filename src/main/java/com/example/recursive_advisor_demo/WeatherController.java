package com.example.recursive_advisor_demo;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/chat")
public class WeatherController {

    private final WeatherService weatherService;

    public WeatherController(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    /**
     * POST /chat
     * Body: { "question": "What is the weather in Tokyo?", "conversationId": "abc123" }
     *
     * Returns the full response as plain text.
     * If conversationId is omitted, a new session is created.
     *
     * Example:
     *   curl -X POST http://localhost:8080/chat \
     *        -H "Content-Type: application/json" \
     *        -d '{"question":"What is the weather in Paris?"}'
     */
    @PostMapping
    public Map<String, String> chat(@RequestBody Map<String, String> body) {
        String conversationId = body.getOrDefault("conversationId", UUID.randomUUID().toString());
        String question       = body.get("question");
        String answer         = weatherService.chat(conversationId, question);
        return Map.of(
                "conversationId", conversationId,
                "answer", answer);
    }

    /**
     * POST /chat/stream
     * Body: { "question": "What is the weather in Tokyo?", "conversationId": "abc123" }
     *
     * Returns tokens as a Server-Sent Events (SSE) stream.
     *
     * Example:
     *   curl -X POST http://localhost:8080/chat/stream \
     *        -H "Content-Type: application/json" \
     *        -d '{"question":"What is the weather in Paris?"}' \
     *        --no-buffer
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(@RequestBody Map<String, String> body) {
        String conversationId = body.getOrDefault("conversationId", UUID.randomUUID().toString());
        String question       = body.get("question");
        return weatherService.stream(conversationId, question);
    }
}
