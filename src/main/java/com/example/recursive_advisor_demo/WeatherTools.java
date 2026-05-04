package com.example.recursive_advisor_demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Uses java.net.http.HttpClient (JDK 11+) — fully blocking, no reactive
 * restrictions, safe to call from any thread including Netty I/O threads.
 */
@Component
public class WeatherTools {

    private final String geocodingUrl;
    private final String forecastUrl;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WeatherTools(
            @Value("${weather.geocoding.url}") String geocodingUrl,
            @Value("${weather.forecast.url}") String forecastUrl) {
        this.geocodingUrl = geocodingUrl;
        this.forecastUrl = forecastUrl;
    }

    @Tool(description = "Get the current weather for a given city name")
    public String weather(String city) {
        try {
            // Step 1: Geocode city name → lat/lon
            String encodedCity = URLEncoder.encode(city, StandardCharsets.UTF_8);
            String geoUri = geocodingUrl + "?name=" + encodedCity + "&count=1&language=en&format=json";

            String geoJson = get(geoUri);
            Map<?, ?> geoResponse = objectMapper.readValue(geoJson, Map.class);

            List<?> results = (List<?>) geoResponse.get("results");
            if (results == null || results.isEmpty()) {
                return "Could not find location: " + city;
            }

            Map<?, ?> location  = (Map<?, ?>) results.get(0);
            double lat          = ((Number) location.get("latitude")).doubleValue();
            double lon          = ((Number) location.get("longitude")).doubleValue();
            String resolvedName = (String) location.get("name");
            String country      = (String) location.get("country");

            // Step 2: Fetch current weather using lat/lon
            String weatherUri = forecastUrl
                    + "?latitude=" + lat
                    + "&longitude=" + lon
                    + "&current=temperature_2m,relative_humidity_2m,wind_speed_10m,weather_code"
                    + "&timezone=auto";

            String weatherJson = get(weatherUri);
            Map<?, ?> weatherResponse = objectMapper.readValue(weatherJson, Map.class);

            Map<?, ?> current = (Map<?, ?>) weatherResponse.get("current");
            double temp     = ((Number) current.get("temperature_2m")).doubleValue();
            double humidity = ((Number) current.get("relative_humidity_2m")).doubleValue();
            double wind     = ((Number) current.get("wind_speed_10m")).doubleValue();
            int code        = ((Number) current.get("weather_code")).intValue();
            String units    = (String) ((Map<?, ?>) weatherResponse.get("current_units")).get("temperature_2m");

            return String.format(
                    "Weather in %s, %s: %s. Temperature: %.1f%s, Humidity: %.0f%%, Wind: %.1f km/h",
                    resolvedName, country, describeWeatherCode(code), temp, units, humidity, wind);

        } catch (Exception e) {
            return "Failed to fetch weather for " + city + ": " + e.getMessage();
        }
    }

    private String get(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body();
    }

    private String describeWeatherCode(int code) {
        if (code == 0)  return "Clear sky";
        if (code <= 3)  return "Partly cloudy";
        if (code <= 9)  return "Foggy";
        if (code <= 29) return "Rain";
        if (code <= 39) return "Snow";
        if (code <= 59) return "Drizzle";
        if (code <= 69) return "Rain";
        if (code <= 79) return "Snow";
        if (code <= 84) return "Rain showers";
        if (code <= 94) return "Thunderstorm";
        return "Thunderstorm with hail";
    }
}
