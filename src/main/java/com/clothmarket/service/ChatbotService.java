package com.clothmarket.service;

import com.clothmarket.dto.ChatbotRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.clothmarket.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class ChatbotService {

    @Value("${groq.api.key}")
    private String apiKey;

    @Value("${groq.api.model}")
    private String model;

    @Value("${groq.api.url}")
    private String apiUrl;

    private static final Logger log = LoggerFactory.getLogger(ChatbotService.class);

    private static final int MAX_MESSAGE_CHARS = 1000;
    private static final int MAX_HISTORY_TURNS = 10;
    private static final int MAX_HISTORY_CHARS = 2000;

    private final RestTemplate restTemplate = buildRestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static RestTemplate buildRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(20000);
        return new RestTemplate(factory);
    }

    private static final String SYSTEM_PROMPT = """
            Tum "Cloth Marketplace" app ke liye ek helpful assistant ho.
            Tumhara kaam sirf DO cheezon me madad karna hai:

            1. WEBSITE KAISE USE KAREIN:
               - Signup: name, email, mobile number aur password se account banta hai; email par aaye 6-digit OTP se email verify karna hota hai
               - Post banane ke liye: "Create Post" pe jaakar image/video, price, quantity daalo
               - Chat: kisi bhi user ki profile pe jaakar "Message" button se text/voice chat kar sakte ho
               - Call: chat screen me phone/video icon se voice ya video call kar sakte ho
               - Profile: apni profile image "Profile" page se update kar sakte ho

            2. TEXTILE/CLOTH INDUSTRY KNOWLEDGE:
               - Fabric types (cotton, polyester, silk, linen, wool, etc.), unke pros/cons
               - Quality check tips (fabric ki quality kaise pehchane)
               - Basic pricing/costing guidance for cloth business
               - Fabric care instructions (washing, storage)

            RULES:
            - Agar koi sawal in dono topics se bilkul related nahi hai (jaise movies,
              politics, coding, general knowledge), to politely mana karo:
              "Main sirf website aur textile se related sawalon me help kar sakta hoon."
            - Jawab hamesha short aur clear rakho (max 3-4 lines jab tak zaroorat na ho)
            - Hindi/English jis bhasha me user poochhe, usi me jawab do
            """;

    public String getReply(ChatbotRequest request) {
        if (request == null || request.getMessage() == null || request.getMessage().isBlank()) {
            throw ApiException.badRequest("Message is required");
        }
        if (request.getMessage().length() > MAX_MESSAGE_CHARS) {
            throw ApiException.badRequest("Message is too long");
        }
        if (apiKey == null || apiKey.isBlank()) {
            return "Chatbot abhi configure nahi hua hai. Admin se contact karein.";
        }

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("max_tokens", 500);

        ArrayNode messages = body.putArray("messages");

        // Groq (OpenAI format) me system prompt bhi messages array ke andar
        // "system" role ke saath jaata hai (Anthropic ki tarah alag field nahi hota)
        ObjectNode systemMsg = messages.addObject();
        systemMsg.put("role", "system");
        systemMsg.put("content", SYSTEM_PROMPT);

        // History comes from the client, so it is untrusted: only "user"/"assistant"
        // roles (a client must not be able to inject its own "system" prompt),
        // bounded in count and length.
        if (request.getHistory() != null) {
            var history = request.getHistory();
            int from = Math.max(0, history.size() - MAX_HISTORY_TURNS);
            for (ChatbotRequest.ChatTurn turn : history.subList(from, history.size())) {
                if (turn == null || turn.getContent() == null) {
                    continue;
                }
                String role = turn.getRole();
                if (!"user".equals(role) && !"assistant".equals(role)) {
                    continue;
                }
                String content = turn.getContent();
                if (content.length() > MAX_HISTORY_CHARS) {
                    content = content.substring(0, MAX_HISTORY_CHARS);
                }
                ObjectNode msgNode = messages.addObject();
                msgNode.put("role", role);
                msgNode.put("content", content);
            }
        }

        ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");
        userMsg.put("content", request.getMessage());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        HttpEntity<String> entity = new HttpEntity<>(body.toString(), headers);

        try {
            JsonNode response = restTemplate.postForObject(apiUrl, entity, JsonNode.class);
            if (response == null) {
                throw new IllegalStateException("Empty chatbot response");
            }
            return response.get("choices").get(0).get("message").get("content").asText();
        } catch (Exception e) {
            log.warn("Chatbot API error: {}", e.getClass().getSimpleName());
            return "Sorry, abhi jawab nahi de pa raha. Thodi der baad try karein.";
        }
    }
}