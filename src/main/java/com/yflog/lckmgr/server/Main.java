package com.yflog.lckmgr.server;

import org.apache.log4j.Logger;

/**
 * Created by vincent on 6/24/16.
 * The entrance of LockManager server.
 */
public class Main {
    private static final String _LOGGER_NAME = "LockService";
    private static final Logger _logger = Logger.getLogger(_LOGGER_NAME);

    public static void main(String[] args) {
        Controller c = new Controller(8888);
        try {
            _logger.info("Start");
            c.run();
        }
        finally {
            _logger.info("Shutdown");
            c.shutdown();
        }
    }
}
