package org.iot.dsa.dslink.bos;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;
import org.iot.dsa.DSRuntime;
import org.iot.dsa.DSRuntime.Timer;
import org.iot.dsa.dslink.DSIRequester;
import org.iot.dsa.dslink.requester.ErrorType;
import org.iot.dsa.dslink.requester.OutboundStream;
import org.iot.dsa.dslink.requester.OutboundSubscribeHandler;
import org.iot.dsa.dslink.restadapter.Constants;
import org.iot.dsa.dslink.restadapter.SubUpdate;
import org.iot.dsa.dslink.restadapter.UpdateSender;
import org.iot.dsa.dslink.restadapter.Util;
import org.iot.dsa.node.DSDouble;
import org.iot.dsa.node.DSElement;
import org.iot.dsa.node.DSFlexEnum;
import org.iot.dsa.node.DSINumber;
import org.iot.dsa.node.DSIObject;
import org.iot.dsa.node.DSIValue;
import org.iot.dsa.node.DSInfo;
import org.iot.dsa.node.DSInt;
import org.iot.dsa.node.DSList;
import org.iot.dsa.node.DSLong;
import org.iot.dsa.node.DSMap;
import org.iot.dsa.node.DSMetadata;
import org.iot.dsa.node.DSStatus;
import org.iot.dsa.node.DSString;
import org.iot.dsa.node.DSValueType;
import org.iot.dsa.node.action.ActionInvocation;
import org.iot.dsa.node.action.ActionResult;
import org.iot.dsa.node.action.ActionSpec;
import org.iot.dsa.node.action.ActionTable;
import org.iot.dsa.node.action.ActionSpec.ResultType;
import org.iot.dsa.node.action.DSAction;
import org.iot.dsa.table.DSIRowCursor;
import org.iot.dsa.table.SimpleTable;
import org.iot.dsa.time.DSDateTime;
import org.iot.dsa.util.DSException;
import okhttp3.Response;

public class MeterNode extends BosObjectNode implements OutboundSubscribeHandler, Runnable, UpdateSender {
    private static final String METHOD = "POST";
    
    private String subPath = "";
    private OutboundStream stream;
    private DSIValue streamParams;
    private double interval = -1;
    private int maxBatchSize = -1;
    private Timer future;
    
    private DSInfo getDataAct = getInfo("Get Data");
    private DSInfo lastRespCode = getInfo(Constants.LAST_RESPONSE_CODE);
    private DSInfo lastRespData = getInfo(Constants.LAST_RESPONSE_DATA);
    private DSInfo lastRespTs = getInfo(Constants.LAST_RESPONSE_TS);

    public MeterNode() {
        super();
    }
    
    public MeterNode(String url, String subPath, double interval, int maxBatchSize) {
        super(url);
        this.subPath = subPath;
        this.interval = interval;
        this.maxBatchSize = maxBatchSize;
    }
    
    @Override
    protected void declareDefaults() {
        super.declareDefaults();
        declareDefault("Edit", makeEditAction());
        declareDefault("Get Data", makeGetDataAction());
        declareDefault(Constants.LAST_RESPONSE_CODE, DSInt.NULL).setReadOnly(true);
        declareDefault(Constants.LAST_RESPONSE_DATA, DSString.EMPTY).setReadOnly(true);
        declareDefault(Constants.LAST_RESPONSE_TS, DSString.EMPTY).setReadOnly(true);
    }

    @Override
    protected void onStarted() {
        super.onStarted();
        DSIObject pathobj = get("Subscribe Path");
        if (pathobj instanceof DSString) {
            this.subPath = ((DSString) pathobj).toString();
        }
        DSIObject intervobj = get("Push Interval");
        if (intervobj instanceof DSINumber) {
            interval = ((DSINumber) intervobj).toDouble();
        }
        DSIObject batchSizeObj = get("Maximum Batch Size");
        if (batchSizeObj instanceof DSINumber) {
            maxBatchSize = ((DSINumber) batchSizeObj).toInt();
        }
    }
    
    @Override
    protected void refresh() {
        super.refresh();
        if (subPath != null) {
            put("Subscribe Path", subPath).setReadOnly(true);
        }
        put("Push Interval", interval).setReadOnly(true);
        put("Maximum Batch Size", maxBatchSize).setReadOnly(true);
        DSRuntime.run(new Runnable() {
            @Override
            public void run() {
                init();
            }
        });
    }
    
    private void init() {
        DSIRequester requester = MainNode.getRequester();
        int qos = 0;
        if (subPath != null && !subPath.isEmpty()) {
            requester.subscribe(this.subPath, DSLong.valueOf(qos), this);
        }
        if (interval > 0) {
            long intervalMillis = (long) (interval * 1000);
            future = DSRuntime.runAfterDelay(this, intervalMillis, intervalMillis);
        }
    }
    
    @Override
    protected void onRemoved() {
        super.onRemoved();
        end();
    }
    
    private void end() {
        if (future != null) {
            future.cancel();
        }
        close();
    }
    
    @Override
    protected DSList getChildList() {
        return null;
    }

    private DSIObject makeEditAction() {
        DSAction act = new DSAction() {
            @Override
            public void prepareParameter(DSInfo target, DSMap parameter) {
                DSMetadata paramMeta = new DSMetadata(parameter);
                DSIObject def = target.getNode().get(paramMeta.getName());
                if (def instanceof DSIValue) {
                    paramMeta.setDefault((DSIValue) def);
                }
            }
            
            @Override
            public ActionResult invoke(DSInfo target, ActionInvocation request) {
                ((MeterNode) target.get()).edit(request.getParameters());
                return null;
            }
        };
        act.addParameter("Subscribe Path", DSValueType.STRING, null);
        act.addDefaultParameter("Push Interval", DSDouble.valueOf(3600), "seconds");
        act.addDefaultParameter("Maximum Batch Size", DSLong.valueOf(50), "Maximum number of updates to put in a single REST request");
        return act;
    }
    
    private void edit(DSMap parameters) {
        subPath = parameters.getString("Subscribe Path");
        interval = parameters.getDouble("Push Interval");
        maxBatchSize = parameters.getInt("Maximum Batch Size");
        end();
        refresh();
    }
    
    private DSIObject makeGetDataAction() {
        DSAction act = new DSAction() {
            
            @Override
            public void prepareParameter(DSInfo target, DSMap parameter) {
            }
            
            @Override
            public ActionResult invoke(DSInfo target, ActionInvocation request) {
                return ((MeterNode) target.get()).getData(request);
            }
        };
        act.addParameter("start", DSValueType.STRING, "ISO timestamp, optional");
        act.addParameter("end", DSValueType.STRING, "ISO timestamp, optional");
        act.addDefaultParameter("order", DSFlexEnum.valueOf("asc", new DSList().add("asc").add("desc")), "Ascending or Descending");
        act.addDefaultParameter("resolution", DSFlexEnum.valueOf("month", 
                new DSList().add("live").add("quarterhour").add("hour").add("day").add("month")), null);
        act.addDefaultParameter("include", DSFlexEnum.valueOf("none", 
                new DSList().add("cost").add("consumption").add("none")), "Whether to include cost, consumption, or neither");
        act.setResultType(ResultType.CLOSED_TABLE);
        act.addColumnMetadata("localtime", DSValueType.STRING);
        act.addColumnMetadata("value", DSValueType.NUMBER);
        act.addColumnMetadata("cost", DSValueType.NUMBER);
        act.addColumnMetadata("consumption", DSValueType.NUMBER);
        return act;
    }
    
    private ActionResult getData(ActionInvocation request) {
        DSMap parameters = request.getParameters().copy();
        DSIObject inclObj = parameters.get("include");
        String include = null;
        if (inclObj instanceof DSString) {
            include = inclObj.toString();
            include = include.equals("none") ? null: include;
        }
        if (include == null) {
            parameters.remove("include");
        }
        Response resp = MainNode.getClientProxy().invoke("GET", getDataUrl(), parameters, null);
        try {
            DSMap json = BosUtil.getMapFromResponse(resp);
            DSList data = json.getList("data");
            
            DataTable dataTable = new DataTable();
            dataTable.addColumn("localtime", DSValueType.STRING);
            dataTable.addColumn("value", DSValueType.NUMBER);
            if (include != null) {
                dataTable.addColumn(include, DSValueType.NUMBER);
            }
            for (DSElement rowElem: data) {
                if (rowElem instanceof DSMap) {
                    DSMap rowMap = (DSMap) rowElem;
                    DSElement time = rowMap.get("localtime");
                    DSElement val = rowMap.get("value");
                    if (include != null) {
                        DSElement extra = rowMap.get(include);
                        dataTable.addRow(time, val, extra);
                    } else {
                        dataTable.addRow(time, val);
                    }
                }
            }
            return dataTable;
        } catch (IOException e) {
            DSException.throwRuntime(e);
            return null;
        }
    }
    
    private class DataTable extends SimpleTable implements ActionTable {
        private DSIRowCursor cursor;
        private DSIRowCursor getCursor() {
            if (cursor == null) {
                cursor = cursor();
            }
            return cursor;
        }
        @Override
        public ActionSpec getAction() {
            return getDataAct.getAction();
        }

        @Override
        public void onClose() {
        }

        @Override
        public int getColumnCount() {
            return getCursor().getColumnCount();
        }

        @Override
        public void getMetadata(int index, DSMap bucket) {
            getCursor().getMetadata(index, bucket);
        }

        @Override
        public DSIValue getValue(int index) {
            return getCursor().getValue(index);
        }

        @Override
        public boolean next() {
            return getCursor().next();
        }
    }

    @Override
    public void onClose() {
        info("Rule with sub path " + subPath + ": onClose called");
        close();
    }

    @Override
    public void onError(ErrorType type, String msg) {
        info("Rule with sub path " + subPath + ": onError called with msg " + msg);
        DSException.throwRuntime(new RuntimeException(msg));
    }

    @Override
    public void onInit(String path, DSIValue params, OutboundStream stream) {
        info("Rule with sub path " + subPath + ": onInit called");
        this.stream = stream;
        this.streamParams = params;
    }

    @Override
    public void onUpdate(DSDateTime dateTime, DSElement value, DSStatus status) {
        info("Rule with sub path " + subPath + ": onUpdate called with value " + (value!=null ? value : "Null"));
        String uuid = getUuid();
        if (uuid == null) {
            warn("Could not record update, no uuid found for meter " + getName());
            return;
        }
        Util.storeInBuffer(uuid, new SubUpdate(dateTime.toString(), value.toString(), status.toString(), dateTime.timeInMillis()));
    }
    
    
    public void close() {
        if (stream != null && stream.isStreamOpen()) {
            info("Rule with sub path " + subPath + ": closing Stream");
            stream.closeStream();
        }
    }

    @Override
    public void run() {
        String uuid = getUuid();
        if (uuid == null) {
            warn("Can't send updates, no uuid found for meter " + getName());
            return;
        }
        Util.processBuffer(uuid, this);
    }

    @Override
    public int getMaxBatchSize() {
        return maxBatchSize;
    }

    @Override
    public Queue<SubUpdate> sendBatchUpdate(Queue<SubUpdate> updates) {
        String uuid = getUuid();
        if (uuid == null) {
            warn("Can't send updates, no uuid found for meter " + getName());
            return updates;
        }
        DSMap urlParams = new DSMap();
        String body = BosConstants.DATA_FORMAT.replaceAll(BosConstants.PLACEHOLDER_UUID, uuid);
        StringBuilder sb = new StringBuilder();
        int indexOfStart = body.indexOf(Constants.PLACEHOLDER_BLOCK_START);
        int indexOfEnd = body.indexOf(Constants.PLACEHOLDER_BLOCK_END);
        String prefix = body.substring(0, indexOfStart);
        String block = body.substring(indexOfStart + Constants.PLACEHOLDER_BLOCK_START.length(), indexOfEnd);
        String suffix = body.substring(indexOfEnd + Constants.PLACEHOLDER_BLOCK_END.length());
        sb.append(prefix);
        Queue<SubUpdate> updatesCopy = new LinkedList<SubUpdate>();
        while (!updates.isEmpty()) {
            SubUpdate update = updates.poll();
            updatesCopy.add(update);
            String temp = block.replaceAll(Constants.PLACEHOLDER_VALUE, update.value)
                    .replaceAll(Constants.PLACEHOLDER_TS, update.dateTime)
                    .replaceAll(Constants.PLACEHOLDER_STATUS, update.status);
            sb.append(temp);
            if (!updates.isEmpty()) {
                sb.append(',');
            }
        }
        sb.append(suffix);
        body = sb.toString();
        info("Rule with sub path " + subPath + ": sending batch update");
        Response resp = restInvoke(urlParams, body);
        if (resp != null && resp.code() == 200) {
            return null;
        } else {
            return updatesCopy;
        }
    }
    
    private String getGatewayUrl() {
        DSIObject gatewayObj = get("gateway");
        if (gatewayObj instanceof DSMap) {
            return ((DSMap) gatewayObj).getString("url");
        }
        return null;
    }
    
    private String getDataUrl() {
        DSIObject gatewayObj = get("data");
        if (gatewayObj instanceof DSString) {
            return ((DSString) gatewayObj).toString();
        }
        return null;
    }
    
    private String getUuid() {
        DSIObject uuidObj = get("uuid");
        if (!(uuidObj instanceof DSString)) {
            return null;
        }
        return ((DSString) uuidObj).toString();
    }
    
    private Response restInvoke(DSMap urlParams, String body) {
        Response resp = null;
        String gatewayUrl = getGatewayUrl();
        if (gatewayUrl != null) {
            try {
                resp = MainNode.getClientProxy().invoke(METHOD, gatewayUrl + "/data/", urlParams, body);
            } catch (Exception e) {
                warn("", e);
            }
        }
        responseRecieved(resp);
        return resp;
    }
    
    private void responseRecieved(Response resp) {
        if (resp == null) {
            put(lastRespCode, DSInt.valueOf(-1));
            put(lastRespData, DSString.valueOf("Failed to send update"));
            put(lastRespTs, DSString.valueOf(DSDateTime.currentTime()));
        } else {
            int status = resp.code();
            String data = null;
            try {
                data = BosUtil.getBodyFromResponse(resp);
            } catch (IOException e) {
                warn("", e);
            }
            
            put(lastRespCode, DSInt.valueOf(status));
            put(lastRespData, DSString.valueOf(data));
            put(lastRespTs, DSString.valueOf(DSDateTime.valueOf(resp.receivedResponseAtMillis())));
        }
    }

    @Override
    public DSIValue getParams() {
        return streamParams;
    }

    @Override
    public OutboundStream getStream() {
        return stream;
    }
    
    

}
