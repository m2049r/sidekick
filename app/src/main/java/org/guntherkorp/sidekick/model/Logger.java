package org.guntherkorp.sidekick.model;

public class Logger {
    static {
        System.loadLibrary("monerujo");
    }

    static public native void initLogger(String argv0, String defaultLogBaseName);

    //TODO: maybe put these in an enum like in monero core - but why?
    static public int LOGLEVEL_SILENT = -1;
    static public int LOGLEVEL_WARN = 0;
    static public int LOGLEVEL_INFO = 1;
    static public int LOGLEVEL_DEBUG = 2;
    static public int LOGLEVEL_TRACE = 3;
    static public int LOGLEVEL_MAX = 4;

    static public native void setLogLevel(int level);

    static public native void logDebug(String category, String message);

    static public native void logInfo(String category, String message);

    static public native void logWarning(String category, String message);

    static public native void logError(String category, String message);

    static public native String moneroVersion();

}
