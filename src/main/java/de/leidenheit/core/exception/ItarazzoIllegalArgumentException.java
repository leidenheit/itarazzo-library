package de.leidenheit.core.exception;

public class ItarazzoIllegalArgumentException extends RuntimeException {

    public ItarazzoIllegalArgumentException() {
        super();
    }

    public ItarazzoIllegalArgumentException(final String message) {
        super(message);
    }

    public ItarazzoIllegalArgumentException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public ItarazzoIllegalArgumentException(final Throwable cause) {
        super(cause);
    }
}
