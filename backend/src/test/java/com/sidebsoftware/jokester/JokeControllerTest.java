package com.sidebsoftware.jokester;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(JokeController.class)
class JokeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JokeService jokeService;

    @Test
    void returnsJoke() throws Exception {
        when(jokeService.tellJoke()).thenReturn("A joke.");

        mockMvc.perform(get("/api/joke"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.joke").value("A joke."));
    }

    @Test
    void generationFailureIs502() throws Exception {
        when(jokeService.tellJoke()).thenThrow(new JokeGenerationException("Model returned an empty joke"));

        mockMvc.perform(get("/api/joke"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("Couldn't come up with a joke right now. Please try again."));
    }
}
