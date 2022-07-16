package net.cassite.tdpcli.daemon;

import io.vproxy.dep.vjson.JSON;
import io.vproxy.dep.vjson.deserializer.rule.IntRule;
import io.vproxy.dep.vjson.deserializer.rule.ObjectRule;
import io.vproxy.dep.vjson.deserializer.rule.Rule;
import io.vproxy.dep.vjson.util.ObjectBuilder;

public class Config {
    public static final Rule<Config> rule = new ObjectRule<>(Config::new)
        .put("interval", (o, n) -> o.interval = n, IntRule.get());

    public int interval = 5; // seconds

    public JSON.Instance<?> toJson() {
        return new ObjectBuilder()
            .put("interval", interval)
            .build();
    }
}
