package org.iot.dsa.dslink.bos;

import org.apache.commons.lang3.RandomStringUtils;
import org.iot.dsa.DSRuntime;
import org.iot.dsa.DSRuntime.Timer;
import org.iot.dsa.dslink.DSIRequester;
import org.iot.dsa.dslink.requester.ErrorType;
import org.iot.dsa.dslink.requester.OutboundStream;
import org.iot.dsa.dslink.requester.OutboundSubscribeHandler;
import org.iot.dsa.dslink.restadapter.MainNode;
import org.iot.dsa.dslink.restadapter.SubUpdate;
import org.iot.dsa.dslink.restadapter.Util;
import org.iot.dsa.node.DSDouble;
import org.iot.dsa.node.DSElement;
import org.iot.dsa.node.DSIObject;
import org.iot.dsa.node.DSIValue;
import org.iot.dsa.node.DSList;
import org.iot.dsa.node.DSStatus;
import org.iot.dsa.node.DSString;
import org.iot.dsa.time.DSDateTime;
import org.iot.dsa.util.DSException;

public class MeterNode extends BosObjectNode implements OutboundSubscribeHandler, Runnable {
    
    private String subPath;
    private OutboundStream stream;
    private String id;
    private double interval = -1;
    
    public MeterNode() {
        super();
    }
    
    public MeterNode(String url, String subPath, double interval) {
        super(url);
        this.subPath = subPath;
        this.interval = interval;
    }
    
    @Override
    protected void declareDefaults() {
        super.declareDefaults();
    }
    
    @Override
    protected void onStarted() {
        super.onStarted();
        DSIObject pathobj = get("Subscription Path");
        if (pathobj instanceof DSString) {
            this.subPath = ((DSString) pathobj).toString();
        }
        DSIObject idobj = get("id");
        if (idobj instanceof DSIValue) {
            id = ((DSIValue) idobj).toElement().toString();
        } else {
            id = RandomStringUtils.randomAlphanumeric(12);
            put("id", id);
        }
        DSIObject intervobj = get("Push Interval");
        if (intervobj instanceof DSDouble) {
            interval = ((DSDouble) intervobj).toDouble();
        }
    }
    
    @Override
    protected void refresh() {
        super.refresh();
        if (subPath != null) {
            put("Subscription Path", subPath);
        }
        put("Push Interval", interval);
        DSIRequester requester = MainNode.getRequester();
        int qos = 0;
        requester.subscribe(this.subPath, qos, this);
        long intervalMillis= (long) (interval * 1000);
        DSRuntime.runAfterDelay(this, intervalMillis, intervalMillis);
    }

    @Override
    protected DSList getChildList() {
        return null;
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
    }

    @Override
    public void onUpdate(DSDateTime dateTime, DSElement value, DSStatus status) {
        info("Rule with sub path " + subPath + ": onUpdate called with value " + (value!=null ? value : "Null"));
        Util.storeInBuffer(id, new SubUpdate(dateTime.toString(), value.toString(), status.toString(), dateTime.timeInMillis()));
    }
    
    
    public void close() {
        if (stream != null && stream.isStreamOpen()) {
            info("Rule with sub path " + subPath + ": closing Stream");
            stream.closeStream();
        }
    }

    @Override
    public void run() {
        // TODO process buffer
    }
    
    

}
