package net.cassite.tdpcli;

import java.util.function.Consumer;

import static net.cassite.tdpcli.Consts.allowedHelpVariations;

public class Args {
    private static final String helpMsg = """
        Usage:
          tdpcli                                 show all settings
          tdpcli -h|--help|-help|help            show this message
          tdpcli [options]
        Options:
          --pl<N>=<w>                            get or set long/short duration power consumption (Watts)
          --enable<N>=<b>                        get or enable or disable long/short duration power limit (bool)
          --clapping<N>=<b>                      get or enable or disable long/short duration power limit clapping (bool)
          --time<N>=<s>                          get or set long/short duration power limit time window (seconds)
          --force-intel                          use the `intel` way of modifying settings
          --force-amd                            use the `amd` way of modifying settings

        Intel Options:
          --msr                                  use msr to get or set power limit
          --mmio                                 use mmio to get or set power limit
                                                 You can specify both --msr and --mmio when modifying, but not when retrieving
        Environment Variables:
          TDPCLI_RW_EVERYTHING_PATH              the path to rw.exe (required for intel processors)
                                                 will use 'C:\\Program Files\\RW-Everything\\RW.exe' by default
          TDPCLI_RYZENADJ_PATH                   the path to ryzenadj.exe (required for amd processors)
                                                 will use '' by default
        """.trim();
    private static final int MAX_ALLOWED_WATTS = 200;
    private static final int MIN_ALLOWED_WATTS = 10;
    private static final int MAX_ALLOWED_SECONDS = 60;
    private static final int MIN_ALLOWED_SECONDS = 1;

    public Integer pl1 = null;
    public Integer pl2 = null;
    public Boolean enable1 = null;
    public Boolean enable2 = null;
    public Boolean clapping1 = null;
    public Boolean clapping2 = null;
    public Integer time1 = null;
    public Integer time2 = null;
    public boolean forceIntel = false;
    public boolean forceAmd = false;
    public boolean msr;
    public boolean mmio;

    public boolean isModify() {
        return pl1 != null
            || pl2 != null
            || enable1 != null
            || enable2 != null
            || clapping1 != null
            || clapping2 != null
            || time1 != null
            || time2 != null;
    }

    public int parse(String[] args) {
        String badArg;
        for (String arg : args) {
            badArg = null;
            if (allowedHelpVariations.contains(arg)) {
                System.out.println(helpMsg);
                return -1;
            }
            if (arg.equals("--force-intel")) {
                forceIntel = true;
            } else if (arg.equals("--force-amd")) {
                forceAmd = true;
            } else if (arg.equals("--msr")) {
                msr = true;
            } else if (arg.equals("--mmio")) {
                mmio = true;
            } else if (arg.startsWith("--pl1=")) {
                badArg = plArg(arg, "pl1", n -> pl1 = n);
            } else if (arg.startsWith("--pl2=")) {
                badArg = plArg(arg, "pl2", n -> pl2 = n);
            } else if (arg.startsWith("--enable1=")) {
                badArg = boolArg(arg, "enable1", b -> enable1 = b);
            } else if (arg.startsWith("--enable2=")) {
                badArg = boolArg(arg, "enable2", b -> enable2 = b);
            } else if (arg.startsWith("--clapping1=")) {
                badArg = boolArg(arg, "clapping1", b -> clapping1 = b);
            } else if (arg.startsWith("--clapping2=")) {
                badArg = boolArg(arg, "clapping2", b -> clapping2 = b);
            } else if (arg.startsWith("--time1=")) {
                badArg = timeArg(arg, "time1", n -> time1 = n);
            } else if (arg.startsWith("--time2=")) {
                badArg = timeArg(arg, "time2", n -> time2 = n);
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

    @SuppressWarnings("DuplicatedCode")
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
}
