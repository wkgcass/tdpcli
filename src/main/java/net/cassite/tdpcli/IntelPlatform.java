package net.cassite.tdpcli;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public class IntelPlatform implements Platform {
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

    public PowerLimit getMMIOPowerLimit() {
        var units = getUnits();
        var mchbar = readPCI32(0, 0, 0, 0x48);
        mchbar = mchbar - mchbar % 4; // align to 4

        long l = read32((int) (mchbar + 0x59A0));
        long h = read32((int) (mchbar + 0x59A0 + 4));

        long value = (h << 32) | l;
        return formatPowerLimit(value, units);
    }

    @Override
    public PowerLimit getPowerLimit() {
        return getMSRPowerLimit();
    }

    public void updateMSRPowerLimit(PowerLimit pl) {
        // TODO
    }

    public void updateMMIOPowerLimit(PowerLimit pl) {
        // TODO
    }

    @Override
    public void updatePowerLimit(PowerLimit pl) {
        // TODO
        throw new UnsupportedOperationException();
    }

    @SuppressWarnings("PointlessBitwiseExpression")
    private static PowerLimit formatPowerLimit(long value, Units units) {
        int pl1 = (int) ((value >> 0) & 0b111111111111111);
        int enable1 = (int) ((value >> 15) & 0b1);
        int clapping1 = (int) ((value >> 16) & 0b1);
        int time1 = (int) ((value >> 17) & 0b1111111);
        int pl2 = (int) ((value >> 32) & 0b111111111111111);
        int enable2 = (int) ((value >> 47) & 0b1);
        int clapping2 = (int) ((value >> 48) & 0b1);
        int time2 = (int) ((value >> 49) & 0b1111111);

        var ret = new PowerLimit();

        ret.locked = ((value >> 63) & 0b1) == 1;

        ret.pl1.power = pl1 * units.power;
        ret.pl1.enabled = enable1 == 1;
        ret.pl1.clamping = clapping1 == 1;
        ret.pl1.time = formatTime(time1, units);

        ret.pl2.power = pl2 * units.power;
        ret.pl2.enabled = enable2 == 1;
        ret.pl2.clamping = clapping2 == 1;
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

    @SuppressWarnings({"DuplicatedCode", "SameParameterValue"})
    private long readPCI32(int b, int d, int f, int loc) {
        String bus = formatBDF(b);
        String device = formatBDF(d);
        String function = formatBDF(f);
        String location = formatLoc(loc);
        String result = exec("RPCI32", bus, device, function, location);
        String expectedRefix = "Read PCI Bus/Dev/Fun/Offset " + bus + "/" + device + "/" + function + "/" + location + " = ";

        String baseErr = "unexpected output for rpci32 " + bus + " " + device + " " + function + " " + location;

        if (!result.startsWith(expectedRefix)) {
            throw new EX(baseErr + ": " + result);
        }
        String str = result.substring(expectedRefix.length()).trim(); // trim newline
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
    private int read32(int loc) {
        String location = formatLoc(loc);
        String result = exec("R32", location);
        String expectedRefix = "Read Memory Address " + location + " = ";

        String baseErr = "unexpected output for r32 " + location;

        if (!result.startsWith(expectedRefix)) {
            throw new EX(baseErr + ": " + result);
        }
        String str = result.substring(expectedRefix.length()).trim(); // trim newline
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
}
