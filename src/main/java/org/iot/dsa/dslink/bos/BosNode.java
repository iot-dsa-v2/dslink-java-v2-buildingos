package org.iot.dsa.dslink.bos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.iot.dsa.node.DSList;
import org.iot.dsa.node.DSMap;

public interface BosNode {
    
    public Map<String, DSMap> getChildMap();
    
    public default DSList getChildNames() {
        DSList names = new DSList();
        if (getChildMap() != null) {
            List<String> keyList = new ArrayList<String>(getChildMap().keySet());
            keyList.sort(null);
            for (String name: keyList) {
                names.add(name);
            }
        }
        return names;
    }
    
    public default String getChildUrl(String name) {
        if (getChildMap() != null) {
            DSMap child = getChildMap().get(name);
            if (child != null) {
                String url = child.getString(BosApiConstants.URL);
                return url;
            }
        }
        return null;
    }

}
