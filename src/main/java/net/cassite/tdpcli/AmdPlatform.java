package net.cassite.tdpcli;

public class AmdPlatform implements Platform {
    private final String ryzenadj;

    public AmdPlatform(String ryzenadj) {
        this.ryzenadj = ryzenadj;
    }

    @Override
    public PowerLimit getPowerLimit() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updatePowerLimit(Args args) {
        throw new UnsupportedOperationException();
    }
}
