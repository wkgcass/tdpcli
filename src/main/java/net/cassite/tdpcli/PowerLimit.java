package net.cassite.tdpcli;

import io.vproxy.dep.vjson.JSON;
import io.vproxy.dep.vjson.deserializer.rule.BoolRule;
import io.vproxy.dep.vjson.deserializer.rule.DoubleRule;
import io.vproxy.dep.vjson.deserializer.rule.ObjectRule;
import io.vproxy.dep.vjson.deserializer.rule.Rule;
import io.vproxy.dep.vjson.util.ObjectBuilder;
import net.cassite.tdpcli.util.TableBuilder;

public class PowerLimit {
    public boolean locked = false;
    public Limit pl1 = new Limit();
    public Limit pl2 = new Limit();

    public static final class Limit {
        public boolean enabled;
        public double power; // watts
        public boolean clamping;
        public double time; // seconds

        public static final Rule<Limit> rule = new ObjectRule<>(Limit::new)
            .put("enabled", (o, b) -> o.enabled = b, BoolRule.get())
            .put("power", (o, d) -> o.power = d, DoubleRule.get())
            .put("clamping", (o, b) -> o.clamping = b, BoolRule.get())
            .put("time", (o, d) -> o.time = d, DoubleRule.get());

        public JSON.Object formatToJson() {
            return new ObjectBuilder()
                .put("enabled", enabled)
                .put("power", power)
                .put("clamping", clamping)
                .put("time", time)
                .build();
        }
    }

    public String formatToTable() {
        var table = new TableBuilder();
        table.tr().td("Property").td("Value").td("Option");
        table.tr().td("locked").td(locked ? "yes" : "no").td("");
        table.tr().td("pl1.enabled").td(pl1.enabled ? "yes" : "no").td("");
        table.tr().td("pl1.power").td(Double.toString(pl1.power)).td("--pl1");
        table.tr().td("pl1.clamping").td(pl1.clamping ? "yes" : "no").td("--clamping1");
        table.tr().td("pl1.time").td(Double.toString(pl1.time)).td("--time1");
        table.tr().td("pl2.enabled").td(pl2.enabled ? "yes" : "no").td("--enable2");
        table.tr().td("pl2.power").td(Double.toString(pl2.power)).td("--pl2");
        table.tr().td("pl2.clamping").td(pl2.clamping ? "yes" : "no").td("--clamping2");
        table.tr().td("pl2.time").td(Double.toString(pl2.time)).td("");
        return table.toString();
    }

    public static final Rule<PowerLimit> rule = new ObjectRule<>(PowerLimit::new)
        .put("locked", (o, b) -> o.locked = b, BoolRule.get())
        .put("pl1", (o, oo) -> o.pl1 = oo, Limit.rule)
        .put("pl2", (o, oo) -> o.pl2 = oo, Limit.rule);

    public JSON.Instance<?> formatToJson() {
        return new ObjectBuilder()
            .put("locked", locked)
            .putInst("pl1", pl1.formatToJson())
            .putInst("pl2", pl2.formatToJson())
            .build();
    }
}
