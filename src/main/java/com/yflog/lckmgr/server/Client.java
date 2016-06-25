package com.yflog.lckmgr.server;

import com.yflog.lckmgr.msg.LSMessage;
import io.netty.channel.Channel;

import java.util.HashSet;
import java.util.Set;

/**
 * Created by vincent on 6/25/16.
 */
public class Client {
    final String clientId;
    Channel channel = null;
    long lastDisconnectedEpochInMs = 0;

    /**
     * List of locks hold by this client
     */
    final Set<String> locks = new HashSet<String>();

    /**
     * The client is waiting for this lock
     */
    String waitForLock = null;

    /**
     * If the client is blocked for waiting for a lock, we remember the request's seqId to
     * build the response when it gets the lock
     */
    long lockRequestSeqId = 0;

    /**
     * A client can have at most one outstanding rpc. We always cache the
     * last RPC response
     */
    LSMessage.Message lastResponse = null;

    public Client(String clientId) {
        this(clientId, null);
    }

    public Client(String clientId, Channel channel) {
        this.clientId = clientId;
        this.channel = channel;
    }
}
