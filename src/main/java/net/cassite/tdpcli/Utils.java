package net.cassite.tdpcli;

import java.util.HashSet;
import java.util.Set;

public class Utils {
    private Utils() {
    }

    public static void log(String s) {
        System.out.println(s);
    }

    public static void debug(String s) {
        log(s);
    }

    public static void info(String s) {
        log(s);
    }

    public static void warn(String s) {
        log(s);
    }

    public static void error(String s) {
        log(s);
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
