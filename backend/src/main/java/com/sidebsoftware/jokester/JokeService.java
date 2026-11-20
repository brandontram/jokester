package com.sidebsoftware.jokester;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/** Asks the configured chat model for a fresh joke. */
@Service
public class JokeService {

    static final String SYSTEM_PROMPT = "You are a comedian. Reply with exactly one short, clean, "
            + "family-friendly joke and nothing else: no preamble, no explanation, no surrounding quotation marks.";
    static final String USER_PROMPT = "Tell me a joke.";

    private final ChatClient chatClient;

    public JokeService(ChatClient.Builder builder) {
        this.chatClient = builder.defaultSystem(SYSTEM_PROMPT).build();
    }

    /**
     * @return the joke text, trimmed and without surrounding quotes
     * @throws JokeGenerationException if the model call fails or returns nothing usable
     */
    public String tellJoke() {
        String content;
        try {
            content = chatClient.prompt().user(USER_PROMPT).call().content();
        } catch (RuntimeException e) {
            throw new JokeGenerationException("Model call failed", e);
        }
        String joke = clean(content);
        if (joke.isEmpty()) {
            throw new JokeGenerationException("Model returned an empty joke");
        }
        return joke;
    }

    private static String clean(String content) {
        if (content == null) {
            return "";
        }
        String text = content.strip();
        if (text.length() >= 2 && isQuotePair(text.charAt(0), text.charAt(text.length() - 1))) {
            text = text.substring(1, text.length() - 1).strip();
        }
        return text;
    }

    private static boolean isQuotePair(char open, char close) {
        return (open == '"' && close == '"') || (open == '“' && close == '”');
    }
}
