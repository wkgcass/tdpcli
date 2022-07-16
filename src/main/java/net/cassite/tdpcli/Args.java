package net.cassite.tdpcli;

import io.vproxy.vfd.IPPort;
import net.cassite.tdpcli.util.LogLevel;
import net.cassite.tdpcli.util.PrintFormat;
import net.cassite.tdpcli.util.Utils;
import net.cassite.tdpcli.util.Version;

import java.util.function.Consumer;

import static net.cassite.tdpcli.Consts.allowedHelpVariations;

public class Args {
    private static final String helpMsg = """
        Usage:
          tdpcli                                 show all settings
          tdpcli -h|--help|-help|help            show this message
          tdpcli --version                       show version
          tdpcli [options]
          tdpcli --show-api                      show http restful api in daemon mode
        Options:
          --pl<N>=<w>                            set long/short duration power consumption (Watts)
                                                 min: 10, max: 200
          --clamping<N>=<b>                      enable or disable long/short duration power limit clamping (bool)
          --time1=<s>                            set long duration power limit time window (seconds)
                                                 min: 1, max: 60
          --enable2=<b>                          enable or disable short duration power limit (bool)

          --force-intel                          use the `intel` way of modifying settings
          --force-amd                            use the `amd` way of modifying settings
          --daemon                               run as a daemon

          --print-format=<table|json>            print format
          --log-level=<enum>                     log level (all|debug|info|warn|error|none)
        Daemon Options:
          --listen=<host>:<port>                 listen on ana address, default 127.0.0.1:14514
          --config=<config-path>                 see api /tdpcli/api/v1.0/config
        Intel Options:
          --msr                                  use msr to get or set power limit
          --mmio                                 use mmio to get or set power limit
                                                 You can specify both --msr and --mmio when modifying, but not when retrieving
                                                 If non specified, msr will be used when retrieving, both will be used when modifying
        Environment Variables:
          TDPCLI_RW_EVERYTHING_PATH              the path to rw.exe (required for intel processors)
                                                 will use 'C:\\Program Files\\RW-Everything\\RW.exe' by default
          TDPCLI_RYZENADJ_PATH                   the path to ryzenadj.exe (required for amd processors)
                                                 will use '' by default
        """.trim();
    private static final String apiMsg = """
        GET /tdpcli/api/v1.0/version             get server version
        GET /tdpcli/api/v1.0/power_limit         retrieve power limit, successful response status code is 200
                                                 on intel platforms, you may add an optional query: mode=<msr|mmio>, default msr
        PUT /tdpcli/api/v1.0/power_limit         update power limit, request body is in the same format as the GET method
                                                 successful response status code is 204
                                                 The daemon will set the power limit to desired value every few seconds
        GET /tdpcli/api/v1.0/config              retrieve daemon config
                                                 body: {
                                                   "interval": integer, seconds, the interval between config check and set
                                                 }
        PUT /tdpcli/api/v1.0/config              modify daemon config
        """.trim();
    public static final int MAX_ALLOWED_WATTS = 200;
    public static final int MIN_ALLOWED_WATTS = 10;
    public static final int MAX_ALLOWED_SECONDS = 60;
    public static final int MIN_ALLOWED_SECONDS = 1;

    public Integer pl1 = null;
    public Integer pl2 = null;
    public Boolean enable2 = null;
    public Boolean clamping1 = null;
    public Boolean clamping2 = null;
    public Integer time1 = null;
    public boolean forceIntel = false;
    public boolean forceAmd = false;
    public boolean daemon = false;
    public PrintFormat printFormat = null;
    public LogLevel logLevel = null;
    public IPPort daemonListen = null;
    public String daemonConfig = null;
    public boolean intelMsr = false;
    public boolean intelMmio = false;

    public boolean isModify() {
        return pl1 != null
            || pl2 != null
            || enable2 != null
            || clamping1 != null
            || clamping2 != null
            || time1 != null;
    }

    public String validateForDaemon() {
        if (intelMsr) {
            return "cannot specify --msr with --daemon";
        }
        if (intelMmio) {
            return "cannot specify --mmio with --daemon";
        }
        return null;
    }

    public int parse(String[] args) {
        String badArg;
        for (String arg : args) {
            badArg = null;
            if (allowedHelpVariations.contains(arg)) {
                System.out.println(helpMsg);
                return -1;
            }
            if (arg.equals("--version")) {
                System.out.println(Version.VERSION);
                return -1;
            }
            if (arg.equals("--show-api")) {
                System.out.println(apiMsg);
                return -1;
            }
            if (arg.equals("--force-intel")) {
                forceIntel = true;
            } else if (arg.equals("--force-amd")) {
                forceAmd = true;
            } else if (arg.equals("--daemon")) {
                daemon = true;
            } else if (arg.startsWith("--listen=")) {
                var v = arg.substring("--listen=".length()).trim();
                if (!IPPort.validL4AddrStr(v)) {
                    badArg = "unexpected value for listen: " + v;
                }
                daemonListen = new IPPort(v);
            } else if (arg.startsWith("--config=")) {
                daemonConfig = arg.substring("--config=".length()).trim();
            } else if (arg.equals("--msr")) {
                intelMsr = true;
            } else if (arg.equals("--mmio")) {
                intelMmio = true;
            } else if (arg.startsWith("--pl1=")) {
                badArg = plArg(arg, "pl1", n -> pl1 = n);
            } else if (arg.startsWith("--pl2=")) {
                badArg = plArg(arg, "pl2", n -> pl2 = n);
            } else if (arg.startsWith("--enable2=")) {
                badArg = boolArg(arg, "enable2", b -> enable2 = b);
            } else if (arg.startsWith("--clamping1=")) {
                badArg = boolArg(arg, "clamping1", b -> clamping1 = b);
            } else if (arg.startsWith("--clamping2=")) {
                badArg = boolArg(arg, "clamping2", b -> clamping2 = b);
            } else if (arg.startsWith("--time1=")) {
                badArg = timeArg(arg, "time1", n -> time1 = n);
            } else if (arg.startsWith("--print-format=")) {
                var v = arg.substring("--print-format=".length()).trim();
                try {
                    printFormat = PrintFormat.valueOf(v);
                } catch (IllegalArgumentException e) {
                    badArg = "unexpected value for print-format: " + v;
                }
            } else if (arg.startsWith("--log-level=")) {
                var v = arg.substring("--log-level=".length()).trim();
                try {
                    logLevel = LogLevel.valueOf(v);
                } catch (IllegalArgumentException e) {
                    badArg = "unexpected value for log-level: " + v;
                }
            } else {
                badArg = "";
            }

            if (badArg != null) {
                if (badArg.equals("")) {
                    Utils.error("bad argument: `" + arg + "`");
                } else {
                    Utils.error("bad argument: `" + arg + "`: " + badArg);
                }
                return 1;
            }
        }
        return 0;
    }

    @SuppressWarnings("DuplicatedCode")
    private static String plArg(String arg, String field, Consumer<Integer> setter) {
        var str = arg.substring("--plN=".length()).trim();
        if (Utils.isInteger(str)) {
            var n = Integer.parseInt(str);
            if (n < MIN_ALLOWED_WATTS) {
                return field + " out of range: [" + MIN_ALLOWED_WATTS + ", " + MAX_ALLOWED_WATTS + "]";
            } else if (n > MAX_ALLOWED_WATTS) {
                return field + " out of range: [" + MIN_ALLOWED_WATTS + ", " + MAX_ALLOWED_WATTS + "]";
            } else {
                setter.accept(n);
                return null;
            }
        } else {
            return str + " is not a valid integer";
        }
    }

    private static String boolArg(String arg, String field, Consumer<Boolean> setter) {
        var str = arg.substring(("--" + field + "=").length()).trim();
        if (Utils.isBool(str)) {
            var b = Utils.getBool(str);
            setter.accept(b);
            return null;
        } else {
            return str + " is not a valid boolean";
        }
    }

    @SuppressWarnings({"DuplicatedCode", "SameParameterValue"})
    private static String timeArg(String arg, String field, Consumer<Integer> setter) {
        var str = arg.substring("--timeN=".length()).trim();
        if (Utils.isInteger(str)) {
            var n = Integer.parseInt(str);
            if (n < MIN_ALLOWED_SECONDS) {
                return field + " out of range: [" + MIN_ALLOWED_SECONDS + ", " + MAX_ALLOWED_SECONDS + "]";
            } else if (n > MAX_ALLOWED_SECONDS) {
                return field + " out of range: [" + MIN_ALLOWED_SECONDS + ", " + MAX_ALLOWED_SECONDS + "]";
            } else {
                setter.accept(n);
                return null;
            }
        } else {
            return str + " is not a valid integer";
        }
    }

    @Override
    public String toString() {
        return "Args{" +
            "pl1=" + pl1 +
            ", pl2=" + pl2 +
            ", enable2=" + enable2 +
            ", clamping1=" + clamping1 +
            ", clamping2=" + clamping2 +
            ", time1=" + time1 +
            ", forceIntel=" + forceIntel +
            ", forceAmd=" + forceAmd +
            ", printFormat=" + printFormat +
            ", logLevel=" + logLevel +
            ", msr=" + intelMsr +
            ", mmio=" + intelMmio +
            '}';
    }

    public String plFieldsToString() {
        return "Args{" +
            "pl1=" + pl1 +
            ", pl2=" + pl2 +
            ", enable2=" + enable2 +
            ", clamping1=" + clamping1 +
            ", clamping2=" + clamping2 +
            ", time1=" + time1 +
            '}';
    }

    public void from(Args that) {
        if (that.pl1 != null) {
            this.pl1 = that.pl1;
        }
        if (that.pl2 != null) {
            this.pl2 = that.pl2;
        }
        if (that.enable2 != null) {
            this.enable2 = that.enable2;
        }
        if (that.clamping1 != null) {
            this.clamping1 = that.clamping1;
        }
        if (that.clamping2 != null) {
            this.clamping2 = that.clamping2;
        }
        if (that.time1 != null) {
            this.time1 = that.time1;
        }
    }
}
