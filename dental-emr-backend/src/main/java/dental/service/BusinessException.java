package dental.service;

/** Rule violation or translated SQL error - message is safe to show to the user. */
public class BusinessException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public BusinessException(String msg) { super(msg); }
}
