package org.iot.dsa.dslink.bos;

import java.util.HashMap;
import java.util.Map;
import org.iot.dsa.node.DSElement;
import org.iot.dsa.node.DSFlexEnum;
import org.iot.dsa.node.DSList;
import org.iot.dsa.node.DSMap;
import org.iot.dsa.node.DSMetadata;
import org.iot.dsa.node.DSValueType;

public class BosParameter extends DSMetadata {
    
    private Map<String, DSElement> enumVals = new HashMap<String, DSElement>();
    
    public BosParameter(String name, DSList enumDetails) {
        super();
        setName(name);
        DSList dispNames = new DSList();
        for(DSElement elem: enumDetails) {
            if (elem instanceof DSMap) {
                DSMap enumObj = (DSMap) elem;
                String dispName = enumObj.getString("displayName");
                DSElement id = enumObj.get("id");
                if (dispName != null && id != null) {
                    enumVals.put(dispName, id);
                    dispNames.add(dispName);
                }
            }
        }
        if (dispNames.isEmpty()) {
            setType(DSValueType.ENUM);
        } else {
            setType(DSFlexEnum.valueOf(dispNames.getString(0), dispNames));
        }
    }
    
    public DSElement getId(String name) {
        return enumVals.get(name);
    }

}
