package net.cassite.tdpcli.util;

import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;

import java.util.HashSet;
import java.util.Set;

public class Utils {
    private Utils() {
    }

    public static LogLevel logLevel = LogLevel.info;

    public static void debug(String s) {
        if (LogLevel.debug.greaterOrEqualTo(logLevel)) {
            Logger.trace(LogType.ALERT, s);
        }
    }

    public static void info(String s) {
        if (LogLevel.info.greaterOrEqualTo(logLevel)) {
            Logger.info(LogType.ALERT, s);
        }
    }

    public static void warn(String s) {
        if (LogLevel.warn.greaterOrEqualTo(logLevel)) {
            Logger.warn(LogType.ALERT, s);
        }
    }

    public static void error(String s) {
        if (LogLevel.error.greaterOrEqualTo(logLevel)) {
            Logger.error(LogType.ALERT, s);
        }
    }

    public static boolean isInteger(String s) {
        try {
            Integer.parseInt(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static final Set<String> trueBools = new HashSet<>() {
        {
            add("true");
            add("y");
            add("Y");
            add("yes");
            add("YES");
        }
    };
    private static final Set<String> falseBools = new HashSet<>() {
        {
            add("false");
            add("n");
            add("N");
            add("no");
            add("NO");
        }
    };

    public static boolean isBool(String s) {
        return trueBools.contains(s) || falseBools.contains(s);
    }

    public static boolean getBool(String s) {
        return trueBools.contains(s);
    }

    public static String toHexString(int n) {
        long x = n;
        x = x & 0xffffffffL;
        return Long.toString(x, 16).toUpperCase();
    }
}
