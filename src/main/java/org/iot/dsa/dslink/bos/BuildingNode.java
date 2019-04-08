package org.iot.dsa.dslink.bos;

import java.io.IOException;
import org.iot.dsa.node.DSBool;
import org.iot.dsa.node.DSDouble;
import org.iot.dsa.node.DSFlexEnum;
import org.iot.dsa.node.DSIObject;
import org.iot.dsa.node.DSInfo;
import org.iot.dsa.node.DSList;
import org.iot.dsa.node.DSMap;
import org.iot.dsa.node.DSMetadata;
import org.iot.dsa.node.DSString;
import org.iot.dsa.node.DSValueType;
import org.iot.dsa.node.action.ActionInvocation;
import org.iot.dsa.node.action.ActionResult;
import org.iot.dsa.node.action.DSAction;
import okhttp3.Response;

public class BuildingNode extends BosObjectNode {
    
    public BuildingNode() {
        super();
    }
    
    public BuildingNode(String url) {
        super(url);
    }
    
    @Override
    protected void declareDefaults() {
        super.declareDefaults();
        declareDefault("Add Meter", makeAddMeterAction());
        declareDefault("Create Meter", makeCreateMeterAction());
    }

    @Override
    protected DSList getChildList() {
        DSIObject meters = get("meters");
        if (meters instanceof DSList) {
            return (DSList) meters;
        }
        return null;
    }

    private DSIObject makeAddMeterAction() {
        DSAction act = new DSAction() {
            
            @Override
            public ActionResult invoke(DSInfo target, ActionInvocation request) {
                ((BuildingNode) target.get()).addMeter(request.getParameters());
                return null;
            }

            @Override
            public void prepareParameter(DSInfo target, DSMap parameter) {
                DSList range = ((BosNode) target.get()).getChildNames();
                if (range.size() > 0) {
                    new DSMetadata(parameter).setType(DSFlexEnum.valueOf(range.getString(0), range));
                }
            }
        };
        act.addParameter("Meter", DSValueType.ENUM, null);
        act.addParameter("Subscribe Path", DSValueType.STRING, null);
        act.addDefaultParameter("Push Interval", DSDouble.valueOf(3600), "seconds");
        return act;
    }
    
    private DSIObject makeCreateMeterAction() {
        DSAction act = new DSAction() {

            @Override
            public ActionResult invoke(DSInfo target, ActionInvocation request) {
                ((BuildingNode) target.get()).createMeter(request.getParameters());
                return null;
            }

            @Override
            public void prepareParameter(DSInfo target, DSMap parameter) {
            }  
        };
        act.addParameter("displayName", DSValueType.STRING, null);
        act.addDefaultParameter("status", DSString.EMPTY, null);
        act.addDefaultParameter("storageUnit", DSString.EMPTY, null);
        act.addDefaultParameter("displayUnit", DSString.EMPTY, null);
        act.addDefaultParameter("sourceUnit", DSString.EMPTY, null);
        act.addDefaultParameter("resourceType", DSString.EMPTY, null);
        act.addDefaultParameter("vendorMeterId", DSString.EMPTY, null);
        act.addDefaultParameter("defaultTimescale", DSString.EMPTY, null);
        act.addDefaultParameter("flatlineThreshold", DSString.EMPTY, null);
        act.addDefaultParameter("scope", DSString.EMPTY, null);
        act.addDefaultParameter("readingType", DSString.EMPTY, null);
        act.addParameter("Subscribe Path", DSValueType.STRING, null);
        act.addDefaultParameter("Push Interval", DSDouble.valueOf(3600), "seconds");
        return act;
    }
    
    private void addMeter(DSMap parameters) {
        String mname = parameters.getString("Meter");
        String url = getChildUrl(mname);
        String subPath = parameters.getString("Subscribe Path");
        double interval = parameters.getDouble("Push Interval");
        if (url != null) {
            put(mname, new MeterNode(url, subPath, interval));
        }
    }
    
    private void createMeter(DSMap parameters) {
        String name = parameters.getString("displayName");
        String subPath = parameters.getString("Subscribe Path");
        double interval = parameters.getDouble("Push Interval");
        //TODO include building and gateway info
        Response resp = MainNode.getClientProxy().invoke("POST", "https://api.buildingos.com/meters/", new DSMap(), parameters.toString());
        
        String respStr;
        try {
            respStr = resp.body().string();
            DSMap json = Util.parseJsonMap(respStr);
            String url = json.getString("url");
            if (url != null) {
                put(name, new MeterNode(url, subPath, interval));
            }
            refresh();
        } catch (IOException e) {
            warn("", e);
        } finally {
            if (resp != null) {
                resp.close();
            }
        }
        
    }
}
