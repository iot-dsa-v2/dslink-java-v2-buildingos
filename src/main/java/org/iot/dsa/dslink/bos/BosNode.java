package org.iot.dsa.dslink.bos;

import java.util.Map;
import org.iot.dsa.node.DSList;
import org.iot.dsa.node.DSMap;

public interface BosNode {
    
    public Map<String, DSMap> getChildMap();
    
    public default DSList getChildNames() {
        DSList names = new DSList();
        if (getChildMap() != null) {
            for (String name: getChildMap().keySet()) {
                names.add(name);
            }
        }
        return names;
    }
    
    public default String getChildUrl(String name) {
        if (getChildMap() != null) {
            DSMap child = getChildMap().get(name);
            if (child != null) {
                String url = child.getString("url");
                return url;
            }
        }
        return null;
    }

}
