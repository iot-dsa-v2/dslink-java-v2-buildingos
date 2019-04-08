package org.iot.dsa.dslink.bos;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.iot.dsa.node.DSElement;
import org.iot.dsa.node.DSIObject;
import org.iot.dsa.node.DSList;
import org.iot.dsa.node.DSMap;
import org.iot.dsa.node.DSNode;
import org.iot.dsa.node.DSString;
import org.iot.dsa.node.DSMap.Entry;
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
    protected void onStable() {
        super.onStable();
        refresh();
    }
    
    protected void refresh() {
        if (url != null) {
            Response resp = MainNode.getClientProxy().invoke("GET", url, new DSMap(), null);
            try {
                String respStr = resp.body().string();
                DSMap json = Util.parseJsonMap(respStr);
                parse(json.getMap("data"));
            } catch (IOException e) {
                warn("", e);
            } finally {
                resp.close();
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
                    String name = ((DSMap) child).getString("name");
                    m.put(name, (DSMap) child);
                }
            }
            childMap = m;
        }
        return childMap;
    }
    
    protected abstract DSList getChildList();

}
