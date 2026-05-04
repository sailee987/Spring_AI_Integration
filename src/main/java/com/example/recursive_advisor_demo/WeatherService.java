package com.example.recursive_advisor_demo;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class WeatherService {

    // Key used by MessageChatMemoryAdvisor to scope memory per session
    private static final String CONVERSATION_ID = "conversationId";

    private final ChatClient chatClient;

    public WeatherService(ChatClient.Builder builder, WeatherTools weatherTools) {
        // In-memory store — keyed by conversationId, keeps last 20 messages
        MessageWindowChatMemory memory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(20)
                .build();

        this.chatClient = builder
                .defaultTools(weatherTools)
                .defaultAdvisors(
                        ToolCallAdvisor.builder().build(),
                        MessageChatMemoryAdvisor.builder(memory).build())
                .defaultSystem("You are a helpful weather assistant. " +
                        "Use the weather tool to fetch real-time data. " +
                        "Be concise and friendly.")
                .build();
    }

    /**
     * Blocking call — returns the full response once complete.
     * Passing conversationId scopes memory to this session.
     */
    public String chat(String conversationId, String question) {
        return chatClient.prompt(question)
                .advisors(a -> a.param(CONVERSATION_ID, conversationId))
                .call()
                .content();
    }

    /**
     * Streaming call — returns tokens as a Flux<String> for SSE.
     * Passing conversationId scopes memory to this session.
     */
    public Flux<String> stream(String conversationId, String question) {
        return chatClient.prompt(question)
                .advisors(a -> a.param(CONVERSATION_ID, conversationId))
                .stream()
                .content();
    }
}
