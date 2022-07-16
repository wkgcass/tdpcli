module tdpcli {
    requires kotlin.stdlib;
    requires kotlinx.coroutines.core.jvm;
    requires com.github.oshi;
    requires io.vproxy.all;

    exports net.cassite.tdpcli;
    exports net.cassite.tdpcli.daemon;
    exports net.cassite.tdpcli.util;
}
