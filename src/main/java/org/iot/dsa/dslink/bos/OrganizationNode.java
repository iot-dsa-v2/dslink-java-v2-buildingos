package org.iot.dsa.dslink.bos;

import java.io.IOException;
import org.iot.dsa.node.DSBool;
import org.iot.dsa.node.DSDouble;
import org.iot.dsa.node.DSFlexEnum;
import org.iot.dsa.node.DSIObject;
import org.iot.dsa.node.DSIValue;
import org.iot.dsa.node.DSInfo;
import org.iot.dsa.node.DSList;
import org.iot.dsa.node.DSMap;
import org.iot.dsa.node.DSMetadata;
import org.iot.dsa.node.action.ActionInvocation;
import org.iot.dsa.node.action.ActionResult;
import org.iot.dsa.node.action.DSAction;
import org.iot.dsa.util.DSException;
import okhttp3.Response;
import org.iot.dsa.node.DSString;
import org.iot.dsa.node.DSValueType;

public class OrganizationNode extends BosObjectNode {
    
    public OrganizationNode() {
        super();
    }
    
    public OrganizationNode(String url) {
        super(url);
    }
    
    @Override
    protected void declareDefaults() {
        super.declareDefaults();
        declareDefault("Add Building", makeAddBuildingAction());
        declareDefault("Create Building", makeCreateBuildingAction());
    }
    
    protected DSList getChildList() {
        DSIObject buildings = get("buildings");
        if (buildings instanceof DSList) {
            return (DSList) buildings;
        }
        return null;
    }
    
    
    private DSAction makeAddBuildingAction() {
        DSAction act = new DSAction() {
            
            @Override
            public ActionResult invoke(DSInfo target, ActionInvocation request) {
                ((OrganizationNode) target.get()).addBuilding(request.getParameters());
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
        act.addParameter("Building", DSValueType.ENUM, null);
        return act;
    }
    
    private DSAction makeCreateBuildingAction() {
        DSAction act = new DSAction() {

            @Override
            public ActionResult invoke(DSInfo target, ActionInvocation request) {
                ((OrganizationNode) target.get()).createBuilding(request.getParameters());
                return null;
            }

            @Override
            public void prepareParameter(DSInfo target, DSMap parameter) {
            }  
        };
        act.addParameter("name", DSValueType.STRING, null);
        act.addDefaultParameter("vendorBuildingId", DSString.EMPTY, null);
        act.addDefaultParameter("buildingType", DSString.EMPTY, null);
        act.addDefaultParameter("address", DSString.EMPTY, null);
        act.addDefaultParameter("description", DSString.EMPTY, null);
        act.addDefaultParameter("postalCode", DSString.EMPTY, null);
        act.addDefaultParameter("countryCode", DSString.EMPTY, null);
        act.addDefaultParameter("area", DSDouble.valueOf(0), null);
        act.addDefaultParameter("longitude", DSDouble.valueOf(0), null);
        act.addDefaultParameter("latitude", DSDouble.valueOf(0), null);
        act.addDefaultParameter("geocoded", DSBool.TRUE, null);
        return act;
    }
    
    private void addBuilding(DSMap parameters) {
        String bName = parameters.getString("Building");
        String url = getChildUrl(bName);
        if (url != null) {
            put(bName, new BuildingNode(url));
        }
    }
    
    private void createBuilding(DSMap parameters) {
        String name = parameters.getString("name");
        DSIObject idObj = get("id");
        if (!(idObj instanceof DSIValue)) {
            warn("missing org id");
            return;
        }
        parameters.put("organization", idObj.toString());
        Response resp = MainNode.getClientProxy().invoke("POST", "https://api.buildingos.com/buildings/", new DSMap(), parameters.toString());
        
        if (resp != null) {
            try {
                DSMap json = BosUtil.getMapFromResponse(resp);
                String url = json.getString("url");
                if (url != null) {
                    put(name, new BuildingNode(url));
                }
                refresh();
            } catch (IOException e) {
                warn("", e);
                DSException.throwRuntime(e);
            }
        }
    }
}
