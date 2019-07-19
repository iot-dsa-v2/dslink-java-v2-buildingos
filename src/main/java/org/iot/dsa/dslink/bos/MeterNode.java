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
import org.iot.dsa.node.DSINumber;
import org.iot.dsa.node.DSIObject;
import org.iot.dsa.node.DSIValue;
import org.iot.dsa.node.DSInfo;
import org.iot.dsa.node.DSInt;
import org.iot.dsa.node.DSJavaEnum;
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
    private static final String METHOD = BosConstants.METHOD_POST;
    
    private String subPath = "";
    private OutboundStream stream;
    private DSIValue streamParams;
    private double interval = -1;
    private int maxBatchSize = -1;
    private double minUpdateInterval = 0;
    private long minUpdateIntervalMillis = 0;
    private long lastUpdateTs = -1;
    private Timer future;
    
    private DSInfo getDataAct = getInfo(BosConstants.ACTION_GET_DATA);
    private DSInfo lastRespCode = getInfo(Constants.LAST_RESPONSE_CODE);
    private DSInfo lastRespData = getInfo(Constants.LAST_RESPONSE_DATA);
    private DSInfo lastRespTs = getInfo(Constants.LAST_RESPONSE_TS);

    public MeterNode() {
        super();
    }
    
    public MeterNode(String url, String subPath, double interval, int maxBatchSize, double minUpdateInterval) {
        super(url);
        this.subPath = subPath;
        this.interval = interval;
        this.maxBatchSize = maxBatchSize;
        this.minUpdateInterval = minUpdateInterval;
    }
    
    @Override
    protected void declareDefaults() {
        super.declareDefaults();
        declareDefault(Constants.ACT_EDIT, makeEditAction());
        declareDefault(BosConstants.ACTION_GET_DATA, makeGetDataAction());
        declareDefault(BosConstants.ACTION_INSERT_BULK, makeBulkInsertAction());
        declareDefault(Constants.LAST_RESPONSE_CODE, DSInt.NULL).setReadOnly(true);
        declareDefault(Constants.LAST_RESPONSE_DATA, DSString.EMPTY).setReadOnly(true);
        declareDefault(Constants.LAST_RESPONSE_TS, DSString.EMPTY).setReadOnly(true);
    }

    @Override
    protected void onStarted() {
        super.onStarted();
        DSIObject pathobj = get(Constants.SUB_PATH);
        if (pathobj instanceof DSString) {
            this.subPath = ((DSString) pathobj).toString();
        }
        DSIObject intervobj = get(BosConstants.PUSH_INTERVAL);
        if (intervobj instanceof DSINumber) {
            interval = ((DSINumber) intervobj).toDouble();
        }
        DSIObject batchSizeObj = get(Constants.MAX_BATCH_SIZE);
        if (batchSizeObj instanceof DSINumber) {
            maxBatchSize = ((DSINumber) batchSizeObj).toInt();
        }
        DSIObject minIntervObj = get(BosConstants.MIN_UPDATE_INTERVAL);
        if (minIntervObj instanceof DSINumber) {
            minUpdateInterval = ((DSINumber) minIntervObj).toDouble();
        }
    }
    
    @Override
    protected void refresh() {
        super.refresh();
        if (subPath != null) {
            put(Constants.SUB_PATH, subPath).setReadOnly(true);
        }
        put(BosConstants.PUSH_INTERVAL, interval).setReadOnly(true);
        put(Constants.MAX_BATCH_SIZE, maxBatchSize).setReadOnly(true);
        put(BosConstants.MIN_UPDATE_INTERVAL, minUpdateInterval).setReadOnly(true);
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
        minUpdateIntervalMillis = (long) (minUpdateInterval * 1000);
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
        act.addParameter(Constants.SUB_PATH, DSValueType.STRING, null);
        act.addDefaultParameter(BosConstants.PUSH_INTERVAL, DSDouble.valueOf(interval), "seconds");
        act.addDefaultParameter(Constants.MAX_BATCH_SIZE, DSLong.valueOf(maxBatchSize), "Maximum number of updates to put in a single REST request");
        act.addDefaultParameter(BosConstants.MIN_UPDATE_INTERVAL, DSDouble.valueOf(minUpdateInterval), "seconds");
        return act;
    }
    
    private void edit(DSMap parameters) {
        subPath = parameters.getString(Constants.SUB_PATH);
        interval = parameters.getDouble(BosConstants.PUSH_INTERVAL);
        maxBatchSize = parameters.getInt(Constants.MAX_BATCH_SIZE);
        minUpdateInterval = parameters.getDouble(BosConstants.MIN_UPDATE_INTERVAL);
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
        act.addParameter(BosApiConstants.START, DSValueType.STRING, "ISO timestamp, optional");
        act.addParameter(BosApiConstants.END, DSValueType.STRING, "ISO timestamp, optional");
        act.addDefaultParameter(BosApiConstants.ORDER, DSJavaEnum.valueOf(BosApiConstants.OrderOptions.asc), "Ascending or Descending");
        act.addDefaultParameter(BosApiConstants.RESOLUTION, DSJavaEnum.valueOf(BosApiConstants.ResolutionOptions.month), null);
        act.addDefaultParameter(BosApiConstants.INCLUDE, DSJavaEnum.valueOf(BosApiConstants.IncludeOptions.none), "Whether to include cost, consumption, or neither");
        act.setResultType(ResultType.CLOSED_TABLE);
        act.addColumnMetadata(BosApiConstants.RECORD_TS, DSValueType.STRING);
        act.addColumnMetadata(BosApiConstants.RECORD_VALUE, DSValueType.NUMBER);
        act.addColumnMetadata(BosApiConstants.IncludeOptions.cost.name(), DSValueType.NUMBER);
        act.addColumnMetadata(BosApiConstants.IncludeOptions.consumption.name(), DSValueType.NUMBER);
        return act;
    }
    
    private ActionResult getData(ActionInvocation request) {
        DSMap parameters = request.getParameters().copy();
        DSIObject inclObj = parameters.get(BosApiConstants.INCLUDE);
        String include = null;
        if (inclObj instanceof DSString) {
            include = inclObj.toString();
            include = include.equals(BosApiConstants.IncludeOptions.none.name()) ? null: include;
        }
        if (include == null) {
            parameters.remove(BosApiConstants.INCLUDE);
        }
        Response resp = MainNode.getClientProxy().invoke(BosConstants.METHOD_GET, getDataUrl(), parameters, null);
        try {
            DSMap json = BosUtil.getMapFromResponse(resp);
            DSList data = json.getList(BosApiConstants.DATA);
            
            DataTable dataTable = new DataTable();
            dataTable.addColumn(BosApiConstants.RECORD_TS, DSValueType.STRING);
            dataTable.addColumn(BosApiConstants.RECORD_VALUE, DSValueType.NUMBER);
            if (include != null) {
                dataTable.addColumn(include, DSValueType.NUMBER);
            }
            for (DSElement rowElem: data) {
                if (rowElem instanceof DSMap) {
                    DSMap rowMap = (DSMap) rowElem;
                    DSElement time = rowMap.get(BosApiConstants.RECORD_TS);
                    DSElement val = rowMap.get(BosApiConstants.RECORD_VALUE);
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
    
    private DSAction makeBulkInsertAction() {
        DSAction act = new DSAction() { 
            @Override
            public void prepareParameter(DSInfo target, DSMap parameter) {
            }
            
            @Override
            public ActionResult invoke(DSInfo target, ActionInvocation request) {
                ((MeterNode) target.get()).bulkInsertRecords(request.getParameters());
                return null;
            }
        };
        act.addParameter(BosConstants.RECORD_TABLE, DSValueType.LIST, "table of records to send to meter");
        return act;
    }
    
    private void bulkInsertRecords(DSMap parameters) {
        DSList records = parameters.getList(BosConstants.RECORD_TABLE);
        String uuid = getUuid();
        if (uuid == null) {
            warn("Could not record updates, no uuid found for meter " + getName());
            return;
        }
        long lastUpdateTs = -1;
        for (DSElement record: records) {
            
            SubUpdate update = BosUtil.parseSubUpdate(record);
            if (update != null) {
                if (lastUpdateTs >= 0 && update.ts - lastUpdateTs < minUpdateIntervalMillis) {
                    // ignore
                } else {
                    lastUpdateTs = update.ts;
                    Util.storeInBuffer(uuid, update);
                }
            }
        }
        Util.processBuffer(uuid, this); //TODO: Should we do this?
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
        if (lastUpdateTs >= 0 && dateTime.timeInMillis() - lastUpdateTs < minUpdateIntervalMillis) {
            // ignore
        } else {
            lastUpdateTs = dateTime.timeInMillis();
            Util.storeInBuffer(uuid, new SubUpdate(dateTime, value, status));
        }
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
        DSIObject gatewayObj = get(BosApiConstants.GATEWAY);
        if (gatewayObj instanceof DSMap) {
            return ((DSMap) gatewayObj).getString(BosApiConstants.URL);
        }
        return null;
    }
    
    private String getDataUrl() {
        DSIObject dataObj = get(BosApiConstants.DATA);
        if (dataObj instanceof DSString) {
            return ((DSString) dataObj).toString();
        }
        return null;
    }
    
    private String getUuid() {
        DSIObject uuidObj = get(BosApiConstants.UUID);
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
            put(lastRespTs, DSString.valueOf(DSDateTime.now()));
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
    public DSIValue getParameters() {
        return streamParams;
    }

    @Override
    public OutboundStream getStream() {
        return stream;
    }
    
    

}
