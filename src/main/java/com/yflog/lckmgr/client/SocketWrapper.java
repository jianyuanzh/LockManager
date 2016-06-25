package com.yflog.lckmgr.client;


import javafx.util.Pair;
import com.yflog.lckmgr.msg.LSMessage;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;

/**
 * Created by vincent on 6/25/16.
 */
public class SocketWrapper {
    private static Logger _logger = Logger.getLogger("socket");
    private static final int TIMEOUT_IN_MS = 5000;

    public enum RCODE {
        OK,
        NETWORK,
        TIMEOUT
    }

    private Socket _sock = null;

    public RCODE connect(final String server, final int port) {
        try {
            _sock = new Socket();
            _sock.connect(new InetSocketAddress(server, port), TIMEOUT_IN_MS);

            _sock.setKeepAlive(true);

            return RCODE.OK;
        } catch (IOException e) {
            _closeQuietly(_sock);
            _logger.warn("Encounter exception server = " + server, e);
            return RCODE.NETWORK;
        }
    }

    public Pair<RCODE, LSMessage.Message> read() {
        try {
            return new Pair<RCODE, LSMessage.Message>(RCODE.OK, LSMessage.Message.parseDelimitedFrom(_sock.getInputStream()));
        } catch (SocketTimeoutException e) {
            return new Pair<RCODE, LSMessage.Message>(RCODE.TIMEOUT, null);
        }
        catch (Exception e) {
            _logger.warn("Encounter an exception when reading message", e);
            return new Pair<RCODE, LSMessage.Message>(RCODE.NETWORK, null);
        }
    }


    public RCODE write(final LSMessage.Message message) {
        try {
            message.writeDelimitedTo(_sock.getOutputStream());
            return RCODE.OK;
        } catch (IOException e) {
            _logger.warn("Encounter exception when write a message", e);
            return RCODE.NETWORK;
        }
    }

    public void close() {
        _closeQuietly(_sock);
        _sock = null;
    }


    private void _closeQuietly(Socket socket) {
        if (socket == null) return;
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}
