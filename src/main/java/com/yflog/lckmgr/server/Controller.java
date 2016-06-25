package com.yflog.lckmgr.server;

import com.yflog.lckmgr.common.TransportException;
import com.yflog.lckmgr.msg.LSMessage;
import com.yflog.lckmgr.server.events.*;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import org.apache.log4j.Logger;

import java.util.LinkedList;
import java.util.concurrent.*;

/**
 * Created by vincent on 6/24/16.
 */
public class Controller {


    private final static Logger _logger = Logger.getLogger("controller");

    private final ConcurrentHashMap<String/*clientId*/, Client> _clients = new ConcurrentHashMap<String, Client>();

    private final ConcurrentHashMap<String, LockItem> _locks = new ConcurrentHashMap<String, LockItem>();

    private BlockingQueue<Event> _eventQueue = new LinkedBlockingQueue<Event>();

    private final int servicePort;
    private ChannelFuture _clieChannelFuture;
    private Future<Void> _futureMainLoop = null;

    Controller(int servicePort) {
        this.servicePort = servicePort;
    }

    void shutdown() throws InterruptedException {
        if (_futureMainLoop != null) {
            _futureMainLoop.cancel(true);
            try {
                _futureMainLoop.get();
            } catch (Exception ignored) {
            }
        }

        if (_clieChannelFuture != null) {
            _clieChannelFuture.channel().close().sync();
        }
    }

    void run() {
        _eventQueue.add(new InitialEvent());

        while (true) {
            ExecutorService executorService = Executors.newSingleThreadExecutor();
            _futureMainLoop = executorService.submit(new MainLoopTask());

            try {
                _futureMainLoop.get();
                break;
            } catch (InterruptedException e) {
                _logger.warn("Interrupted, quit service now ...");
            } catch (ExecutionException e) {
                _logger.warn("Exception happened in Main loop, re-submit it", e.getCause());
            } finally {
                if (executorService != null) {
                    executorService.shutdownNow();
                }
            }
        }
    }

    private class MainLoopTask implements Callable<Void> {

        public Void call() throws Exception {
            while (true) {
                Event ev = _eventQueue.poll(60, TimeUnit.SECONDS);
                if (ev == null) {
                    // do clean up
                    onCleanup();
                } else {
                    if (ev instanceof ClientMessageEvent) {
                        onClientMessage((ClientMessageEvent) ev);
                    } else if (ev instanceof InitialEvent) {
                        initialize();
                    } else if (ev instanceof ClientHandShakeEvent) {
                        // TODO dummy now
                    } else if (ev instanceof ClientQuitEvent) {
                        onClientQuit((ClientQuitEvent) ev);
                    }
                }
            }
        }
    }

    private void onClientMessage(ClientMessageEvent cme) {
        String clientId = cme.msg.getClientId();
        Client client = null;
        // get client and create if no registered client
        {
            if (!_clients.containsKey(clientId)) {
                Client newOne = new Client(clientId);
                _clients.putIfAbsent(clientId, newOne);
            }
            client = _clients.get(clientId);
        }

        // validate the request by comparing the requestId with cached request seq
        // send resp if it equals the last reqSeq or deny request if the seq is smaller to cached one
        // response to the new req seq
        {
            final long cachedSeqId = client.lastResponse != null ? client.lastResponse.getSeqId() : -1;
            final long seqId = cme.msg.getSeqId();

            if (seqId < cachedSeqId || seqId < 0) {
                // the client is corrupted. Let's close the channel immediately
                _logger.warn(String.format("client is corrupted with a small req id, close the channel immediately - cachedSeqId=%d, seqId=%d, clientId=%s", cachedSeqId, seqId, clientId));
                cme.channel.close();
            }
            else if (seqId == cachedSeqId) {
                cme.channel.writeAndFlush(client.lastResponse);
            }
            else {
                // seqId > cachedSeqId : this is a new request
                LSMessage.Message[] msgs = _processRequest(cme.msg);
                assert msgs.length == 2;
                if (msgs[0] != null && client.channel != null) {
                    client.channel.writeAndFlush(msgs[0]);
                }

                if (msgs[1] != null) {
                    Client newOwner = _clients.get(msgs[1].getClientId());
                    if (newOwner.channel != null){
                        newOwner.channel.writeAndFlush(msgs[1]);
                    }
                }
            }
        }
    }

    private LSMessage.Message[] _processRequest(final LSMessage.Message message)  {
        switch (message.getCommand()) {
            case ACQUIRE:
                return new LSMessage.Message[]{_acquire(message), null};
            case RELEASE:
                return _release(message);
            default:
                throw new TransportException();
        }
    }

    private LSMessage.Message _acquire(LSMessage.Message message) {
        String lockname = message.getLckName();
        Client client = _clients.get(message.getClientId());
        LockItem lockItem = null;
        // get or create LockItem
        if (!_locks.containsKey(lockname)) {
            _locks.putIfAbsent(lockname, new LockItem(lockname));
        }
        lockItem = _locks.get(lockname);

        // check lock status
        LSMessage.Message.Builder mb = LSMessage.Message.newBuilder();
        mb.setClientId(client.clientId).setCommand(message.getCommand())
                .setSeqId(message.getSeqId()).setLckName(lockname);
        if (lockItem.currentOwner == null) {
            lockItem.currentOwner = client.clientId;
            client.locks.add(lockname);
            mb.setStatus(LSMessage.Status.OK);
        }
        else if (lockItem.currentOwner.equalsIgnoreCase(client.clientId)) {
            mb.setStatus(LSMessage.Status.OK);
        }
        else { // lock is held by other, wait in the queue
            lockItem.waitingList.add(client.clientId);
            client.waitForLock = lockname;
            client.lockRequestSeqId = message.getSeqId(); // client is blocked at this request seq
            _logger.info("Lock is already held by others - waiting in queue - client=%s");
            return null;
        }

        client.lastResponse = mb.build();
        _logger.info(String.format("Get the lock - client=%s, lock=%s", client.clientId, lockname));
        return client.lastResponse;
    }

    private LSMessage.Message[] _release(LSMessage.Message message) {
        String lockname = message.getLckName();
        final Client client = _clients.get(message.getClientId());
        LSMessage.Message.Builder mb = LSMessage.Message.newBuilder();
        mb.setCommand(LSMessage.Message.Command.RELEASE).setClientId(client.clientId)
                .setLckName(lockname).setSeqId(message.getSeqId());

        LSMessage.Message msg2 = null;
        _logger.info(String.format("unlock lockName=%s, client=%s, seqId=%d", lockname, client.clientId, message.getSeqId()));

        LockItem lockItem = _locks.get(lockname);
        if (lockItem == null) {
            mb.setStatus(LSMessage.Status.INVALID_LOCK);
        }
        else {
            if (lockItem.currentOwner != null && lockItem.currentOwner.equals(client.clientId)) {
                // got the owner
                if (!lockItem.waitingList.isEmpty()) {
                    // some one is waiting
                    // clean myself; assign the lock to the first waiting one
                    client.locks.remove(lockname);
                    String newOwner = lockItem.waitingList.removeFirst();
                    lockItem.currentOwner = newOwner;
                    Client newOwnerClient = _clients.get(newOwner);
                    newOwnerClient.waitForLock = null;
                    newOwnerClient.locks.add(lockname);

                    LSMessage.Message.Builder mb2 = LSMessage.Message.newBuilder()
                            .setSeqId(newOwnerClient.lockRequestSeqId).setLckName(lockname).setCommand(LSMessage.Message.Command.ACQUIRE)
                            .setStatus(LSMessage.Status.OK);
                    newOwnerClient.lastResponse = msg2 = mb2.build();
                    newOwnerClient.lockRequestSeqId = 0;

                    mb.setStatus(LSMessage.Status.OK);
                }
                else {
                    // no one is waiting
                    // delete the lock, return ok
                    _locks.remove(lockname);
                    client.locks.remove(lockname);
                    _logger.info(String.format("Delete the idle lock - lock=%s", lockname));
                    mb.setStatus(LSMessage.Status.OK);
                }
            }
            else {
                mb.setStatus(LSMessage.Status.NOT_LOCK_OWNER);
            }
        }

        client.lastResponse = mb.build();

        return new LSMessage.Message[]{client.lastResponse, msg2};
    }

    private void onClientQuit(final ClientQuitEvent cqe) {
        if (cqe.clientId != null) {
            Client cli = _clients.get(cqe.clientId);
            if (cli != null && cli.channel == cqe.channel) {
                _logger.info("Clean channel, clientId=" + cli.clientId);
                cli.channel = null;
                cli.lastDisconnectedEpochInMs = System.currentTimeMillis();
            }
        }

    }
    /**
     * Used to kill clients that did'nt establish connection to the server for a specific
     * interval.
     */
    private void onCleanup() {
        for (Client cli : _clients.values()) {
            if (cli.channel == null && System.currentTimeMillis() - cli.lastDisconnectedEpochInMs > 30000) {
                killClient(cli);
            }
        }
    }

    private void killClient(Client cli) {
        _logger.warn("Removing an idle client; client=" +  cli.clientId);
        // cancel waiting lock
        if (cli.waitForLock != null) {
            LockItem lockItem = _locks.get(cli.waitForLock);
            if (lockItem != null) {
                lockItem.waitingList.remove(cli.clientId);
                cli.waitForLock = null;
            }
        }

        LinkedList<Client> impactedClis = new LinkedList<Client>();
        // release held locks, and notify the next client
        for (String lockName : cli.locks) {
            LockItem lock = _locks.get(lockName);
            if (lock == null) // holding an un-exist lock
                continue;
            if (lock.waitingList.isEmpty()) {
                _logger.info(String.format("Remove a lock hold by idle client, lock=%s, cli=%s", lockName, cli.clientId));
                _locks.remove(lockName);
            }
            else {
                String impacted = lock.waitingList.removeFirst();
                Client client = _clients.get(impacted);
                lock.currentOwner = impacted;

                LSMessage.Message.Builder builder = LSMessage.Message.newBuilder().setClientId(impacted)
                        .setCommand(LSMessage.Message.Command.ACQUIRE)
                        .setStatus(LSMessage.Status.OK);
                client.waitForLock = null;
                client.locks.add(lockName);
                client.lastResponse = builder.build();

                _logger.info(String.format("Grant lock to a waiting client, lock=%s, client=%s", lockName, client.clientId));
                impactedClis.add(client);
            }
        }

        // remove client from clients
        _clients.remove(cli.clientId);

        // send response to all impacted clients
        for (Client impactedCli : impactedClis) {
            if (impactedCli.channel != null) {
                impactedCli.channel.writeAndFlush(impactedCli.lastResponse);
            }
        }
    }

    private void initialize() {
        _logger.info("Start, port=" + servicePort);

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 1024)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ch.pipeline()
                                    // inbound
                                    .addLast(new ProtobufVarint32FrameDecoder())
                                    .addLast(new ProtobufDecoder(LSMessage.Message.getDefaultInstance()))
                                    .addLast("business", new MyHandler())
                                    // out bound
                                    .addLast(new ProtobufVarint32LengthFieldPrepender())
                                    .addLast(new ProtobufEncoder());
                        }
                    });
            _clieChannelFuture = bootstrap.bind(servicePort).sync();
        } catch (InterruptedException e) {
            _logger.error("Encounter exception when initializing netty, quit", e);
            System.exit(1);
        }

        _logger.info("Done");
    }

    class MyHandler extends ChannelInboundHandlerAdapter {
        String clientId = null;

        /**
         * Generate ClientDisconnectedEvent
         */
        @Override
        public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
            _logger.warn("a channel unregistered, clientId=" + clientId);
            _eventQueue.put(new ClientQuitEvent(ctx.channel(), clientId));
        }

        /**
         * Generate ClientMessageEvent or ClientShakeHandEvent
         * @throws Exception
         */
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            LSMessage.Message message = (LSMessage.Message) msg;
            _logger.info(String.format("Get a message, clientId=%s, command=%s, seq=%d",
                    message.getClientId(), message.getCommand(), message.getSeqId()));
            // TODO no handshake now
            _eventQueue.put(new ClientMessageEvent(ctx.channel(), message));
            super.channelRead(ctx, msg);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            _logger.warn("encounter an exception, clientId=" + clientId);
            ctx.close();
        }
    }

}
