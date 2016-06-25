package com.yflog.lckmgr.client;

import com.yflog.lckmgr.common.ClientClosedException;
import com.yflog.lckmgr.common.NotLockHolderException;
import com.yflog.lckmgr.msg.LSMessage;

/**
 * Created by vincent on 6/25/16.
 */
class LockService {
    private final MessageDelivery _delivery;
    private final String _clientId;

    public LockService(MessageDelivery delivery, String clientId) {
        this._delivery = delivery;
        this._clientId = clientId;
    }

    public void acquire(final String lockName) {
        LSMessage.Message.Builder mb = LSMessage.Message.newBuilder();
        mb.setClientId(_clientId).setCommand(LSMessage.Message.Command.ACQUIRE)
                .setLckName(lockName);

        LSMessage.Message message = _delivery.call(mb);
        if (message.getStatus() != LSMessage.Status.OK) {
            throw new ClientClosedException("Already closed");
        }
    }

    public void release(final String lockName) {
        // build the message
        LSMessage.Message.Builder builder = LSMessage.Message.newBuilder();
        builder.setClientId(_clientId).setCommand(LSMessage.Message.Command.RELEASE).setLckName(lockName);

        // send the message and get the response
        LSMessage.Message rsp = _delivery.call(builder);
        switch (rsp.getStatus()) {
            case NOT_LOCK_OWNER:
                throw new NotLockHolderException(String.format("%s is not holding %s", _clientId, lockName));
            case OK:
                return;
            default:
                throw new ClientClosedException("closed");
        }
    }

    public void shutdown() {
        _delivery.shutDown();
    }
}
