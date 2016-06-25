package com.yflog.lckmgr.client;

import com.yflog.lckmgr.common.TimeoutException;
import com.yflog.lckmgr.common.TransportException;
import com.yflog.lckmgr.msg.LSMessage;
import org.apache.log4j.Logger;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by vincent on 6/25/16.
 */
class MessageDelivery {
    private static final Logger _logger = Logger.getLogger("MsgDeliver");

    private final AtomicLong _seq = new AtomicLong(1);

    private final Transport _transport;

    MessageDelivery(final Transport transport) {
        this._transport = transport;
    }

    LSMessage.Message call(final LSMessage.Message.Builder mBuilder) {
        try {
            mBuilder.setSeqId(_seq.getAndIncrement());
            LSMessage.Message msg = mBuilder.build();

            while (true) {
                _transport.write(msg); /// TransportException if failed to write message

                try {

                }
                catch (TimeoutException te) {
                    // the only cause of timeout is that the service does not get the request
                    // so we should retry
                    _logger.warn("TimeoutException");
                    continue;
                }
            }
        }
        catch (TransportException te) {
            throw te;
        }
    }

    private LSMessage.Message _readWithMatchedSeq() {
        while (true) {
            LSMessage.Message ms = _transport.read();
            if (ms.getSeqId() == _seq.longValue() -1) {
                return ms;
            }
            _logger.warn(String.format("Read an obsolete message seqId=%d, command=%s", ms.getSeqId(), ms.getCommand()));
        }
    }

    void shutDown() {
        _transport.shutDown();
    }
}
