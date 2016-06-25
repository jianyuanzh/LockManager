package com.yflog.lckmgr.common;

/**
 * Created by vincent on 5/15/16.
 */
public class LockServiceException extends RuntimeException {
    public LockServiceException(Throwable e) {
        super(e);
    }

    public LockServiceException(String errMsg) {
        super(errMsg);
    }
}
