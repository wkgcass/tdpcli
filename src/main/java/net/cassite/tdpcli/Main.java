package net.cassite.tdpcli;

import io.vproxy.dep.vjson.JSON;
import io.vproxy.vfd.IPPort;
import net.cassite.tdpcli.daemon.Config;
import net.cassite.tdpcli.daemon.Daemon;
import net.cassite.tdpcli.util.PrintFormat;
import net.cassite.tdpcli.util.Utils;
import oshi.SystemInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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

        if (a.logLevel != null) {
            Utils.logLevel = a.logLevel;
        }

        Utils.debug("args = " + a);

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
                Utils.info("If you are sure this program can work on your platform, add `--force-intel` or `--force-amd` flag, and please create an issues to report your platform: https://github.com/wkgcass/tdpcli");
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
            if (a.intelMsr) {
                Utils.error("cannot specify --msr");
                System.exit(1);
                return;
            }
            if (a.intelMmio) {
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

        if (a.daemon) {
            String err = a.validateForDaemon();
            if (err != null) {
                Utils.error(err);
                System.exit(1);
                return;
            }
            var ipport = a.daemonListen;
            if (ipport == null) {
                ipport = new IPPort("127.0.0.1", 14514);
            }
            var configPath = a.daemonConfig;
            Config config;
            if (configPath != null) {
                String configStr;
                try {
                    configStr = Files.readString(Path.of(configPath));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                config = JSON.deserialize(configStr, Config.rule);
            } else {
                config = new Config();
            }
            var daemon = new Daemon(ipport, platform, config);
            daemon.start();
            if (a.isModify()) {
                daemon.setArgs(a);
            }
            return;
        }

        if (a.isModify()) {
            if (a.intelMsr) {
                //noinspection ConstantConditions
                ((IntelPlatform) platform).updateMSRPowerLimit(a);
            }
            if (a.intelMmio) {
                //noinspection ConstantConditions
                ((IntelPlatform) platform).updateMMIOPowerLimit(a);
            }
            if (!a.intelMsr && !a.intelMmio) {
                platform.updatePowerLimit(a);
            }
        } else {
            if (a.intelMsr && a.intelMmio) {
                Utils.error("cannot specify --msr and --mmio at the same time when retrieving info");
                System.exit(1);
                return;
            }

            PowerLimit pl;
            if (a.intelMsr) {
                //noinspection ConstantConditions
                pl = ((IntelPlatform) platform).getMSRPowerLimit();
            } else if (a.intelMmio) {
                //noinspection ConstantConditions
                pl = ((IntelPlatform) platform).getMMIOPowerLimit();
            } else {
                pl = platform.getPowerLimit();
            }

            if (a.printFormat == PrintFormat.table) {
                System.out.println(pl.formatToTable());
            } else if (a.printFormat == PrintFormat.json) {
                System.out.println(pl.formatToJson().pretty());
            } else {
                System.out.println(pl.formatToTable());
            }
        }
    }
}
