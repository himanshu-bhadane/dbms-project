package dental.auth;

import dental.service.BusinessException;

/** Logged in, but not allowed to do this. WebServer maps it to HTTP 403. */
public class ForbiddenException extends BusinessException {
    private static final long serialVersionUID = 1L;
    public ForbiddenException(String msg) { super(msg); }
}