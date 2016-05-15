package com.yflog.lckmgr.client;

import com.yflog.lckmgr.common.LockServiceException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by vincent on 5/15/16.
 */
public class LockableHttpClient extends Lockable {

    private static Logger _logger = Logger.getLogger(LockableHttpClient.class);

    private final CloseableHttpClient _httpClient;
    private volatile int _status = 0;  // 0: init, 1: ready, 2: closed
    private final String _caller;

    private String _serverAddr = "http://127.0.0.1:2946";

    private Map<String, String> _lockSessions = new HashMap<String, String>();

    public LockableHttpClient(final String caller) throws IOException {
        _httpClient = HttpClients.createDefault();
        _status = 1;
        _caller = caller;
    }

    public JSONObject doWork(String work, String lockName) throws IOException, LockServiceException {
        if (!_lockSessions.containsKey(lockName)) {
            JSONObject lockResp = lock(lockName);
            int status = lockResp.getInt("status");
            if (status == 200) {
                String session = lockResp.getString("session_id");
                _lockSessions.put(lockName, session);
            }
            else {
                throw new LockServiceException(String.format("Lock failed - lockname=%s, caller=%s, errMsg=%s", lockName, _caller, lockResp));
            }
        }

        final String target = String.format("%s?func=%s&callerId=%s&lock=%s", _serverAddr, "work", _caller, lockName);
        HttpGet request = new HttpGet(target);
        request.addHeader(new BasicHeader("_ses", _lockSessions.get(lockName)));
        CloseableHttpResponse response = null;
        try {
            response = _httpClient.execute(request);
            String respStr = EntityUtils.toString(response.getEntity());
            _logger.info("response got - " + respStr);
            return new JSONObject(respStr);
        }
        finally {
            if (response != null) {
                try {
                    response.close();
                }
                catch (Exception e) {
                }
            }

        }

    }

    public JSONObject tryWorking(String work, String lockName) throws IOException, LockServiceException {
        if (!_lockSessions.containsKey(lockName)) {
            JSONObject lockResp = tryLock(lockName);
            int status = lockResp.getInt("status");
            if (status == 200) {
                String session = lockResp.getString("session_id");
                _lockSessions.put(lockName, session);
            }
            else {
                throw new LockServiceException(String.format("Lock failed - lockname=%s, caller=%s, errMsg=%s", lockName, _caller, lockResp));
            }
        }

        final String target = String.format("%s?func=%s&callerId=%s&lock=%s", _serverAddr, "work", _caller, lockName);
        HttpGet request = new HttpGet(target);
        request.addHeader(new BasicHeader("_ses", _lockSessions.get(lockName)));
        CloseableHttpResponse response = null;
        try {
            response = _httpClient.execute(request);
            String respStr = EntityUtils.toString(response.getEntity());
            _logger.info("response got - " + respStr);
            return new JSONObject(respStr);
        }
        finally {
            if (response != null) {
                try {
                    response.close();
                }
                catch (Exception e) {
                }
            }

        }
    }

    @Override
    protected final JSONObject lock(String lockName) throws IOException {
        JSONObject tryLockResp = null;
        while (true) {
            try {
                tryLockResp = tryLock(lockName);
                int status = tryLockResp.getInt("status");
                if (status == 200) {
                    return tryLockResp;
                }
            }
            catch (IOException e) {
                _logger.warn("try lock failed with exception", e);
                throw e;
            }
        }
    }

    protected final JSONObject unlock(String lockName) throws IOException {
        String target = String.format("%s?func=%s&callerId=%s&lock=%s",
                _serverAddr, "unlock", _caller, lockName);
        CloseableHttpResponse response = null;
        try {
            response = _httpClient.execute(new HttpGet(target));
            String responseString = EntityUtils.toString(response.getEntity());
            _logger.info("response got - " + responseString);
            JSONObject resp = new JSONObject(responseString);
            if (resp.getInt("status") == 200) {
                _lockSessions.remove(lockName);
            }
            return resp;
        }
        finally {
            if (response != null) {
                try {
                    response.close();
                }
                catch (Exception e) {
                }
            }
        }
    }

    protected final JSONObject tryLock(String lockName) throws IOException {
        String target = String.format("%s?func=%s&callerId=%s&lock=%s",
                _serverAddr, "tryLock", _caller, lockName);

        CloseableHttpResponse response = null;
        try {
            response = _httpClient.execute(new HttpGet(target));
            String responseString = EntityUtils.toString(response.getEntity());
            _logger.info("response got - " + responseString);
            return new JSONObject(responseString);
        }
        finally {
            if (response != null) {
                try {
                    response.close();
                }
                catch (Exception e) {
                }
            }
        }
    }

    public void close() {
        for (String lockName : _lockSessions.keySet()) {
            try {
                unlock(lockName);
            }
            catch (Exception e) {
                _logger.warn("failed to unlock on " + lockName);
            }
            finally {
                _lockSessions.remove(lockName);
            }
        }
        try {
            _httpClient.close();
        }
        catch (IOException e) {
        }
        finally {
            _status = 2;
        }
    }

    private boolean _checkStatus() {
        return _status == 1;
    }

    public static void main(String[] args) throws IOException, LockServiceException {
        LockableHttpClient client = new LockableHttpClient("dummy-caller");
        JSONObject json = client.tryWorking("test", "lockOneX");
        System.out.println(json);

        LockableHttpClient client2 = new LockableHttpClient("anothercaller");
        JSONObject json2 = client2.doWork("test", "lockOne");

        System.out.println(json2);
        System.out.println(client2.unlock("lockOne"));
        System.out.println(client2.unlock("lockOne"));

    }
}
