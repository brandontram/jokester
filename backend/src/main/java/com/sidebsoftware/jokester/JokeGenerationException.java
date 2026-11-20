package com.sidebsoftware.jokester;

/** Thrown when the model fails to produce a usable joke. */
public class JokeGenerationException extends RuntimeException {

    public JokeGenerationException(String message) {
        super(message);
    }

    public JokeGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
