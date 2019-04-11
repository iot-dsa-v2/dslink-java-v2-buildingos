package org.iot.dsa.dslink.bos;

import java.io.IOException;
import org.iot.dsa.dslink.restadapter.Constants;
import org.iot.dsa.dslink.restadapter.Util;
import org.iot.dsa.node.DSDouble;
import org.iot.dsa.node.DSElement;
import org.iot.dsa.node.DSFlexEnum;
import org.iot.dsa.node.DSIObject;
import org.iot.dsa.node.DSIValue;
import org.iot.dsa.node.DSInfo;
import org.iot.dsa.node.DSList;
import org.iot.dsa.node.DSLong;
import org.iot.dsa.node.DSMap;
import org.iot.dsa.node.DSMetadata;
import org.iot.dsa.node.DSString;
import org.iot.dsa.node.DSValueType;
import org.iot.dsa.node.action.ActionInvocation;
import org.iot.dsa.node.action.ActionResult;
import org.iot.dsa.node.action.DSAction;
import org.iot.dsa.util.DSException;
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
        declareDefault("Bulk Add Meters", makeBulkAddMetersAction());
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

    private DSAction makeAddMeterAction() {
        DSAction act = new DSAction() {
            
            @Override
            public ActionResult invoke(DSInfo target, ActionInvocation request) {
                ((BuildingNode) target.get()).addMeter(request.getParameters());
                return null;
            }

            @Override
            public void prepareParameter(DSInfo target, DSMap parameter) {
                DSMetadata paramMeta = new DSMetadata(parameter);
                if (paramMeta.getName().equals("Meter")) {
                    DSList range = ((BosNode) target.get()).getChildNames();
                    if (range.size() > 0) {
                        paramMeta.setType(DSFlexEnum.valueOf(range.getString(0), range));
                    }
                }
            }
        };
        act.addParameter("Meter", DSValueType.ENUM, null);
        act.addParameter("Subscribe Path", DSValueType.STRING, null);
        act.addDefaultParameter("Push Interval", DSDouble.valueOf(3600), "seconds");
        act.addDefaultParameter("Maximum Batch Size", DSLong.valueOf(50), "Maximum number of updates to put in a single REST request");
        return act;
    }
    
    private DSAction makeBulkAddMetersAction() {
        DSAction act = new DSAction() {
            
            @Override
            public void prepareParameter(DSInfo target, DSMap parameter) {
            }
            
            @Override
            public ActionResult invoke(DSInfo target, ActionInvocation request) {
                ((BuildingNode) target.get()).bulkAddMeters(request.getParameters());
                return null;
            }
        };
        act.addDefaultParameter("Meter Table", new DSList(), null);
        return act;
    }
    
    private DSAction makeCreateMeterAction() {
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
        act.addParameter("gateway", DSValueType.STRING, null);
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
        act.addDefaultParameter("Maximum Batch Size", DSLong.valueOf(50), "Maximum number of updates to put in a single REST request");
        return act;
    }
    
    private void addMeter(DSMap parameters) {
        String mname = parameters.getString("Meter");
        String url = getChildUrl(mname);
        String subPath = parameters.getString("Subscribe Path");
        double interval = parameters.getDouble("Push Interval");
        int maxBatchSize = parameters.getInt("Maximum Batch Size");
        if (url != null) {
            put(mname, new MeterNode(url, subPath, interval, maxBatchSize));
        }
    }
    
    private void bulkAddMeters(DSMap parameters) {
        DSList table = parameters.getList(Constants.RULE_TABLE);
        for (DSElement elem: table) {
            String mname, subPath;
            double interval;
            int maxBatchSize;
            if (elem instanceof DSMap) {
                DSMap row = (DSMap) elem;
                mname = row.getString("Meter");
                subPath = row.getString("Subscribe Path");
                interval = Util.getDouble(row, "Push Interval", 3600);
                maxBatchSize = (int) Util.getDouble(row, "Maximum Batch Size", 50);
                String url = getChildUrl(mname);
                put(mname, new MeterNode(url, subPath, interval, maxBatchSize));
            } else if (elem instanceof DSList) {
                DSList row = (DSList) elem;
                mname = row.getString(1);
                subPath = row.getString(2);
                interval = Util.getDouble(row, 3, 3600);
                maxBatchSize = (int) Util.getDouble(row, 4, 50);
                String url = getChildUrl(mname);
                put(mname, new MeterNode(url, subPath, interval, maxBatchSize));
            }
        }
    }
    
    private void createMeter(DSMap parameters) {
        String name = parameters.getString("displayName");
        String subPath = parameters.getString("Subscribe Path");
        double interval = parameters.getDouble("Push Interval");
        int maxBatchSize = parameters.getInt("Maximum Batch Size");
        DSIObject idObj = get("id");
        if (!(idObj instanceof DSIValue)) {
            warn("missing building id");
            return;
        }
        parameters.put("building", idObj.toString());
        Response resp = MainNode.getClientProxy().invoke("POST", "https://api.buildingos.com/meters/", new DSMap(), parameters.toString());
        
        if (resp != null) {
            try {
                DSMap json = BosUtil.getMapFromResponse(resp);
                String url = json.getString("url");
                if (url != null) {
                    put(name, new MeterNode(url, subPath, interval, maxBatchSize));
                }
                refresh();
            } catch (IOException e) {
                warn("", e);
                DSException.throwRuntime(e);
            }
        }
    }
}
