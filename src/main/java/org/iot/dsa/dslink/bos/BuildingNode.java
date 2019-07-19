package org.iot.dsa.dslink.bos;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
        declareDefault(BosConstants.ACTION_ADD_METER, makeAddMeterAction());
        declareDefault(BosConstants.ACTION_ADD_METERS_BULK, makeBulkAddMetersAction());
    }

    @Override
    protected DSList getChildList() {
        DSIObject meters = get(BosApiConstants.METERS);
        if (meters instanceof DSList) {
            return (DSList) meters;
        }
        return null;
    }
    
    @Override
    protected void refresh() {
        super.refresh();
        put(BosConstants.ACTION_CREATE_METER, makeCreateMeterAction());
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
                if (paramMeta.getName().equals(BosConstants.METER)) {
                    DSList range = ((BosNode) target.get()).getChildNames();
                    if (range.size() > 0) {
                        paramMeta.setType(DSFlexEnum.valueOf(range.getString(0), range));
                    }
                }
            }
        };
        act.addParameter(BosConstants.METER, DSValueType.ENUM, null);
        act.addParameter(Constants.SUB_PATH, DSValueType.STRING, null);
        act.addDefaultParameter(BosConstants.PUSH_INTERVAL, DSDouble.valueOf(3600), "seconds");
        act.addDefaultParameter(Constants.MAX_BATCH_SIZE, DSLong.valueOf(50), "Maximum number of updates to put in a single REST request");
        act.addDefaultParameter(BosConstants.MIN_UPDATE_INTERVAL, DSDouble.valueOf(60), "seconds");
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
        act.addDefaultParameter(BosConstants.METER_TABLE, new DSList(), null);
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
                if (paramMeta.getName().equals(BosApiConstants.GATEWAY)) {
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
        act.addParameter(BosApiConstants.DISP_NAME, DSValueType.STRING, null);
        act.addParameter(BosApiConstants.GATEWAY, DSValueType.ENUM, null);
        Map<String, BosParameter> enumParams = BosUtil.getMeterEnumParams(MainNode.getClientProxy());
        if (enumParams == null) {
            return null;
        }

        act.addParameter(enumParams.get("sourceUnit").getMap());
        act.addParameter(enumParams.get("resourceType").getMap());
        act.addParameter(enumParams.get("readingType").getMap());
        act.addParameter(enumParams.get("displayUnit").getMap());

//        act.addDefaultParameter(BosApiConstants.STORAGE_UNIT, DSString.EMPTY, null);  
//        act.addDefaultParameter(BosApiConstants.VENDOR_METER_ID, DSString.EMPTY, null);
        act.addParameter(Constants.SUB_PATH, DSValueType.STRING, null);
        act.addDefaultParameter(BosConstants.PUSH_INTERVAL, DSDouble.valueOf(3600), "seconds");
        act.addDefaultParameter(Constants.MAX_BATCH_SIZE, DSLong.valueOf(50), "Maximum number of updates to put in a single REST request");
        act.addDefaultParameter(BosConstants.MIN_UPDATE_INTERVAL, DSDouble.valueOf(60), "seconds");
        return act;
    }
    
    private void addMeter(DSMap parameters) {
        String mname = parameters.getString(BosConstants.METER);
        String url = getChildUrl(mname);
        String subPath = parameters.getString(Constants.SUB_PATH);
        double interval = parameters.getDouble(BosConstants.PUSH_INTERVAL);
        int maxBatchSize = parameters.getInt(Constants.MAX_BATCH_SIZE);
        double minUpdateInterval = parameters.getDouble(BosConstants.MIN_UPDATE_INTERVAL);
        if (url != null) {
            put(mname, new MeterNode(url, subPath, interval, maxBatchSize, minUpdateInterval));
        }
    }
    
    private void bulkAddMeters(DSMap parameters) {
        DSList table = parameters.getList(BosConstants.METER_TABLE);
        for (DSElement elem: table) {
            String mname, subPath;
            double interval;
            int maxBatchSize;
            double minUpdateInterval;
            if (elem instanceof DSMap) {
                DSMap row = (DSMap) elem;
                mname = row.getString(BosConstants.METER);
                subPath = row.getString(Constants.SUB_PATH);
                interval = Util.getDouble(row, BosConstants.PUSH_INTERVAL, 3600);
                maxBatchSize = (int) Util.getDouble(row, Constants.MAX_BATCH_SIZE, 50);
                minUpdateInterval = Util.getDouble(row, BosConstants.MIN_UPDATE_INTERVAL, 60);
                String url = getChildUrl(mname);
                put(mname, new MeterNode(url, subPath, interval, maxBatchSize, minUpdateInterval));
            } else if (elem instanceof DSList) {
                DSList row = (DSList) elem;
                mname = row.getString(1);
                subPath = row.size() > 2 ? row.getString(2) : null;
                interval = Util.getDouble(row, 3, 3600);
                maxBatchSize = (int) Util.getDouble(row, 4, 50);
                minUpdateInterval = Util.getDouble(row, 5, 60);
                String url = getChildUrl(mname);
                put(mname, new MeterNode(url, subPath, interval, maxBatchSize, minUpdateInterval));
            }
        }
    }
    
    private void createMeter(DSMap parameters) {
        String name = parameters.getString(BosApiConstants.DISP_NAME);
        String subPath = parameters.getString(Constants.SUB_PATH);
        parameters.remove(Constants.SUB_PATH);
        double interval = parameters.remove(BosConstants.PUSH_INTERVAL).toDouble();
        int maxBatchSize = parameters.remove(Constants.MAX_BATCH_SIZE).toInt();
        double minUpdateInterval = parameters.remove(BosConstants.MIN_UPDATE_INTERVAL).toDouble();
        DSIObject idObj = get(BosApiConstants.ID);
        if (!(idObj instanceof DSIValue)) {
            warn("missing building id");
            return;
        }
        parameters.put(BosApiConstants.BUILDING, idObj.toString());
        
        Map<String, BosParameter> enumParams = BosUtil.getMeterEnumParams(MainNode.getClientProxy());
        if (enumParams == null) {
            return;
        }
        for (BosParameter param: enumParams.values()) {
            String paramName = param.getName();
            String disp = parameters.getString(paramName);
            if (disp != null) {
                parameters.put(paramName, param.getId(disp));
            }
        }
        String gatewayName = parameters.getString(BosApiConstants.GATEWAY);
        if (gatewayName != null) {
            String gatewayId  = MainNode.getGatewayList().get(gatewayName);
            if (gatewayId != null) {
                parameters.put(BosApiConstants.GATEWAY, gatewayId);
            }
        }
        
        Response resp = MainNode.getClientProxy().invoke(BosConstants.METHOD_POST, BosConstants.METERS_URL, new DSMap(), parameters.toString());
        
        if (resp != null) {
            try {
                DSMap json = BosUtil.getMapFromResponse(resp);
                String url = json.getString(BosApiConstants.URL);
                if (url != null) {
                    put(name, new MeterNode(url, subPath, interval, maxBatchSize, minUpdateInterval));
                }
                refresh();
            } catch (IOException e) {
                warn("", e);
                DSException.throwRuntime(e);
            }
        }
    }
}
