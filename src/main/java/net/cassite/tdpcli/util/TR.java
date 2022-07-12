// code copied from https://github.com/wkgcass/vproxy

package net.cassite.tdpcli.util;

import java.util.ArrayList;
import java.util.List;

public class TR {
    final List<String> columns = new ArrayList<>();

    public TR td(String col) {
        columns.add(col);
        return this;
    }
}
