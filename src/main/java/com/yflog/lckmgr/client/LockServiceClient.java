package com.yflog.lckmgr.client;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.yflog.lckmgr.common.ClientClosedException;
import com.yflog.lckmgr.common.LockServiceException;
import com.yflog.lckmgr.common.NotLockHolderException;

/**
 * Created by vincent on 6/25/16.
 */
public class LockServiceClient {
    private volatile boolean _closed = false;

    private final String _id;
    private final LockService _lockService;

    LockServiceClient(String clientId, LockService lockService) {
        Preconditions.checkNotNull(clientId);
        this._id = clientId;
        this._lockService = lockService;
    }

    public synchronized void acquire(final String lockName) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(lockName));
        Preconditions.checkState(!_closed);

        try {
            _lockService.acquire(lockName);
        }
        catch (LockServiceException lse) {
            shutdown();
            throw new ClientClosedException("closed");
        }
    }

    public synchronized void release(final String lockname) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(lockname));
        Preconditions.checkState(!_closed);

        try {
            _lockService.acquire(lockname);
        }
        catch (NotLockHolderException nlhe) {
            throw nlhe;
        }
        catch (LockServiceException lse) {
            shutdown();
            throw new ClientClosedException("closed");
        }
    }

    public void shutdown() {
        _lockService.shutdown();
        _closed = true;
    }

}
