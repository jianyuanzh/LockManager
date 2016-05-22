package com.yflog.lckmgr.server;


import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.yflog.lckmgr.common.HttpMessages;
import com.yflog.lckmgr.common.LockServiceException;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Created by vincent on 5/15/16.
 */
public class LockServiceManager implements LockService {

    private static Logger _logger = Logger.getLogger(LockServiceManager.class);

    private static final String _fmtter = "{\"status\":%d, \"errMsg\":\"%s\", \"session_id\": \"%s\"}";

    // --- singleton ----
    private static LockServiceManager _INSTANCE = new LockServiceManager();

    private LockServiceManager() {
    }

    public static LockServiceManager getInstance() {
        return _INSTANCE;
    }

    private final Object _addLockObject = new Object();

    // --- private members

    // for HttpServer
    private int _port = 2946;
    private volatile boolean _started = false;
    private HttpServer _server = null;

    private static int _LOCK_EXPIRATION_TIME_SECOND = 30;
    private static int _MAX_LOCK_NUMBER = 10000;


    // caches
    private Cache<String, String> _lockCache;
    private Map<String/*lockName*/, String/*Holder*/> _lockHolderMap;


    public void start() throws LockServiceException {

        _lockHolderMap = new HashMap<String, String>();
        _lockCache = CacheBuilder.newBuilder()
                .expireAfterWrite(_LOCK_EXPIRATION_TIME_SECOND, TimeUnit.SECONDS)
                .maximumSize(_MAX_LOCK_NUMBER)
                .build();

        try {
            _server = HttpServer.create(new InetSocketAddress("0.0.0.0", _port), 1);
            _server.createContext("/", new MyHttpHandler());
            _server.setExecutor(null);
            _server.start();
        }
        catch (IOException e) {
            _logger.warn(String.format("Failed to create server - %s", e.getMessage()), e);
            throw new LockServiceException(e);
        }
    }

    public void stop() {
        if (_server != null) {
            try {
                _server.stop(0);
            }
            catch (Exception e) {
            }
        }

        _lockCache.cleanUp();
        _lockHolderMap.clear();;

    }


    // --- private helpers
    private void _initHttpServer() throws LockServiceException {
        try {
            _server = HttpServer.create(new InetSocketAddress(_port), 1);
            _server.createContext("lockService", new HttpHandler() {
                public void handle(HttpExchange httpExchange) throws IOException {
                }
            });
        }
        catch (IOException e) {
            throw new LockServiceException(e);
        }
    }

    public String lock(String lockName) {
        return _lock(lockName);
    }

    // process can be slow up, if many caller try to lock ...
    private String _lock(final String lockName) {
        String newSession = UUID.randomUUID().toString();
        String oldSession = _lockCache.asMap().putIfAbsent(lockName, newSession);
        if (oldSession != null) {
            return String.format(_fmtter, 100, "Lock exists - " + lockName, " ");
        }
        else {
            return String.format(_fmtter, 200, " ", newSession);
        }
    }

    public void tryLock(String lockName) {

    }

    public String unlock(final String lockName, final String callerId) {
        if (!callerId.equals(LockServiceManager.getInstance().getHolder(lockName))) {
            return String.format(_fmtter, 102, "You are not the lock holder", " ");
        }

//        if (!session.equals(LockServiceManager.getInstance().getSession(lockName))) {
//            return String.format(_fmtter, 108, "Session check failed", "");
//        }

        _lockHolderMap.remove(lockName);
        _lockCache.invalidate(lockName);

        return String.format(_fmtter, 200, "OK", " ");
    }

    public void updateHolder(final String lockName, final String caller) {
        _lockHolderMap.put(lockName, caller);
    }

    public String getHolder(final String lockName) {
        return _lockHolderMap.get(lockName);
    }


    private static class MyHttpHandler implements HttpHandler {

        private static final String _FUNC = "func";

        private static final String _FUNC_LOCK = "lock";
        private static final String _FUNC_TRYLOCK = "tryLock";  // send tryLock command
        private static final String _FUNC_QUERYLOCK = "queryLock"; // query if lock is aquired
        private static final String _FUNC_UNLOCK = "unlock";
        private static final String _FUNC_WORK = "work";

        private static final String _CALLER = "callerId";

        private static final String _LOCK = "lock";

        private static final String _SESSION = "_ses";

        public void handle(HttpExchange httpExchange) throws IOException {
            String query = httpExchange.getRequestURI().getQuery();
            Map<String, String> params = _queryToMap(query);
            final String func = params.get(_FUNC);
            if (func == null) {
                _noFuncSpecified(httpExchange);
            }
            else if (func.equals(_FUNC_LOCK)) {
                _lock(httpExchange, params);
            }
            else if (func.equals(_FUNC_TRYLOCK)) {
                _tryLock(httpExchange, params);
            }
            else if (func.equals(_FUNC_UNLOCK)) {
                _unlock(httpExchange, params);
            }
            else if (func.equals(_FUNC_QUERYLOCK)) {
                _queryLock(httpExchange, params);
            }
            else if (func.equals(_FUNC_WORK)) {
                _work(httpExchange, params);
            }
            else {
                _default(httpExchange);
            }
        }

        private void _default(HttpExchange httpExchange) {

        }


        private void _lock(final HttpExchange httpExchange, Map<String, String> params) throws IOException {
            _tryLock(httpExchange, params);
        }

        private void _tryLock(final HttpExchange httpExchange, Map<String, String> params) throws IOException {
            // background threads to try lock
            String callerId = params.get(_CALLER);
            String lockName = params.get(_LOCK);

            if (callerId == null) {
                String resp = String.format(_fmtter, 106, "caller id not specified", " ");
                httpExchange.sendResponseHeaders(200, resp.getBytes().length);
                final OutputStream output = httpExchange.getResponseBody();
                output.write(resp.getBytes());
                output.flush();
                httpExchange.close();
            }
            else {
                String resp;
                if (callerId.equalsIgnoreCase(LockServiceManager.getInstance().getHolder(lockName))) {
                    String session = LockServiceManager.getInstance().getSession(lockName);
                    if (session == null) {
                        _logger.warn("session expired, try re-lock");
                        resp = LockServiceManager.getInstance().lock(lockName);
                    }
                    else {
                        resp = String.format(_fmtter, 200, "OK", session);
                    }
                }
                else {
                    resp = LockServiceManager.getInstance().lock(lockName);
                }

                if (!resp.contains("Lock exists -")) {
                    LockServiceManager.getInstance().updateHolder(lockName, callerId);
                }
                httpExchange.sendResponseHeaders(200, resp.getBytes().length);
                final OutputStream output = httpExchange.getResponseBody();
                output.write(resp.getBytes());
                output.flush();
                httpExchange.close();
            }
        }

        private void _unlock(final HttpExchange httpExchange, Map<String, String> params) throws IOException {
            String callerId = params.get(_CALLER);
            String lockName = params.get(_LOCK);
//            final String session = httpExchange.getRequestHeaders().getFirst(_SESSION);

            if (callerId == null) {
                // todo: should response in json
                httpExchange.sendResponseHeaders(500, HttpMessages.MESSAGE_NO_CALLER.getBytes().length);
                final OutputStream output = httpExchange.getResponseBody();
                output.write(HttpMessages.MESSAGE_NO_CALLER.getBytes());
                output.flush();
                httpExchange.close();
            }
            else {
                final String resp = LockServiceManager.getInstance().unlock(lockName, callerId);
                httpExchange.sendResponseHeaders(200, resp.getBytes().length);
                final OutputStream output = httpExchange.getResponseBody();
                output.write(resp.getBytes());
                output.flush();
                httpExchange.close();
            }
        }

        private void _queryLock(final HttpExchange he, Map<String, String> params) {

        }

        // dummy work
        // write things back
        private void _work(final HttpExchange he, Map<String, String> params) throws IOException {
            final String callerId = params.get(_CALLER);
            final String session = he.getRequestHeaders().getFirst(_SESSION);
            final String lock = params.get(_LOCK);
            // todo: check params

            // check lock holder
            String resp;
            if (!_checkLockHolder(lock, callerId)) {
                resp = String.format(_fmtter, 103, "You are not the lock holder", " ");
            }
            else if (!_checkLockSession(lock, session)) {
                resp = String.format(_fmtter, 104, "Session not correct", " ");
            }
            else {
                resp = String.format(_fmtter, 200, "All OK. Dummy Working... ", " ");
            }

            he.sendResponseHeaders(200, resp.getBytes().length);
            final OutputStream output = he.getResponseBody();
            output.write(resp.getBytes());
            output.flush();
            he.close();
        }

        private boolean _checkLockHolder(final String lock, final String caller) {
            final String _curHolder = LockServiceManager.getInstance().getHolder(lock);
            return caller.equalsIgnoreCase(_curHolder);
        }

        private boolean _checkLockSession(final String lock, final String ses) {
            final String correct = LockServiceManager.getInstance().getSession(lock);
            return ses.equals(correct);
        }

        private void _noFuncSpecified(final HttpExchange httpExchange) throws IOException {
            httpExchange.sendResponseHeaders(500, HttpMessages.MESSAGE_NO_FUNC.getBytes().length);
            final OutputStream output = httpExchange.getResponseBody();
            output.write(HttpMessages.MESSAGE_NO_FUNC.getBytes());
            output.flush();
            httpExchange.close();
        }

        private Map<String, String> _queryToMap(final String query) {
            Map<String, String> params = new HashMap<String, String>();
            for (String param : query.split("&")) {
                String pair[] = param.split("=");
                params.put(pair[0], pair.length > 1 ? pair[1] : "");
            }
            return params;
        }
    }

    private String getSession(String lock) {
        return _lockCache.asMap().get(lock);
    }

    public static void main(String[] args) throws LockServiceException {
        LockServiceManager.getInstance().start();
        _logger.info("started");
    }
}
