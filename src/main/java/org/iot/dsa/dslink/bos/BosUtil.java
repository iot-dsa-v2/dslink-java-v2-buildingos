package org.iot.dsa.dslink.bos;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.iot.dsa.dslink.restadapter.WebClientProxy;
import org.iot.dsa.io.json.JsonReader;
import org.iot.dsa.node.DSElement;
import org.iot.dsa.node.DSIObject;
import org.iot.dsa.node.DSList;
import org.iot.dsa.node.DSMap;
import org.iot.dsa.node.DSMap.Entry;
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
        Response resp = clientProxy.invoke(BosConstants.METHOD_GET, BosConstants.ORGS_URL, new DSMap(), null);
        Map<String, DSMap> orgs = new HashMap<String, DSMap>();
        try {
            DSMap json = getMapFromResponse(resp);
            DSList orgsjson = json.getList(BosApiConstants.DATA);
            for (DSIObject obj: orgsjson) {
                if (obj instanceof DSMap) {
                    DSMap org = (DSMap) obj;
                    orgs.put(org.getString(BosApiConstants.NAME), org);
                }
            } 
        } catch (Exception e) {
            return null;
        }
        return orgs;
    }
    
    private static List<BosParameter> getEnumParams(WebClientProxy clientProxy, String url) {
        Response resp = clientProxy.invoke(BosConstants.METHOD_GET, url, new DSMap(), null);
        try {
            DSMap json = getMapFromResponse(resp);
            DSMap definitions = json.getMap(BosApiConstants.META).getMap(BosApiConstants.DEFINITIONS);
            List<BosParameter> enumParams = new ArrayList<BosParameter>();
            for (Entry defn: definitions) {
                String defnName = defn.getKey();
                String defnUrl = defn.getValue().toString();
                Response defnResp = clientProxy.invoke(BosConstants.METHOD_GET, defnUrl, new DSMap(), null);
                try {
                    DSMap defnjson = getMapFromResponse(defnResp);
                    DSList defnData = defnjson.getList(BosApiConstants.DATA);
                    enumParams.add(new BosParameter(defnName, defnData));
                } catch (Exception e) { 
                }
            }
            return enumParams;
        } catch (Exception e) {
            return null;
        }
    }
    
    private static List<BosParameter> buildingEnumParams = null;
    public static List<BosParameter> getBuildingEnumParams(WebClientProxy clientProxy) {
        if (buildingEnumParams == null) {
            buildingEnumParams = getEnumParams(clientProxy, BosConstants.BUILDINGS_URL);
        }
        return buildingEnumParams;
    }
    
    private static List<BosParameter> meterEnumParams = null;
    public static List<BosParameter> getMeterEnumParams(WebClientProxy clientProxy) {
        if (meterEnumParams == null) {
            meterEnumParams = getEnumParams(clientProxy, BosConstants.METERS_URL);
        }
        return meterEnumParams;
    }
    
    public static Map<String, String> getGatewayList(WebClientProxy clientProxy) {
        Response resp = clientProxy.invoke(BosConstants.METHOD_GET, BosConstants.GATEWAYS_URL, new DSMap(), null);
        Map<String, String> gatewayList = new HashMap<String, String>();
        try {
            DSMap json = getMapFromResponse(resp);
            DSList data = json.getList(BosApiConstants.DATA);
            for (DSElement elem: data) {
                if (elem instanceof DSMap) {
                    String id = ((DSMap) elem).getString(BosApiConstants.ID);
                    String name = ((DSMap) elem).getString(BosApiConstants.NAME);
                    gatewayList.put(name, id);
                }
            }
        } catch (Exception e) { 
        }
        return gatewayList;
    }
    
    public static String splitCamelCase(String s) {
        return s.replaceAll(
            String.format("%s|%s|%s",
               "(?<=[A-Z])(?=[A-Z][a-z])",
               "(?<=[^A-Z])(?=[A-Z])",
               "(?<=[A-Za-z])(?=[^A-Za-z])"
            ),
            " "
         );
    }
}
