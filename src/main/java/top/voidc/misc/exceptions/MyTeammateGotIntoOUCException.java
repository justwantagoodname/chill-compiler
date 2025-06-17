package top.voidc.misc.exceptions;

import top.voidc.misc.annotation.ThankAndWorshipSam;

/**
 * This exception is thrown when a teammate gets into OUC.
 * It is a runtime exception and should be used to indicate
 * that a teammate has successfully entered OUC.
 * This exception is not meant to be caught or handled,
 * but rather to be thrown as a signal of success.
 * @see RuntimeException
 * @see Exception
 * @see Throwable
 * @author SamBillon
 */
@ThankAndWorshipSam
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
