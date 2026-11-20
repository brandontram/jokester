package com.sidebsoftware.jokester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.FinishReasonCategory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

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
        modelReplies(content, "end_turn", FinishReasonCategory.COMPLETED);
    }

    private void modelReplies(String content, String finishReason, FinishReasonCategory category) {
        ChatGenerationMetadata metadata = ChatGenerationMetadata.builder()
                .finishReason(finishReason)
                .finishReasonCategory(category)
                .build();
        ChatResponse response = new ChatResponse(List.of(new Generation(new AssistantMessage(content), metadata)));
        when(chatClient.prompt().user("Tell me a joke.").call().chatResponse()).thenReturn(response);
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
    void nullContentThrows() {
        modelReplies(null);
        assertThrows(JokeGenerationException.class, service::tellJoke);
    }

    @Test
    void nullChatResponseThrows() {
        when(chatClient.prompt().user("Tell me a joke.").call().chatResponse()).thenReturn(null);
        assertThrows(JokeGenerationException.class, service::tellJoke);
    }

    @Test
    void keepsQuotesInDialogueJoke() {
        String joke = "\"Doctor, I feel like a deck of cards.\" \"Sit down, I'll deal with you later.\"";
        modelReplies(joke);
        assertThat(service.tellJoke()).isEqualTo(joke);
    }

    @Test
    void keepsCurlyQuotesInDialogueJoke() {
        String joke = "\u201CDoctor, I feel like a deck of cards.\u201D \u201CSit down, I'll deal with you later.\u201D";
        modelReplies(joke);
        assertThat(service.tellJoke()).isEqualTo(joke);
    }

    @Test
    void truncatedReplyThrows() {
        modelReplies("Why did the chicken cross the", "max_tokens", FinishReasonCategory.TRUNCATED);
        assertThrows(JokeGenerationException.class, service::tellJoke);
    }

    @Test
    void modelFailureIsWrapped() {
        RuntimeException boom = new RuntimeException("API down");
        when(chatClient.prompt().user("Tell me a joke.").call().chatResponse()).thenThrow(boom);
        JokeGenerationException ex = assertThrows(JokeGenerationException.class, service::tellJoke);
        assertThat(ex).hasCause(boom);
    }

    @Test
    void usesExactSystemPrompt() {
        verify(builder).defaultSystem(SYSTEM_PROMPT);
    }
}
