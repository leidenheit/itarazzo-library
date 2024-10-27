package de.leidenheit.core.exception;

public class ItarazzoUnsupportedException extends RuntimeException {

    public ItarazzoUnsupportedException() {
        super();
    }

    public ItarazzoUnsupportedException(final String message) {
        super(message);
    }

    public ItarazzoUnsupportedException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public ItarazzoUnsupportedException(final Throwable cause) {
        super(cause);
    }
}
