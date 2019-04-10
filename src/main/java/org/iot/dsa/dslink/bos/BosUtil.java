package org.iot.dsa.dslink.bos;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.iot.dsa.dslink.restadapter.WebClientProxy;
import org.iot.dsa.io.json.JsonReader;
import org.iot.dsa.node.DSIObject;
import org.iot.dsa.node.DSList;
import org.iot.dsa.node.DSMap;
import okhttp3.Response;

public class BosUtil {
    
    public static DSMap parseJsonMap(String jsonStr) {
        JsonReader jr = new JsonReader(jsonStr);
        try {
            return jr.getMap();
        } finally {
            jr.close();
        }
    }
    public static DSMap getMapFromResponse(Response resp) throws IOException {
        return parseJsonMap(getBodyFromResponse(resp));
    }
    
    public static String getBodyFromResponse(Response resp) throws IOException {
        try {
            return resp.body().string();
        } catch (IOException e) {
            throw e;
        } finally {
            resp.close();
        }
    }
    
    public static Map<String, DSMap> getOrganizations(WebClientProxy clientProxy) {
        Response resp = clientProxy.invoke(BosConstants.METHOD_GET, "https://api.buildingos.com/organizations/", new DSMap(), null);
        Map<String, DSMap> orgs = new HashMap<String, DSMap>();
        try {
            DSMap json = getMapFromResponse(resp);
            DSList orgsjson = json.getList("data");
            for (DSIObject obj: orgsjson) {
                if (obj instanceof DSMap) {
                    DSMap org = (DSMap) obj;
                    orgs.put(org.getString("name"), org);
                }
            } 
        } catch (Exception e) {
            return null;
        }
        return orgs;
    }

}
