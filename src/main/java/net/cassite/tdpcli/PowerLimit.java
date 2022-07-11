package net.cassite.tdpcli;

public class PowerLimit {
    public boolean locked = false;
    public final Limit pl1 = new Limit();
    public final Limit pl2 = new Limit();

    public static final class Limit {
        public boolean enabled;
        public double power; // watts
        public boolean clamping;
        double time; // seconds

        @Override
        public String toString() {
            return power + "W " + time + "s enabled=" + (enabled ? "Y" : "N") + " clapping=" + (clamping ? "Y" : "N");
        }
    }

    @Override
    public String toString() {
        return "-----BEGIN PowerLimit-----\n" +
            "locked=" + (locked ? "Y" : "N") + "\n" +
            "PL1: " + pl1 + "\n" +
            "PL2: " + pl2 + "\n" +
            "-----END PowerLimit-----\n";
    }
}
