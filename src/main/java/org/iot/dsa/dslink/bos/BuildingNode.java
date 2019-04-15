package org.iot.dsa.dslink.bos;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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
    }

    @Override
    protected DSList getChildList() {
        DSIObject meters = get("meters");
        if (meters instanceof DSList) {
            return (DSList) meters;
        }
        return null;
    }
    
    @Override
    protected void refresh() {
        super.refresh();
        put("Create Meter", makeCreateMeterAction());
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
                DSMetadata paramMeta = new DSMetadata(parameter);
                if (paramMeta.getName().equals("gateway")) {
                    DSList range = new DSList();
                    List<String> rangeList = new ArrayList<String>(MainNode.getGatewayList().keySet());
                    rangeList.sort(null);
                    for (String key: rangeList) {
                        range.add(key);
                    }
                    if (range.size() > 0) {
                        paramMeta.setType(DSFlexEnum.valueOf(range.getString(0), range));
                    }
                }
            }  
        };
        act.addParameter("displayName", DSValueType.STRING, null);
        act.addParameter("gateway", DSValueType.ENUM, null);
        List<BosParameter> enumParams = BosUtil.getMeterEnumParams(MainNode.getClientProxy());
        if (enumParams == null) {
            return null;
        }
        for (BosParameter param: enumParams) {
            act.addParameter(param.getMap());
        }
        act.addDefaultParameter("storageUnit", DSString.EMPTY, null);  
        act.addDefaultParameter("vendorMeterId", DSString.EMPTY, null);
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
        DSList table = parameters.getList("Meter Table");
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
                subPath = row.size() > 2 ? row.getString(2) : null;
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
        
        List<BosParameter> enumParams = BosUtil.getMeterEnumParams(MainNode.getClientProxy());
        if (enumParams == null) {
            return;
        }
        for (BosParameter param: enumParams) {
            String paramName = param.getName();
            String disp = parameters.getString(paramName);
            if (disp != null) {
                parameters.put(paramName, param.getId(disp));
            }
        }
        String gatewayName = parameters.getString("gateway");
        if (gatewayName != null) {
            String gatewayId  = MainNode.getGatewayList().get(gatewayName);
            if (gatewayId != null) {
                parameters.put("gateway", gatewayId);
            }
        }
        
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
