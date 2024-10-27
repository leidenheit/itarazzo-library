package de.leidenheit.core.exception;

public class ItarazzoInterruptException extends RuntimeException {

    public ItarazzoInterruptException() {
        super();
    }

    public ItarazzoInterruptException(final String message) {
        super(message);
    }

    public ItarazzoInterruptException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public ItarazzoInterruptException(final Throwable cause) {
        super(cause);
    }
}
