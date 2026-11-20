package com.sidebsoftware.jokester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.IntUnaryOperator;
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

    /** User prompt the service sends when the topic picker returns index 0. */
    private static String firstTopicPrompt() {
        return promptFor(0);
    }

    private static String promptFor(int index) {
        return "Tell me a joke about " + JokeService.TOPICS.get(index) + ".";
    }

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        builder = mock(ChatClient.Builder.class);
        when(builder.defaultSystem(anyString())).thenReturn(builder);
        when(builder.build()).thenReturn(chatClient);
        service = new JokeService(builder, size -> 0);
    }

    private void modelReplies(String content) {
        modelReplies(content, "end_turn", FinishReasonCategory.COMPLETED);
    }

    private void modelReplies(String content, String finishReason, FinishReasonCategory category) {
        ChatGenerationMetadata metadata = ChatGenerationMetadata.builder()
                .finishReason(finishReason)
                .finishReasonCategory(category)
                .build();
        when(chatClient.prompt().user(firstTopicPrompt()).call().chatResponse()).thenReturn(response(content, metadata));
    }

    private static ChatResponse response(String content, ChatGenerationMetadata metadata) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(content), metadata)));
    }

    private static ChatResponse completed(String content) {
        return response(content, ChatGenerationMetadata.builder()
                .finishReason("end_turn")
                .finishReasonCategory(FinishReasonCategory.COMPLETED)
                .build());
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
        when(chatClient.prompt().user(firstTopicPrompt()).call().chatResponse()).thenReturn(null);
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
        when(chatClient.prompt().user(firstTopicPrompt()).call().chatResponse()).thenThrow(boom);
        JokeGenerationException ex = assertThrows(JokeGenerationException.class, service::tellJoke);
        assertThat(ex).hasCause(boom);
    }

    @Test
    void asksForAJokeAboutThePickedTopic() {
        service = new JokeService(builder, size -> 3);
        when(chatClient.prompt().user(promptFor(3)).call().chatResponse()).thenReturn(completed("A topical joke."));
        assertThat(service.tellJoke()).isEqualTo("A topical joke.");
    }

    @Test
    void picksANewTopicOnEveryCall() {
        List<Integer> picks = new ArrayList<>(List.of(1, 2));
        List<Integer> sizesSeen = new ArrayList<>();
        IntUnaryOperator picker = size -> {
            sizesSeen.add(size);
            return picks.remove(0);
        };
        service = new JokeService(builder, picker);
        when(chatClient.prompt().user(promptFor(1)).call().chatResponse()).thenReturn(completed("Joke one."));
        when(chatClient.prompt().user(promptFor(2)).call().chatResponse()).thenReturn(completed("Joke two."));

        assertThat(service.tellJoke()).isEqualTo("Joke one.");
        assertThat(service.tellJoke()).isEqualTo("Joke two.");
        assertThat(sizesSeen).containsExactly(JokeService.TOPICS.size(), JokeService.TOPICS.size());
    }

    @Test
    void topicsAreDistinctAndNonBlank() {
        assertThat(JokeService.TOPICS).hasSizeGreaterThanOrEqualTo(25);
        assertThat(JokeService.TOPICS).allSatisfy(topic -> assertThat(topic).isNotBlank());
        assertThat(new HashSet<>(JokeService.TOPICS)).hasSameSizeAs(JokeService.TOPICS);
    }

    @Test
    void usesExactSystemPrompt() {
        verify(builder).defaultSystem(SYSTEM_PROMPT);
    }
}
