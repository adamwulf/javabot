package com.tradebits.exchange;

/**
 * The ExchangeException if any problems happen inside an exchange
 * @version 2012-08-15
 */
public class ExchangeException extends Exception {
    private static final long serialVersionUID = 0;
    private Throwable cause;

    /**
     * Constructs a JSONException with an explanatory message.
     * @param message Detail about the reason for the exception.
     */
    public ExchangeException(String message) {
        super(message);
    }

    public ExchangeException(Throwable cause) {
        super(cause.getMessage());
        this.cause = cause;
    }

    public Throwable getCause() {
        return this.cause;
    }
}
