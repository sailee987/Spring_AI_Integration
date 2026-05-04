# Spring AI Recursive Advisors Demo

A Spring Boot application showcasing the **Recursive Advisors** feature in Spring AI 2.0, with a real-time weather assistant backed by the [Open-Meteo](https://open-meteo.com/) API. The app exposes a REST API with blocking and streaming endpoints, and maintains per-session conversation memory.

## Overview

This project demonstrates:

- **Recursive Advisors** — `ToolCallAdvisor` loops through the advisor chain until all tool calls are resolved
- **Real Weather Data** — live data from Open-Meteo (free, no API key required)
- **REST API** — blocking (`POST /chat`) and streaming SSE (`POST /chat/stream`) endpoints
- **Conversation Memory** — per-session memory via `MessageChatMemoryAdvisor` so follow-up questions work naturally
- **Dynamic Input** — city and question are runtime inputs, nothing hardcoded

## Prerequisites

- Java 17 or higher
- Maven 3.6+ (or use the included `mvnw` wrapper)
- An [OpenRouter](https://openrouter.ai/) API key (free tier available)

## Configuration

All configuration lives in `src/main/resources/application.properties`:

```properties
spring.ai.openai.api-key=<your-openrouter-key>
spring.ai.openai.base-url=https://openrouter.ai/api/v1
spring.ai.openai.chat.options.model=openrouter/free

# Open-Meteo endpoints (no API key needed)
weather.geocoding.url=https://geocoding-api.open-meteo.com/v1/search
weather.forecast.url=https://api.open-meteo.com/v1/forecast
```

The `openrouter/free` model router automatically selects a free model that supports tool calling.

## Project Structure

```
src/main/java/com/example/recursive_advisor_demo/
├── RecursiveAdvisorDemoApplication.java  ← Spring Boot entry point
├── WeatherTools.java                     ← @Tool: geocoding + live weather fetch
├── WeatherService.java                   ← ChatClient wired with memory & advisors
└── WeatherController.java                ← REST endpoints (/chat, /chat/stream)
```

## Key Components

### 1. WeatherTools

Fetches real weather data in two steps using the JDK `HttpClient`:

1. **Geocoding** — resolves city name → latitude/longitude via Open-Meteo geocoding API
2. **Forecast** — fetches live temperature, humidity, wind speed, and weather condition

```java
@Tool(description = "Get the current weather for a given city name")
public String weather(String city) {
    // Step 1: geocode city → lat/lon
    // Step 2: fetch current weather for those coordinates
}
```

### 2. WeatherService

Builds the `ChatClient` with all advisors and memory wired in:

```java
this.chatClient = builder
    .defaultTools(weatherTools)
    .defaultAdvisors(
        ToolCallAdvisor.builder().build(),              // recursive tool-call loop
        MessageChatMemoryAdvisor.builder(memory).build()) // per-session memory
    .defaultSystem("You are a helpful weather assistant...")
    .build();
```

- **`ToolCallAdvisor`** — keeps calling the model until it stops requesting tool calls
- **`MessageChatMemoryAdvisor`** — stores conversation history keyed by `conversationId`, keeping the last 20 messages

### 3. WeatherController

Exposes two endpoints:

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/chat` | Blocking — returns full response as JSON |
| `POST` | `/chat/stream` | Streaming — returns tokens as Server-Sent Events |

Both accept:
```json
{
  "question": "What is the weather in Paris?",
  "conversationId": "my-session-123"
}
```
If `conversationId` is omitted, a new session is created automatically.

## Running the App

```bash
./mvnw spring-boot:run
```

The server starts on port `8080`.

To pass a custom question directly (no HTTP needed):
```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="What is the weather in London?"
```

## API Usage

### Blocking chat

```bash
curl -X POST http://localhost:8080/chat \
     -H "Content-Type: application/json" \
     -d '{"question":"What is the weather in Paris?","conversationId":"session-1"}'
```

Response:
```json
{
  "conversationId": "session-1",
  "answer": "The current weather in Paris, France is partly cloudy. Temperature: 15.6°C, Humidity: 83%, Wind: 4.1 km/h."
}
```

### Follow-up with memory

```bash
curl -X POST http://localhost:8080/chat \
     -H "Content-Type: application/json" \
     -d '{"question":"How does that compare to London?","conversationId":"session-1"}'
```

The model remembers Paris from the previous turn and fetches London to compare — no need to repeat the city.

### Streaming (SSE)

```bash
curl -X POST http://localhost:8080/chat/stream \
     -H "Content-Type: application/json" \
     -d '{"question":"What is the weather in Tokyo?","conversationId":"session-2"}' \
     --no-buffer
```

Tokens arrive as SSE events:
```
data:The
data: current
data: weather
data: in
data: Tokyo
...
```

## How the Recursive Advisor Loop Works

```
User: "What is the weather in Paris?"
        │
        ▼
  MyLogAdvisor.before()       ← logs REQUEST
        │
        ▼
  ToolCallAdvisor              ← sends to model
        │
        ▼
  Model responds: TOOL_CALL    ← finishReason=TOOL_CALLS
  {"name":"weather","args":{"city":"Paris"}}
        │
        ▼
  WeatherTools.weather("Paris") ← hits Open-Meteo API
  returns: "Partly cloudy, 15.6°C, 83%, 4.1 km/h"
        │
        ▼
  ToolCallAdvisor loops back   ← sends tool result to model
        │
        ▼
  Model responds: STOP         ← finishReason=STOP
  "The current weather in Paris is partly cloudy..."
        │
        ▼
  MyLogAdvisor.after()         ← logs RESPONSE
        │
        ▼
  Final answer printed / returned via REST
```

## Expected Console Output

```
REQUEST:[{"messageType":"USER","text":"What is current weather in Paris?"}]

RESPONSE:[{"finishReason":"TOOL_CALLS","toolCalls":[{"name":"weather","arguments":"{\"city\":\"Paris\"}"}]}]

REQUEST:[...conversation with tool result appended...]

RESPONSE:[{"finishReason":"STOP","text":"The current weather in Paris, France is partly cloudy. Temperature: 15.6°C, Humidity: 83%, Wind: 4.1 km/h."}]

=== FINAL ANSWER ===
The current weather in Paris, France is partly cloudy. Temperature: 15.6°C, Humidity: 83%, Wind: 4.1 km/h.
```
