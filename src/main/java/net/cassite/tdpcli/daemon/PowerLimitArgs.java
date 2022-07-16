package net.cassite.tdpcli.daemon;

import io.vproxy.dep.vjson.deserializer.rule.BoolRule;
import io.vproxy.dep.vjson.deserializer.rule.IntRule;
import io.vproxy.dep.vjson.deserializer.rule.ObjectRule;
import io.vproxy.dep.vjson.deserializer.rule.Rule;
import net.cassite.tdpcli.Args;

public class PowerLimitArgs {
    public Limit pl1 = new Limit();
    public Limit pl2 = new Limit();

    public static final class Limit {
        public Boolean enabled;
        public Integer power; // watts
        public Boolean clamping;
        public Integer time; // seconds

        public static final Rule<Limit> rule = new ObjectRule<>(Limit::new)
            .put("enabled", (o, b) -> o.enabled = b, BoolRule.get())
            .put("power", (o, d) -> o.power = d, IntRule.get())
            .put("clamping", (o, b) -> o.clamping = b, BoolRule.get())
            .put("time", (o, d) -> o.time = d, IntRule.get());
    }

    public static final Rule<PowerLimitArgs> rule = new ObjectRule<>(PowerLimitArgs::new)
        .put("pl1", (o, oo) -> o.pl1 = oo, Limit.rule)
        .put("pl2", (o, oo) -> o.pl2 = oo, Limit.rule);

    public String checkAndAssignToArgs(Args args) {
        if (pl1.power != null) {
            int pl1 = this.pl1.power;
            if (pl1 < Args.MIN_ALLOWED_WATTS || pl1 > Args.MAX_ALLOWED_WATTS) {
                return "pl1 out of range: [" + Args.MIN_ALLOWED_WATTS + ", " + Args.MAX_ALLOWED_WATTS + "]";
            }
            args.pl1 = pl1;
        }
        if (pl1.time != null) {
            int time1 = this.pl1.time;
            if (time1 < Args.MIN_ALLOWED_SECONDS || time1 > Args.MAX_ALLOWED_SECONDS) {
                return "time1 out of range: [" + Args.MIN_ALLOWED_SECONDS + ", " + Args.MAX_ALLOWED_SECONDS + "]";
            }
            args.time1 = time1;
        }
        if (pl1.clamping != null) {
            args.clamping1 = pl1.clamping;
        }
        if (pl2.power != null) {
            int pl2 = this.pl2.power;
            if (pl2 < Args.MIN_ALLOWED_WATTS || pl2 > Args.MAX_ALLOWED_WATTS) {
                return "pl2 out of range: [" + Args.MIN_ALLOWED_WATTS + ", " + Args.MAX_ALLOWED_WATTS + "]";
            }
            args.pl2 = pl2;
        }
        if (pl2.enabled != null) {
            args.enable2 = pl2.enabled;
        }
        if (pl2.clamping != null) {
            args.clamping2 = pl2.clamping;
        }
        return null;
    }
}
