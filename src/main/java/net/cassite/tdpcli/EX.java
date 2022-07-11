package net.cassite.tdpcli;

public class EX extends RuntimeException {
    public EX(String message) {
        super(message);
    }

    public EX(String message, Throwable cause) {
        super(message, cause);
    }
}
