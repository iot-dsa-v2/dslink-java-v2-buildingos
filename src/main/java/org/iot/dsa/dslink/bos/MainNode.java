package org.iot.dsa.dslink.bos;

import java.util.Collection;
import java.util.Map;
import org.etsdb.util.PurgeSettings;
import org.iot.dsa.dslink.DSIRequester;
import org.iot.dsa.dslink.DSLinkConnection;
import org.iot.dsa.dslink.DSMainNode;
import org.iot.dsa.dslink.restadapter.Constants;
import org.iot.dsa.dslink.restadapter.CredentialProvider;
import org.iot.dsa.dslink.restadapter.Util;
import org.iot.dsa.dslink.restadapter.Util.AUTH_SCHEME;
import org.iot.dsa.dslink.restadapter.WebClientProxy;
import org.iot.dsa.node.DSBool;
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
import org.iot.dsa.node.event.DSEventFilter;
import org.iot.dsa.util.DSException;
/**
 * The root and only node of this link.
 *
 * @author Aaron Hansen
 */
public class MainNode extends DSMainNode implements CredentialProvider, BosNode, PurgeSettings {

    private final WebClientProxy clientProxy = new WebClientProxy(this, 30000, 30000);
    private Map<String, DSMap> orgList;
    private Map<String, String> gatewayList;
    private static final Object requesterLock = new Object();
    private static DSIRequester requester;
    private static MainNode instance;
    
    private DSInfo maxBufferSize = getInfo(Constants.BUFFER_MAX_SIZE);
    private DSInfo purgeEnabled = getInfo(Constants.BUFFER_PURGE_ENABLED);
    
    public static WebClientProxy getClientProxy() {
        return instance.clientProxy;
    }

    public MainNode() {
    }
    
    public static DSIRequester getRequester() {
        synchronized (requesterLock) {
            while (requester == null) {
                try {
                    requesterLock.wait();
                } catch (InterruptedException e) {
                    DSException.throwRuntime(e);
                }
            }
            return requester;
        }
    }
    
    public static void setRequester(DSIRequester requester) {
        synchronized (requesterLock) {
            MainNode.requester = requester;
            requesterLock.notifyAll();
        }
    }

    
    /**
     * Defines the permanent children of this node type, their existence is guaranteed in all
     * instances.  This is only ever called once per, type per process.
     */
    @Override
    protected void declareDefaults() {
        super.declareDefaults();
        declareDefault(BosConstants.ACTION_SET_CREDS, makeSetCredentialsAction());
        declareDefault(BosConstants.ACTION_ADD_ORG, makeAddOrganizationAction());
        declareDefault(Constants.BUFFER_PURGE_ENABLED, DSBool.FALSE,
                "Whether old unsent records should automatically be purged from the buffer when the buffer gets too large");
        declareDefault(Constants.BUFFER_MAX_SIZE, DSLong.valueOf(1074000000),
                "Maximum size of buffer in bytes; only applies if auto-purge is enabled");
    }
    
    @Override
    protected void onStarted() {
        super.onStarted();
        instance = this;
        Util.setBufferPurgeSettings(instance);
        getLink().getConnection().subscribe(new DSEventFilter(
                ((event, node, child, data) -> MainNode.setRequester(
                        getLink().getConnection().getRequester())),
                DSLinkConnection.CONNECTED_EVENT,
                null));
    }
    
    @Override
    protected void onStable() {
        super.onStable();
        refresh();
    }
    
    @Override
    public void getVirtualActions(DSInfo target, Collection<String> bucket) {
        //No Virtual Actions
    }

    private DSAction makeSetCredentialsAction() {
        DSAction act = new DSAction.Parameterless() {
            
            @Override
            public ActionResult invoke(DSInfo target, ActionInvocation request) {
                ((MainNode) target.get()).setCredentials(request.getParameters());
                return null;
            }
        };
        act.addParameter(BosConstants.CLIENT_ID, DSValueType.STRING, null);
        act.addParameter(BosConstants.CLIENT_SECRET, DSValueType.STRING, null);
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
        act.addParameter(BosConstants.ORGANIZATION, DSValueType.ENUM, null);
        return act;
    }
    
    private void setCredentials(DSMap parameters) {
        String clientID = parameters.getString(BosConstants.CLIENT_ID);
        String clientSecret = parameters.getString(BosConstants.CLIENT_SECRET);
        put(BosConstants.CLIENT_ID, clientID);
        put(BosConstants.CLIENT_SECRET, clientSecret);
        refresh();
    }
    
    private void addOrganization(DSMap parameters) {
        String orgName = parameters.getString(BosConstants.ORGANIZATION);
        String url = getChildUrl(orgName);
        if (url != null) {
            put(orgName, new OrganizationNode(url));
        }
    }
    
    private void refresh() {
        if (getClientId() != null && getClientSecret() != null) {
            orgList = BosUtil.getOrganizations(clientProxy);
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
        DSIObject obj = get(BosConstants.CLIENT_ID);
        if (obj instanceof DSIValue) {
            return ((DSIValue) obj).toElement().toString(); 
        } else {
            return null;
        }
    }


    @Override
    public String getClientSecret() {
        DSIObject obj = get(BosConstants.CLIENT_SECRET);
        if (obj instanceof DSIValue) {
            return ((DSIValue) obj).toElement().toString(); 
        } else {
            return null;
        }
    }


    @Override
    public String getTokenURL() {
        return BosConstants.TOKEN_URL;
    }


    @Override
    public org.iot.dsa.dslink.restadapter.Util.AUTH_SCHEME getAuthScheme() {
        return AUTH_SCHEME.OAUTH2_CLIENT;
    }

    @Override
    public Map<String, DSMap> getChildMap() {
        return orgList;
    }
    
    public static Map<String, String> getGatewayList() {
        if (instance.gatewayList == null) {
            instance.gatewayList = BosUtil.getGatewayList(instance.clientProxy);
        }
        return instance.gatewayList;
    }

    @Override
    public boolean isPurgeEnabled() {
        return purgeEnabled.getValue().toElement().toBoolean();
    }

    @Override
    public long getMaxSizeInBytes() {
        return maxBufferSize.getValue().toElement().toLong();
    }

}
