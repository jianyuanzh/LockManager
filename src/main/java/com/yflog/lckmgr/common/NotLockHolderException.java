package com.yflog.lckmgr.common;

/**
 * Created by vincent on 6/25/16.
 */
public class NotLockHolderException extends LockServiceException {
    public NotLockHolderException(String errMsg) {
        super(errMsg);
    }


}
