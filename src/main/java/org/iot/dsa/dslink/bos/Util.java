package org.iot.dsa.dslink.bos;

import java.util.HashMap;
import java.util.Map;
import org.iot.dsa.dslink.restadapter.WebClientProxy;
import org.iot.dsa.io.json.JsonReader;
import org.iot.dsa.node.DSIObject;
import org.iot.dsa.node.DSList;
import org.iot.dsa.node.DSMap;
import okhttp3.Response;

public class Util {
    
    public static DSMap parseJsonMap(String jsonStr) {
        JsonReader jr = new JsonReader(jsonStr);
        try {
            return jr.getMap();
        } finally {
            jr.close();
        }
    }
    
    public static Map<String, DSMap> getOrganizations(WebClientProxy clientProxy) {
        Response resp = clientProxy.invoke(Constants.METHOD_GET, "https://api.buildingos.com/organizations/", new DSMap(), null);
        Map<String, DSMap> orgs = new HashMap<String, DSMap>();
        try {
            String respStr = resp.body().string();
            DSMap json = Util.parseJsonMap(respStr);
            DSList orgsjson = json.getList("data");
            for (DSIObject obj: orgsjson) {
                if (obj instanceof DSMap) {
                    DSMap org = (DSMap) obj;
                    orgs.put(org.getString("name"), org);
                }
            }
            
        } catch (Exception e) {
            return null;
        } finally {
            if (resp != null) {
                resp.close();
            }
        }
        return orgs;
    }

}
