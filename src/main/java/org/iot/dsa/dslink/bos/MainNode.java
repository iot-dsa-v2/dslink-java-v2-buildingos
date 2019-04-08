package org.iot.dsa.dslink.bos;

import java.util.Map;
import org.iot.dsa.dslink.DSMainNode;
import org.iot.dsa.dslink.restadapter.CredentialProvider;
import org.iot.dsa.dslink.restadapter.Util.AUTH_SCHEME;
import org.iot.dsa.dslink.restadapter.WebClientProxy;
import org.iot.dsa.node.DSFlexEnum;
import org.iot.dsa.node.DSIObject;
import org.iot.dsa.node.DSIValue;
import org.iot.dsa.node.DSInfo;
import org.iot.dsa.node.DSList;
import org.iot.dsa.node.DSMap;
import org.iot.dsa.node.DSMetadata;
import org.iot.dsa.node.DSValueType;
import org.iot.dsa.node.action.ActionInvocation;
import org.iot.dsa.node.action.ActionResult;
import org.iot.dsa.node.action.DSAction;
/**
 * The root and only node of this link.
 *
 * @author Aaron Hansen
 */
public class MainNode extends DSMainNode implements CredentialProvider, BosNode {

    private final WebClientProxy clientProxy = new WebClientProxy(this);
    private Map<String, DSMap> orgList;
    
    private static MainNode instance;
    
    public static WebClientProxy getClientProxy() {
        return instance.clientProxy;
    }

    public MainNode() {
    }

    
    /**
     * Defines the permanent children of this node type, their existence is guaranteed in all
     * instances.  This is only ever called once per, type per process.
     */
    @Override
    protected void declareDefaults() {
        super.declareDefaults();
        declareDefault(Constants.ACTION_SET_CREDS, makeSetCredentialsAction());
        declareDefault(Constants.ACTION_ADD_ORG, makeAddOrganizationAction());
    }
    
    @Override
    protected void onStarted() {
        super.onStarted();
        instance = this;
    }
    
    @Override
    protected void onStable() {
        super.onStable();
        refresh();
    }

    private DSAction makeSetCredentialsAction() {
        DSAction act = new DSAction.Parameterless() {
            
            @Override
            public ActionResult invoke(DSInfo target, ActionInvocation request) {
                ((MainNode) target.get()).setCredentials(request.getParameters());
                return null;
            }
        };
        act.addParameter(Constants.CLIENT_ID, DSValueType.STRING, null);
        act.addParameter(Constants.CLIENT_SECRET, DSValueType.STRING, null);
        return act;
    }
    
    private DSAction makeAddOrganizationAction() {
        DSAction act = new DSAction() {
            
            @Override
            public void prepareParameter(DSInfo target, DSMap parameter) {
                DSList range = ((MainNode) target.get()).getChildNames();
                if (range.size() > 0) {
                    new DSMetadata(parameter).setType(DSFlexEnum.valueOf(range.getString(0), range));
                }
            }
            
            @Override
            public ActionResult invoke(DSInfo target, ActionInvocation request) {
                ((MainNode) target.get()).addOrganization(request.getParameters());
                return null;
            }
        };
        act.addParameter("Organization", DSValueType.ENUM, null);
        return act;
    }
    
    private void setCredentials(DSMap parameters) {
        String clientID = parameters.getString(Constants.CLIENT_ID);
        String clientSecret = parameters.getString(Constants.CLIENT_SECRET);
        put(Constants.CLIENT_ID, clientID);
        put(Constants.CLIENT_SECRET, clientSecret);
        refresh();
    }
    
    private void addOrganization(DSMap parameters) {
        String orgName = parameters.getString("Organization");
        String url = getChildUrl(orgName);
        if (url != null) {
            put(orgName, new OrganizationNode(url));
        }
    }
    
    private void refresh() {
        if (getClientId() != null && getClientSecret() != null) {
            orgList = Util.getOrganizations(clientProxy);
        }
    }


    @Override
    public String getUsername() {
        return null;
    }


    @Override
    public String getPassword() {
        return null;
    }


    @Override
    public String getClientId() {
        DSIObject obj = get(Constants.CLIENT_ID);
        if (obj instanceof DSIValue) {
            return ((DSIValue) obj).toElement().toString(); 
        } else {
            return null;
        }
    }


    @Override
    public String getClientSecret() {
        DSIObject obj = get(Constants.CLIENT_SECRET);
        if (obj instanceof DSIValue) {
            return ((DSIValue) obj).toElement().toString(); 
        } else {
            return null;
        }
    }


    @Override
    public String getTokenURL() {
        return "https://api.buildingos.com/o/token/";
    }


    @Override
    public org.iot.dsa.dslink.restadapter.Util.AUTH_SCHEME getAuthScheme() {
        return AUTH_SCHEME.OAUTH2_CLIENT;
    }

    @Override
    public Map<String, DSMap> getChildMap() {
        return orgList;
    }

}
