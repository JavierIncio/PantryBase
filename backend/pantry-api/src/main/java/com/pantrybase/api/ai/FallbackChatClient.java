package com.pantrybase.api.ai;

import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FallbackChatClient {

    private final List<org.springframework.ai.chat.client.ChatClient> clients;

    public FallbackChatClient(List<org.springframework.ai.chat.client.ChatClient> clients) {
        this.clients = clients;
    }

    public String callTextWithFallback(Prompt prompt) {
        var messages = prompt.getInstructions();
        for (org.springframework.ai.chat.client.ChatClient client : clients) {
            try {
                return client.prompt().messages(messages).call().content();
            } catch (Exception e) {
                // try next client
            }
        }
        throw new AiFallbackException("All AI providers failed", null);
    }

    public static class AiFallbackException extends RuntimeException {
        public AiFallbackException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}