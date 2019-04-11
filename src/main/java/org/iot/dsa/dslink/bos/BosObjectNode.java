package org.iot.dsa.dslink.bos;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.iot.dsa.node.DSElement;
import org.iot.dsa.node.DSIObject;
import org.iot.dsa.node.DSInfo;
import org.iot.dsa.node.DSList;
import org.iot.dsa.node.DSMap;
import org.iot.dsa.node.DSNode;
import org.iot.dsa.node.DSString;
import org.iot.dsa.node.DSMap.Entry;
import org.iot.dsa.node.action.ActionInvocation;
import org.iot.dsa.node.action.ActionResult;
import org.iot.dsa.node.action.DSAction;
import okhttp3.Response;

public abstract class BosObjectNode extends DSNode implements BosNode {
    
    private String url;
    private Map<String, DSMap> childMap;
    
    public BosObjectNode() {
    }
    
    public BosObjectNode(String url) {
        this.url = url;
    }
    
    @Override
    protected void onStarted() {
        super.onStarted();
        DSIObject urlobj = get("url");
        if (urlobj instanceof DSString) {
            this.url = ((DSString) urlobj).toString();
        }
    }
    
    @Override
    protected void declareDefaults() {
        super.declareDefaults();
        declareDefault("Refresh", makeRefreshAction());
    }
    
    @Override
    protected void onStable() {
        super.onStable();
        refresh();
    }
    
    private DSIObject makeRefreshAction() {
        DSAction act = new DSAction.Parameterless() {
            
            @Override
            public ActionResult invoke(DSInfo target, ActionInvocation request) {
                ((BosObjectNode) target.get()).refresh();
                return null;
            }
        };
        return act;
    }
    
    protected void refresh() {
        if (url != null) {
            Response resp = MainNode.getClientProxy().invoke("GET", url, new DSMap(), null);
            try {
                DSMap json = BosUtil.getMapFromResponse(resp);
                parse(json.getMap("data"));
            } catch (IOException e) {
                warn("", e);
            }
        }
    }
    
    private void parse(DSMap data) {
        if (data == null) {
            return;
        }
        for (Entry entry: data) {
            put(entry.getKey(), entry.getValue().copy());
        }
    }

    @Override
    public Map<String, DSMap> getChildMap() {
        if (childMap == null) {
            DSList childList = getChildList();
            if (childList == null) {
                return null;
            }
            Map<String, DSMap> m = new HashMap<String, DSMap>();
            for (DSElement child: childList) {
                if (child instanceof DSMap) {
                    String name = getChildDisplayName((DSMap) child);
                    m.put(name, (DSMap) child);
                }
            }
            childMap = m;
        }
        return childMap;
    }
    
    protected String getChildDisplayName(DSMap childSummary) {
        return childSummary.getString("displayName");
    }
    
    protected abstract DSList getChildList();

}
