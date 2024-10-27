package de.leidenheit.core.exception;

public class ItarazzoIllegalStateException  extends RuntimeException {

    public ItarazzoIllegalStateException() {
        super();
    }

    public ItarazzoIllegalStateException(final String message) {
        super(message);
    }

    public ItarazzoIllegalStateException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public ItarazzoIllegalStateException(final Throwable cause) {
        super(cause);
    }
}
