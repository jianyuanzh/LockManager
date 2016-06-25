package com.yflog.lckmgr.common;

/**
 * Created by vincent on 6/25/16.
 */
public class TransportException extends LockServiceException {
    public TransportException() {
        super("TransportException");
    }
    public TransportException(Throwable e) {
        super(e);
    }

    public TransportException(String errMsg) {
        super(errMsg);
    }
}
