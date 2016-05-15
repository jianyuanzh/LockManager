package com.yflog.lckmgr.client;

import org.json.JSONObject;

import java.io.IOException;

/**
 * Created by vincent on 5/15/16.
 */
public abstract class Lockable {
    protected abstract JSONObject lock(String lockName) throws IOException;

    protected abstract JSONObject unlock(String lockName) throws IOException;

    protected abstract JSONObject tryLock(String lockName) throws IOException;

}
