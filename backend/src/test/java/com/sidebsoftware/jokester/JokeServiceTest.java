package com.sidebsoftware.jokester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

class JokeServiceTest {

    private static final String SYSTEM_PROMPT = "You are a comedian. Reply with exactly one short, clean, "
            + "family-friendly joke and nothing else: no preamble, no explanation, no surrounding quotation marks.";

    private ChatClient.Builder builder;
    private ChatClient chatClient;
    private JokeService service;

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        builder = mock(ChatClient.Builder.class);
        when(builder.defaultSystem(anyString())).thenReturn(builder);
        when(builder.build()).thenReturn(chatClient);
        service = new JokeService(builder);
    }

    private void modelReplies(String content) {
        when(chatClient.prompt().user("Tell me a joke.").call().content()).thenReturn(content);
    }

    @Test
    void returnsJokeFromModel() {
        modelReplies("Why did the chicken cross the road? To get to the other side.");
        assertThat(service.tellJoke()).isEqualTo("Why did the chicken cross the road? To get to the other side.");
    }

    @Test
    void trimsWhitespace() {
        modelReplies("  A joke.\n");
        assertThat(service.tellJoke()).isEqualTo("A joke.");
    }

    @Test
    void stripsSurroundingStraightQuotes() {
        modelReplies("\"A joke.\"");
        assertThat(service.tellJoke()).isEqualTo("A joke.");
    }

    @Test
    void stripsSurroundingCurlyQuotes() {
        modelReplies(" “A joke.” ");
        assertThat(service.tellJoke()).isEqualTo("A joke.");
    }

    @Test
    void blankResponseThrows() {
        modelReplies("   ");
        assertThrows(JokeGenerationException.class, service::tellJoke);
    }

    @Test
    void quotesOnlyResponseThrows() {
        modelReplies("\"\"");
        assertThrows(JokeGenerationException.class, service::tellJoke);
    }

    @Test
    void nullResponseThrows() {
        modelReplies(null);
        assertThrows(JokeGenerationException.class, service::tellJoke);
    }

    @Test
    void modelFailureIsWrapped() {
        RuntimeException boom = new RuntimeException("API down");
        when(chatClient.prompt().user("Tell me a joke.").call().content()).thenThrow(boom);
        JokeGenerationException ex = assertThrows(JokeGenerationException.class, service::tellJoke);
        assertThat(ex).hasCause(boom);
    }

    @Test
    void usesExactSystemPrompt() {
        verify(builder).defaultSystem(SYSTEM_PROMPT);
    }
}
