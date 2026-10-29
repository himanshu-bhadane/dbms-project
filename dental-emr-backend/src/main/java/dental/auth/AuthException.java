package dental.auth;

import dental.service.BusinessException;

/** Not logged in / bad credentials. WebServer maps this to HTTP 401. */
public class AuthException extends BusinessException {
    private static final long serialVersionUID = 1L;
    public AuthException(String msg) { super(msg); }
}