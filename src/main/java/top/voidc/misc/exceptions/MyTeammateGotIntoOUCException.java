package top.voidc.misc.exceptions;

public class MyTeammateGotIntoOUCException extends RuntimeException {
    public MyTeammateGotIntoOUCException() {
        super("My teammate got into OUC. Congratulations!");
    }

    public MyTeammateGotIntoOUCException(String message) {
        super(message);
    }

    public MyTeammateGotIntoOUCException(String message, Throwable cause) {
        super(message, cause);
    }

    public MyTeammateGotIntoOUCException(Throwable cause) {
        super(cause);
    }
}
