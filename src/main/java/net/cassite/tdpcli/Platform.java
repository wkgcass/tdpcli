package net.cassite.tdpcli;

public interface Platform {
    PowerLimit getPowerLimit();

    boolean updatePowerLimit(Args args);
}
