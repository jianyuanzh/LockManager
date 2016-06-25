package com.yflog.lckmgr.server.events;

import com.yflog.lckmgr.msg.LSMessage;
import io.netty.channel.Channel;

/**
 * Created by vincent on 6/25/16.
 */
public class ClientHandShakeEvent extends Event{
    final Channel channel;
    final LSMessage.Message msg;

    public ClientHandShakeEvent(Channel channel, LSMessage.Message msg) {
        this.channel = channel;
        this.msg = msg;
    }
}
