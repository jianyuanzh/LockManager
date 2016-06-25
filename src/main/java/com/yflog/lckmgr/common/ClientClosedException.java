package com.yflog.lckmgr.common;

/**
 * Created by vincent on 6/25/16.
 */
public class ClientClosedException extends LockServiceException {
    public ClientClosedException(Throwable e) {
        super(e);
    }

    public ClientClosedException(String errMsg) {
        super(errMsg);
    }
}
