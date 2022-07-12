package net.cassite.tdpcli.util;

public enum LogLevel {
    all(0),
    debug(1),
    info(2),
    warn(3),
    error(4),
    none(5),
    ;
    private final int level;

    LogLevel(int level) {
        this.level = level;
    }

    public boolean greaterOrEqualTo(LogLevel l) {
        return level >= l.level;
    }
}
