package net.cassite.tdpcli;

import net.cassite.tdpcli.util.Utils;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public class IntelPlatform implements Platform {
    /*
     * 2^Y * (1.0 + Z/4.0) * Time_Unit
     * Y=5bits(0~31), Z=2bits (0,1,2,3)
     */
    private static final int TIME_Y_TOTAL = 32;
    private static final int TIME_Z_TOTAL = 4;
    private static final double[][] timeTableYZ = new double[TIME_Y_TOTAL][TIME_Z_TOTAL];

    static {
        int[] numTableY = new int[TIME_Y_TOTAL];
        for (int i = 0; i < numTableY.length; ++i) {
            numTableY[i] = 1;
            for (int j = 0; j < i; ++j) {
                numTableY[i] *= 2;
            }
        }
        double[] numTableZ = new double[TIME_Z_TOTAL];
        for (int i = 0; i < numTableZ.length; ++i) {
            numTableZ[i] = 1 + i / 4.0;
        }
        for (int i = 0; i < numTableY.length; ++i) {
            for (int j = 0; j < numTableZ.length; ++j) {
                timeTableYZ[i][j] = numTableY[i] * numTableZ[j];
            }
        }
    }

    private final String rwPath;

    public IntelPlatform(String rwPath) {
        this.rwPath = rwPath;
    }

    private String exec(String... args) {
        var cmd = new StringBuilder();
        var isFirst = true;
        for (var arg : args) {
            if (isFirst) {
                isFirst = false;
            } else {
                cmd.append(" ");
            }
            cmd.append(arg);
        }
        var exec = new String[]{
            rwPath,
            "/Min", "/Nologo", "/Stdout", "/Command=" + cmd
        };
        Utils.debug("execute: " + Arrays.asList(exec));
        Process p;
        try {
            p = Runtime.getRuntime().exec(exec);
        } catch (IOException e) {
            throw new EX("failed to execute command " + Arrays.toString(exec), e);
        }
        boolean exited;
        while (true) {
            try {
                exited = p.waitFor(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignore) {
                continue;
            }
            break;
        }
        if (!exited) {
            throw new EX("failed to execute command " + Arrays.toString(exec) + ": timeout");
        }
        var reader = p.inputReader();
        var outputSB = new StringBuilder();
        var buf = new char[16];
        while (true) {
            int n;
            try {
                n = reader.read(buf);
            } catch (IOException e) {
                throw new EX("failed to retrieve output of " + Arrays.toString(exec), e);
            }
            if (n == -1) {
                break;
            }
            outputSB.append(buf, 0, n);
        }
        var output = outputSB.toString();
        if (p.exitValue() != 0) {
            throw new EX("failed to execute command " + Arrays.toString(exec) + ": exit code: " + p.exitValue() + ", output: " + output);
        }
        Utils.debug("output: " + output);
        return output;
    }

    private static final class Units {
        double power;
        double time;
    }

    @SuppressWarnings("PointlessBitwiseExpression")
    private Units getUnits() {
        long value = readMSR(0x606);

        int power = (int) ((value >> 0) & 0b1111); // [3:0]
        int time = (int) ((value >> 16) & 0b1111); // [19:16]

        var units = new Units();
        units.power = Math.pow(0.5, power);
        units.time = Math.pow(0.5, time);

        Utils.debug("units: power = 1/2^" + power + ", time = 1/2^" + time);

        return units;
    }

    public PowerLimit getMSRPowerLimit() {
        var units = getUnits();
        long value = readMSR(0x610);
        return formatPowerLimit(value, units);
    }

    @SuppressWarnings("DuplicatedCode")
    public PowerLimit getMMIOPowerLimit() {
        var units = getUnits();
        var mchbar = readPCI32(0, 0, 0, 0x48);
        mchbar = mchbar - mchbar % 4; // align to 4

        long l = read32(mchbar + 0x59A0);
        long h = read32(mchbar + 0x59A0 + 4);

        long value = (h << 32) | l;
        return formatPowerLimit(value, units);
    }

    @Override
    public PowerLimit getPowerLimit() {
        return getMSRPowerLimit();
    }

    public void updateMSRPowerLimit(Args args) {
        var units = getUnits();
        long value = readMSR(0x610);
        long oldValue = value;
        value = setPLValues(value, args, units);
        if (oldValue == value) {
            Utils.debug("msr not changed");
            return;
        }
        wrmsr(0x610, value);
    }

    @SuppressWarnings("DuplicatedCode")
    public void updateMMIOPowerLimit(Args args) {
        var units = getUnits();
        var mchbar = readPCI32(0, 0, 0, 0x48);
        mchbar = mchbar - mchbar % 4; // align to 4

        long l = read32(mchbar + 0x59A0);
        long h = read32(mchbar + 0x59A0 + 4);

        long value = (h << 32) | l;
        long oldValue = value;
        var oldPL = formatPowerLimit(value, units);
        value = setPLValues(value, args, units);
        var newPL = formatPowerLimit(value, units);

        if (value == oldValue) {
            Utils.debug("mmio not changed");
            return;
        }

        if (newPL.pl1.power > oldPL.pl2.power) {
            // need to apply pl2 first because pl1 exceeds old pl2
            write32(mchbar + 0x59A0 + 4, (int) ((value >> 32) & 0xffffffffL));
            write32(mchbar + 0x59A0, (int) (value & 0xffffffffL));
        } else {
            // apply pl1 first
            write32(mchbar + 0x59A0, (int) (value & 0xffffffffL));
            write32(mchbar + 0x59A0 + 4, (int) ((value >> 32) & 0xffffffffL));
        }
    }

    @Override
    public void updatePowerLimit(Args args) {
        updateMSRPowerLimit(args);
        updateMMIOPowerLimit(args);
    }

    @SuppressWarnings("PointlessBitwiseExpression")
    private static PowerLimit formatPowerLimit(long value, Units units) {
        int pl1 = (int) ((value >> 0) & 0b111111111111111);
        int enable1 = (int) ((value >> 15) & 0b1);
        int clamping1 = (int) ((value >> 16) & 0b1);
        int time1 = (int) ((value >> 17) & 0b1111111);
        int pl2 = (int) ((value >> 32) & 0b111111111111111);
        int enable2 = (int) ((value >> 47) & 0b1);
        int clamping2 = (int) ((value >> 48) & 0b1);
        int time2 = (int) ((value >> 49) & 0b1111111);

        var ret = new PowerLimit();

        ret.locked = ((value >> 63) & 0b1) == 1;

        ret.pl1.power = pl1 * units.power;
        ret.pl1.enabled = enable1 == 1;
        ret.pl1.clamping = clamping1 == 1;
        ret.pl1.time = formatTime(time1, units);

        ret.pl2.power = pl2 * units.power;
        ret.pl2.enabled = enable2 == 1;
        ret.pl2.clamping = clamping2 == 1;
        ret.pl2.time = formatTime(time2, units);

        return ret;
    }

    @SuppressWarnings("PointlessBitwiseExpression")
    private static double formatTime(int time, Units units) {
        int y = (time >> 0) & 0b11111;
        int z = (time >> 5) & 0b11;

        return Math.pow(2, y) * (1.0 + z / 4.0) * units.time;
    }

    private static String formatBDF(int bdf) {
        String s = Utils.toHexString(bdf);
        if (s.length() == 1) {
            s = "0" + s;
        }
        return "0x" + s;
    }

    private static String formatLoc(int loc) {
        String s = Utils.toHexString(loc);
        if (s.length() == 1) {
            s = "00" + s;
        } else if (s.length() == 2) {
            s = "0" + s;
        }
        return "0x" + s;
    }

    private static String format32bitLoc(long loc) {
        String hex = Long.toHexString(loc).toUpperCase();
        if (hex.length() < 8) {
            hex = "0".repeat(8 - hex.length()) + hex;
        }
        return "0x" + hex;
    }

    @SuppressWarnings("ConstantConditions")
    private long setPLValues(long value, Args args, Units units) {
        if (args.pl1 != null) {
            long v = (int) (args.pl1 / units.power);
            long mask = 0b111111111111111;
            v = v & mask;
            value = (value & ~mask) | v;
        }
        if (args.pl2 != null) {
            long v = (long) (args.pl2 / units.power);
            v = v << 32;
            long mask = 0b111111111111111L << 32;
            v = v & mask;
            value = (value & ~mask) | v;
        }
        if (args.enable2 != null) {
            long v = args.enable2 ? 1 : 0;
            v = v << 47;
            long mask = 1L << 47;
            v = v & mask;
            value = (value & ~mask) | v;
        }
        if (args.clamping1 != null) {
            long v = args.clamping1 ? 1 : 0;
            v = v << 16;
            long mask = 1L << 16;
            v = v & mask;
            value = (value & ~mask) | v;
        }
        if (args.clamping2 != null) {
            long v = args.clamping2 ? 1 : 0;
            v = v << 48;
            long mask = 1L << 48;
            v = v & mask;
            value = (value & ~mask) | v;
        }
        if (args.time1 != null) {
            double t = args.time1 / units.time;
            int y = 0;
            int z = 0;
            double delta = Double.MAX_VALUE;
            for (int yi = 0; yi < TIME_Y_TOTAL; ++yi) {
                for (int zi = 0; zi < TIME_Z_TOTAL; ++zi) {
                    double d = Math.abs(timeTableYZ[yi][zi] - t);
                    if (delta > d) {
                        delta = d;
                        y = yi;
                        z = zi;
                    }
                }
            }
            int res = ((z & 0b11) << 5) | (y & 0b11111);
            Utils.debug("time = " + args.time1 + ", tu = " + units.time + ", t = " + t + ", y = " + y + " z = " + z + ", res = " + Integer.toBinaryString(res));

            long v = res;
            v = v << 17;
            long mask = 0b1111111 << 17;
            v = v & mask;
            value = (value & ~mask) | v;
        }
        return value;
    }

    @SuppressWarnings("DuplicatedCode")
    private long readMSR(int loc) {
        String location = formatLoc(loc);
        String result = exec("RDMSR", location);
        String expectedPrefix = "Read MSR " + location + ": High 32bit(EDX) = ";

        String baseErr = "unexpected output for rdmsr " + location;

        if (!result.startsWith(expectedPrefix)) {
            throw new EX(baseErr + ": " + result);
        }
        String x = result.substring(expectedPrefix.length());
        if (!x.contains(",")) {
            throw new EX(baseErr + ": " + result);
        }
        String highStr = x.substring(0, x.indexOf(","));
        if (!highStr.startsWith("0x")) {
            throw new EX(baseErr + ": " + highStr + " is not valid hex");
        }
        long high;
        try {
            high = Long.parseLong(highStr.substring("0x".length()), 16);
        } catch (NumberFormatException e) {
            throw new EX(baseErr + ": " + highStr + " is not valid hex");
        }

        x = x.substring(x.indexOf(","));
        expectedPrefix = ", Low 32bit(EAX) = ";
        if (!x.startsWith(expectedPrefix)) {
            throw new EX(baseErr + ": " + result);
        }
        x = x.substring(expectedPrefix.length());
        String lowStr = x;
        if (x.contains("\n")) {
            lowStr = x.substring(0, x.indexOf("\n")).trim(); // trim the \r if exists
        }
        if (!lowStr.startsWith("0x")) {
            throw new EX(baseErr + ": " + lowStr + " is not valid hex");
        }
        long low;
        try {
            low = Long.parseLong(lowStr.substring("0x".length()), 16);
        } catch (NumberFormatException e) {
            throw new EX(baseErr + ": " + lowStr + " is not valid hex");
        }

        return high << 32 | low;
    }

    private void wrmsr(int loc, long value) {
        String location = formatLoc(loc);
        String high = format32bitLoc((value >> 32) & 0xffffffffL);
        String low = format32bitLoc((value) & 0xffffffffL);
        String result = exec("WRMSR", location, high, low, "0");
        String expectedPrefix = "Write MSR " + location + ": High 32bit(EDX) = " + high + ", Low 32bit(EAX) = " + low;

        String baseErr = "unexpected output for wrmsr " + location + " " + high + " " + low + " 0";

        if (!result.startsWith(expectedPrefix)) {
            throw new EX(baseErr + ": " + result);
        }
    }

    @SuppressWarnings({"DuplicatedCode", "SameParameterValue"})
    private long readPCI32(int b, int d, int f, int loc) {
        String bus = formatBDF(b);
        String device = formatBDF(d);
        String function = formatBDF(f);
        String location = formatLoc(loc);
        String result = exec("RPCI32", bus, device, function, location);
        String expectedPrefix = "Read PCI Bus/Dev/Fun/Offset " + bus + "/" + device + "/" + function + "/" + location + " = ";

        String baseErr = "unexpected output for rpci32 " + bus + " " + device + " " + function + " " + location;

        if (!result.startsWith(expectedPrefix)) {
            throw new EX(baseErr + ": " + result);
        }
        String str = result.substring(expectedPrefix.length()).trim(); // trim newline
        if (!str.startsWith("0x")) {
            throw new EX(baseErr + ": " + str + " is not valid hex");
        }
        long res;
        try {
            res = Long.parseLong(str.substring("0x".length()), 16);
        } catch (NumberFormatException e) {
            throw new EX(baseErr + ": " + str + " is not valid hex");
        }
        return res;
    }

    @SuppressWarnings("DuplicatedCode")
    private int read32(long loc) {
        String location = format32bitLoc(loc);
        String result = exec("R32", location);
        String expectedPrefix = "Read Memory Address " + location + " = ";

        String baseErr = "unexpected output for r32 " + location;

        if (!result.startsWith(expectedPrefix)) {
            throw new EX(baseErr + ": " + result);
        }
        String str = result.substring(expectedPrefix.length()).trim(); // trim newline
        if (!str.startsWith("0x")) {
            throw new EX(baseErr + ": " + str + " is not valid hex");
        }
        int res;
        try {
            res = (int) Long.parseLong(str.substring("0x".length()), 16);
        } catch (NumberFormatException e) {
            throw new EX(baseErr + ": " + str + " is not valid hex");
        }
        return res;
    }

    private void write32(long loc, int v) {
        String location = format32bitLoc(loc);
        String value = format32bitLoc(v);
        String result = exec("W32", location, value);
        String expectedRes = "Write Memory Address " + location + " = " + value;

        String baseErr = "unexpected output for w32 " + location + " " + value;

        if (!result.trim().equals(expectedRes)) {
            throw new EX(baseErr + ": " + result);
        }
    }
}
