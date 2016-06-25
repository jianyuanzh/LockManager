package com.yflog.lckmgr.server;

import java.util.LinkedList;

/**
 * Created by vincent on 6/25/16.
 */
public class LockItem {
    final String name;
    String currentOwner = null;

    final LinkedList<String> waitingList = new LinkedList<String>();
    LockItem(final String name) {
        this.name = name;
    }
}
