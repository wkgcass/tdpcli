package net.cassite.tdpcli;

public interface Platform {
    PowerLimit getPowerLimit();

    void updatePowerLimit(Args args);
}
