package com.yflog.lckmgr.server.events;

import io.netty.channel.Channel;

/**
 * Created by vincent on 6/25/16.
 */
public class ClientQuitEvent  extends Event {
    public final Channel channel;
    public final String clientId;

    public ClientQuitEvent(Channel channel, String clientId) {
        this.channel = channel;
        this.clientId = clientId;
    }
}