package com.yflog.lckmgr.common;

/**
 * Created by vincent on 6/25/16.
 */
public class TimeoutException extends LockServiceException {
    public TimeoutException(Throwable e) {
        super(e);
    }

    public TimeoutException(String errMsg) {
        super(errMsg);
    }
}
