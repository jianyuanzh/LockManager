package com.yflog.lckmgr.server.events;

import com.yflog.lckmgr.msg.LSMessage;
import io.netty.channel.Channel;

/**
 * Created by vincent on 6/25/16.
 */
public class ClientMessageEvent extends Event {
    public final Channel channel;
    public final LSMessage.Message msg;

    public ClientMessageEvent(final Channel channel, final LSMessage.Message msg) {
        this.channel = channel;
        this.msg = msg;
    }
}
