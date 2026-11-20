package com.sidebsoftware.jokester;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class JokeController {

    private static final Logger log = LoggerFactory.getLogger(JokeController.class);
    static final String ERROR_MESSAGE = "Couldn't come up with a joke right now. Please try again.";

    record JokeResponse(String joke) {}

    record ErrorResponse(String error) {}

    private final JokeService jokeService;

    public JokeController(JokeService jokeService) {
        this.jokeService = jokeService;
    }

    @GetMapping("/api/joke")
    public JokeResponse joke() {
        return new JokeResponse(jokeService.tellJoke());
    }

    @ExceptionHandler(JokeGenerationException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public ErrorResponse handleGenerationFailure(JokeGenerationException e) {
        log.warn("Joke generation failed", e);
        return new ErrorResponse(ERROR_MESSAGE);
    }
}
