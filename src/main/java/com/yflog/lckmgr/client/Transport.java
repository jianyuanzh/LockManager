package com.yflog.lckmgr.client;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.yflog.lckmgr.common.TimeoutException;
import com.yflog.lckmgr.common.TransportException;
import com.yflog.lckmgr.msg.LSMessage;
import org.apache.log4j.Logger;
import javafx.util.Pair;


/**
 * Created by vincent on 6/25/16.
 * State machie:
 * _____________________________________
 * |                                     |
 * |                                     V
 * disconnected -------> connected -------> closed
 * ^                   |
 * |-------------------|
 */
class Transport {
    private static final Logger _logger = Logger.getLogger("Tranport");

    enum STATE {
        DISCONNECTED, // NO CONNECTION
        CONNECTED,    // connected to _server
        CLOSED          // transport is shut down
    }

    public static final int PORT = 8888;

    private STATE _state = STATE.DISCONNECTED;
    private SocketWrapper _socket = null;

    /**
     * IP of _server
     */
    private final String _server;
    private final String _clientId;

    public Transport(String server, String clientId) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(server));
        this._server = server;
        this._clientId = clientId;
    }

    public void write(final LSMessage.Message message) {
        Preconditions.checkState(_state != STATE.CLOSED);
        Preconditions.checkNotNull(message);

        _connectIfDisconnected();

        if (_socket.write(message) != SocketWrapper.RCODE.OK) {
            throw new TransportException("Failed to write message");
        }
    }

    public LSMessage.Message read() {
        Preconditions.checkState(_state != STATE.CLOSED);
        _connectIfDisconnected();

        Pair<SocketWrapper.RCODE, LSMessage.Message> rsp = _socket.read();
        switch (rsp.getKey()) {
            case OK:
                return rsp.getValue();
            case TIMEOUT:
                throw new TimeoutException("Timed out");
            default:
                throw new TransportException("Read failed");

        }
    }

    public void shutDown() {
        if (_socket != null) {
            _socket.close();
            _socket = null;
        }

        _state = STATE.CLOSED;
    }

    private void _connectIfDisconnected() {
        switch (_state) {
            case CONNECTED:
                return;
            case CLOSED:
                throw new TransportException("Transport connection closed");
            case DISCONNECTED:
                _connect();
        }
    }

    private void _connect() {
        for (int i = 0; i < 3; i++) {
            SocketWrapper socket = new SocketWrapper();
            SocketWrapper.RCODE code = socket.connect(_server, PORT);
            if (code != SocketWrapper.RCODE.OK) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            else {
                this._socket = socket;
                _state = STATE.CONNECTED;
                return;
            }
        }
        _state = STATE.CLOSED;
        throw new TransportException("Connect failed");
    }

}
