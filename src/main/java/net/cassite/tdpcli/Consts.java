package net.cassite.tdpcli;

import java.util.Set;

public class Consts {
    public static final Set<String> allowedHelpVariations = Set.of("-h", "--help", "-help", "help", "/h", "/help");
    public static final Set<String> intelArch = Set.of("Alder Lake");
    public static final Set<String> amdArch = Set.of();

    private Consts() {
    }
}
