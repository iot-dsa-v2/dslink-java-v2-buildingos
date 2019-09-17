package org.iot.dsa.dslink.bos;

import java.io.IOException;
import java.util.Map;
import okhttp3.Response;
import org.iot.dsa.dslink.ActionResults;
import org.iot.dsa.node.DSFlexEnum;
import org.iot.dsa.node.DSIObject;
import org.iot.dsa.node.DSIValue;
import org.iot.dsa.node.DSInfo;
import org.iot.dsa.node.DSJavaEnum;
import org.iot.dsa.node.DSList;
import org.iot.dsa.node.DSMap;
import org.iot.dsa.node.DSMetadata;
import org.iot.dsa.node.DSString;
import org.iot.dsa.node.action.DSAction;
import org.iot.dsa.node.action.DSIActionRequest;
import org.iot.dsa.util.DSException;

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
        declareDefault(BosConstants.ACTION_ADD_BUILDING, makeAddBuildingAction());
    }

    protected DSList getChildList() {
        DSIObject buildings = get(BosApiConstants.BUILDINGS);
        if (buildings instanceof DSList) {
            return (DSList) buildings;
        }
        return null;
    }

    @Override
    protected void refresh() {
        super.refresh();
        put(BosConstants.ACTION_CREATE_BUILDING, makeCreateBuildingAction());
    }

    private void addBuilding(DSMap parameters) {
        String bName = parameters.getString(BosConstants.BUILDING);
        String url = getChildUrl(bName);
        if (url != null) {
            put(bName, new BuildingNode(url));
        }
    }

    private void createBuilding(DSMap parameters) {
        String name = parameters.getString(BosApiConstants.NAME);
        DSIObject idObj = get(BosApiConstants.ID);
        if (!(idObj instanceof DSIValue)) {
            warn("missing org id");
            return;
        }
        parameters.put(BosApiConstants.ORGANIZATION, idObj.toString());

        Map<String, BosParameter> enumParams = BosUtil
                .getBuildingEnumParams(MainNode.getClientProxy());
        if (enumParams == null) {
            return;
        }
        for (BosParameter param : enumParams.values()) {
            String paramName = param.getName();
            String disp = parameters.getString(paramName);
            if (disp != null) {
                parameters.put(paramName, param.getId(disp));
            }
        }

        Response resp = MainNode.getClientProxy()
                                .invoke(BosConstants.METHOD_POST, BosConstants.BUILDINGS_URL,
                                        new DSMap(), parameters.toString());

        if (resp != null) {
            try {
                DSMap json = BosUtil.getMapFromResponse(resp);
                String url = json.getString(BosApiConstants.URL);
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

    private DSAction makeAddBuildingAction() {
        DSAction act = new DSAction() {

            @Override
            public ActionResults invoke(DSIActionRequest request) {
                ((OrganizationNode) request.getTarget()).addBuilding(request.getParameters());
                return null;
            }

            @Override
            public void prepareParameter(DSInfo target, DSMap parameter) {
                DSList range = ((BosNode) target.get()).getChildNames();
                if (range.size() > 0) {
                    new DSMetadata(parameter)
                            .setType(DSFlexEnum.valueOf(range.getString(0), range));
                }
            }
        };
        act.addParameter(BosConstants.BUILDING, DSJavaEnum.NULL, null);
        return act;
    }

    private DSAction makeCreateBuildingAction() {
        DSAction act = new DSAction() {

            @Override
            public ActionResults invoke(DSIActionRequest request) {
                ((OrganizationNode) request.getTarget()).createBuilding(request.getParameters());
                return null;
            }

            @Override
            public void prepareParameter(DSInfo target, DSMap parameter) {
            }
        };
        act.addParameter(BosApiConstants.NAME, DSString.NULL, null);
        Map<String, BosParameter> enumParams = BosUtil
                .getBuildingEnumParams(MainNode.getClientProxy());
        if (enumParams == null) {
            return null;
        }

        act.addParameter(enumParams.get("buildingType").getMap());

        act.addDefaultParameter(BosApiConstants.VENDOR_BUILDING_ID, DSString.EMPTY, null);
        act.addDefaultParameter(BosApiConstants.ADDRESS, DSString.EMPTY, null);
//        act.addDefaultParameter(BosApiConstants.DESCRIPTION, DSString.EMPTY, null);
        act.addDefaultParameter(BosApiConstants.POST_CODE, DSString.EMPTY, null);
        act.addDefaultParameter(BosApiConstants.COUNTRY_CODE, DSString.EMPTY, null);
//        act.addDefaultParameter(BosApiConstants.AREA, DSDouble.valueOf(0), null);
//        act.addDefaultParameter(BosApiConstants.LONGITUDE, DSDouble.valueOf(0), null);
//        act.addDefaultParameter(BosApiConstants.LATITUDE, DSDouble.valueOf(0), null);
//        act.addDefaultParameter(BosApiConstants.GEOCODED, DSBool.TRUE, null);
        return act;
    }
}
