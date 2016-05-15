package com.yflog.lckmgr.server;

/**
 * Created by vincent on 5/15/16.
 * interface of LockService
 */
public interface LockService {
    String lock(final String lockName);

    void tryLock(final String lockName);

    String unlock(final String lockName, final String callerId);
}
