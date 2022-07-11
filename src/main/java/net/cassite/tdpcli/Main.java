package net.cassite.tdpcli;

import oshi.SystemInfo;

import static net.cassite.tdpcli.Consts.amdArch;
import static net.cassite.tdpcli.Consts.intelArch;

public class Main {
    private static final String TDPCLI_RW_EVERYTHING_PATH = "TDPCLI_RW_EVERYTHING_PATH";
    private static final String TDPCLI_RYZENADJ_PATH = "TDPCLI_RYZENADJ_PATH";
    private static final String DEFAULT_RW_PATH = "C:\\Program Files\\RW-Everything\\RW.exe";
    private static final String DEFAULT_RYZENADJ_PATH = "";

    public static void main(String[] args) {
        Args a = new Args();
        int exit = a.parse(args);
        if (exit > 0) {
            System.exit(exit);
            return;
        } else if (exit != 0) {
            System.exit(0);
            return;
        }

        if (a.forceIntel && a.forceAmd) {
            Utils.error("cannot force to use intel and amd at the same time");
            System.exit(1);
            return;
        }

        var si = new SystemInfo();
        var hal = si.getHardware();
        var microArch = hal.getProcessor().getProcessorIdentifier().getMicroarchitecture();
        if (!intelArch.contains(microArch) && !amdArch.contains(microArch)) {
            if (!a.forceIntel && !a.forceAmd) {
                Utils.error("Unregistered micro architecture `" + microArch + "`, this program might not work on this platform");
                Utils.info("If you are sure this program can work on your platform, add `--force-intel` or `--force-amd` flag, and please create an issues to report your platform: https://github.com/wkgcass");
                System.exit(1);
                return;
            }
        } else {
            if (a.forceIntel && amdArch.contains(microArch)) {
                Utils.error("--force-intel is set, but " + microArch + " is detected to be amd");
                System.exit(1);
                return;
            } else if (a.forceAmd && intelArch.contains(microArch)) {
                Utils.error("--force-amd is set, but " + microArch + " is detected to be intel");
                System.exit(1);
                return;
            }
            if (intelArch.contains(microArch)) {
                a.forceIntel = true;
            } else if (amdArch.contains(microArch)) {
                a.forceAmd = true;
            }
        }

        if (!a.forceIntel) {
            if (a.msr) {
                Utils.error("cannot specify --msr");
                System.exit(1);
                return;
            }
            if (a.mmio) {
                Utils.error("cannot specify --mmio");
                System.exit(1);
                return;
            }
        }

        Platform platform;
        if (a.forceIntel) {
            String path = System.getenv(TDPCLI_RW_EVERYTHING_PATH);
            if (path == null) {
                path = DEFAULT_RW_PATH;
            }
            platform = new IntelPlatform(path);
        } else if (a.forceAmd) {
            String path = System.getenv(TDPCLI_RYZENADJ_PATH);
            if (path == null) {
                path = DEFAULT_RYZENADJ_PATH;
            }
            platform = new AmdPlatform(path);
        } else {
            Utils.error("unsupported platform");
            System.exit(1);
            return;
        }

        if (a.isModify()) {
            throw new UnsupportedOperationException();
        } else {
            if (a.msr && a.mmio) {
                Utils.error("cannot specify --msr and --mmio at the same time when retrieving info");
                System.exit(1);
                return;
            }

            PowerLimit pl;
            if (a.msr) {
                //noinspection ConstantConditions
                pl = ((IntelPlatform) platform).getMSRPowerLimit();
            } else if (a.mmio) {
                //noinspection ConstantConditions
                pl = ((IntelPlatform) platform).getMMIOPowerLimit();
            } else {
                pl = platform.getPowerLimit();
            }

            System.out.println(pl);
        }
    }
}
