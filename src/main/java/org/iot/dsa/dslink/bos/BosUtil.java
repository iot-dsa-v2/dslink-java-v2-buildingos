package org.iot.dsa.dslink.bos;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.iot.dsa.dslink.restadapter.SubUpdate;
import org.iot.dsa.dslink.restadapter.WebClientProxy;
import org.iot.dsa.io.json.JsonReader;
import org.iot.dsa.node.DSElement;
import org.iot.dsa.node.DSIObject;
import org.iot.dsa.node.DSList;
import org.iot.dsa.node.DSMap;
import org.iot.dsa.node.DSMap.Entry;
import org.iot.dsa.node.DSStatus;
import org.iot.dsa.time.DSDateTime;
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
        String url = BosConstants.ORGS_URL;
        Map<String, DSMap> orgs = new HashMap<String, DSMap>();
        while (url != null) {
            Response resp = clientProxy.invoke(BosConstants.METHOD_GET, url, new DSMap(), null);
            try {
                DSMap json = getMapFromResponse(resp);
                DSList orgsjson = json.getList(BosApiConstants.DATA);
                for (DSIObject obj: orgsjson) {
                    if (obj instanceof DSMap) {
                        DSMap org = (DSMap) obj;
                        orgs.put(org.getString(BosApiConstants.NAME), org);
                    }
                }
                url = getNextPageUrl(json); 
            } catch (Exception e) {
                url = null;
            }
        }
        return orgs;
    }
    
    private static String getNextPageUrl(DSMap json) {
        DSMap links = json.getMap("links");
        if (links == null) {
            return null;
        }
        String next = links.getString("next");
        if (next == null || next.equals("null")) {
            return null;
        } else {
            return next;
        }
    }
    
    private static Map<String, BosParameter> getEnumParams(WebClientProxy clientProxy, String url) {
        Response resp = clientProxy.invoke(BosConstants.METHOD_GET, url, new DSMap(), null);
        try {
            DSMap json = getMapFromResponse(resp);
            DSMap definitions = json.getMap(BosApiConstants.META).getMap(BosApiConstants.DEFINITIONS);
            Map<String, BosParameter> enumParams = new HashMap<String, BosParameter>();
            for (Entry defn: definitions) {
                String defnName = defn.getKey();
                String defnUrl = defn.getValue().toString();
                Response defnResp = clientProxy.invoke(BosConstants.METHOD_GET, defnUrl, new DSMap(), null);
                try {
                    DSMap defnjson = getMapFromResponse(defnResp);
                    DSList defnData = defnjson.getList(BosApiConstants.DATA);
                    enumParams.put(defnName, new BosParameter(defnName, defnData));
                } catch (Exception e) { 
                }
            }
            return enumParams;
        } catch (Exception e) {
            return null;
        }
    }
    
    private static Map<String, BosParameter> buildingEnumParams = null;
    public static Map<String, BosParameter> getBuildingEnumParams(WebClientProxy clientProxy) {
        if (buildingEnumParams == null) {
            buildingEnumParams = getEnumParams(clientProxy, BosConstants.BUILDINGS_URL);
        }
        return buildingEnumParams;
    }
    
    private static Map<String, BosParameter> meterEnumParams = null;
    public static Map<String, BosParameter> getMeterEnumParams(WebClientProxy clientProxy) {
        if (meterEnumParams == null) {
            meterEnumParams = getEnumParams(clientProxy, BosConstants.METERS_URL);
        }
        return meterEnumParams;
    }
    
    public static Map<String, String> getGatewayList(WebClientProxy clientProxy) {
        String url = BosConstants.GATEWAYS_URL;
        Map<String, String> gatewayList = new HashMap<String, String>();
        while (url != null) {
            Response resp = clientProxy.invoke(BosConstants.METHOD_GET, url, new DSMap(), null);
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
                url = getNextPageUrl(json);
            } catch (Exception e) {
                url = null;
            }
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
    
    public static SubUpdate parseSubUpdate(DSElement record) {
        if (record.isMap()) {
            return parseSubUpdate(record.toMap());
        } else if (record.isList()) {
            return parseSubUpdate(record.toList());
        }
        return null;
    }
    
    private static SubUpdate parseSubUpdate(DSMap record) {
       String tsStr = record.getString("timestamp");
       String val = record.getString("value");
       String status = record.getString("status");
       long ts = parseTs(tsStr);
       if (ts == -1 || val == null) {
           return null;
       }
       if (status == null) {
           status = DSStatus.ok.toString();
       }
       return new SubUpdate(tsStr, val, status, ts);
    }
    
    private static SubUpdate parseSubUpdate(DSList record) {
        if (record.size() < 2) {
            return null;
        }
        String tsStr = record.getString(0);
        long ts = parseTs(tsStr);
        int startInd = 0;
        if (ts == -1) {
            startInd = 1;
            tsStr = record.getString(1);
            ts = parseTs(tsStr);
        }
        if (ts == -1 || startInd + 1 >= record.size()) {
            return null;
        }
        String val = record.getString(startInd + 1);
        String status = startInd + 2 < record.size() ? record.getString(startInd + 2) : DSStatus.ok.toString();
        
        return new SubUpdate(tsStr, val, status, ts);
    }
    
    public static long parseTs(String tsStr) {
        try {
            return DSDateTime.valueOf(tsStr).timeInMillis();
        } catch (Exception e) {
        }
        return -1;
    }
}
