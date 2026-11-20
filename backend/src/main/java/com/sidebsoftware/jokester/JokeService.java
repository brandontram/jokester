package com.sidebsoftware.jokester;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.FinishReasonCategory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
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
     * @throws JokeGenerationException if the model call fails, is cut off, or returns nothing usable
     */
    public String tellJoke() {
        ChatResponse response;
        try {
            response = chatClient.prompt().user(USER_PROMPT).call().chatResponse();
        } catch (RuntimeException e) {
            throw new JokeGenerationException("Model call failed", e);
        }
        Generation generation = response == null ? null : response.getResult();
        if (generation == null) {
            throw new JokeGenerationException("Model returned no result");
        }
        if (generation.getMetadata().getFinishReasonCategory() == FinishReasonCategory.TRUNCATED) {
            // A cut-off joke would look complete on screen but lack its punchline.
            throw new JokeGenerationException(
                    "Model reply was cut off (" + generation.getMetadata().getFinishReason() + ")");
        }
        String joke = clean(generation.getOutput().getText());
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
            String inner = text.substring(1, text.length() - 1);
            // Only unwrap a single quoted joke; dialogue like "A." "B." must keep its quotes.
            if (inner.indexOf('"') < 0 && inner.indexOf(LEFT_QUOTE) < 0 && inner.indexOf(RIGHT_QUOTE) < 0) {
                text = inner.strip();
            }
        }
        return text;
    }

    private static final char LEFT_QUOTE = '“';
    private static final char RIGHT_QUOTE = '”';

    private static boolean isQuotePair(char open, char close) {
        return (open == '"' && close == '"') || (open == LEFT_QUOTE && close == RIGHT_QUOTE);
    }
}
