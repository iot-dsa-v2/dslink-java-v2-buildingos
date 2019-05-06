package org.iot.dsa.dslink.bos;

public class BosConstants {
    
    public static final String METHOD_GET = "GET";
    public static final String METHOD_POST = "POST";
    
    public static final String ACTION_SET_CREDS = "Set Credentials";
    public static final String ACTION_ADD_ORG = "Add Organization";
    public static final String ACTION_ADD_BUILDING = "Add Building";
    public static final String ACTION_CREATE_BUILDING = "Create Building";
    public static final String ACTION_ADD_METER = "Add Meter";
    public static final String ACTION_ADD_METERS_BULK = "Bulk Add Meters";
    public static final String ACTION_CREATE_METER = "Create Meter";
    public static final String ACTION_GET_DATA = "Get Data";
    public static final String ACTION_REFRESH = "Refresh";
    
    public static final String CLIENT_ID = "Client ID";
    public static final String CLIENT_SECRET = "Client Secret";
    
    public static final String ORGANIZATION = "Organization";
    public static final String BUILDING = "Building";
    public static final String METER = "Meter";
    public static final String PUSH_INTERVAL = "Push Interval";
    public static final String MIN_UPDATE_INTERVAL = "Minimum time between updates";
    public static final String METER_TABLE = "Meter Table";
    
    public static final String TOKEN_URL = "https://api.buildingos.com/o/token/";
    public static final String GATEWAYS_URL = "https://api.buildingos.com/gateways";
    public static final String ORGS_URL = "https://api.buildingos.com/organizations/";
    public static final String BUILDINGS_URL = "https://api.buildingos.com/buildings/";
    public static final String METERS_URL = "https://api.buildingos.com/meters/";
    public static final String PLACEHOLDER_UUID = "%UUID%";
    public static final String DATA_FORMAT = "{\"meta\": {\"naive_timestamp_utc\": true},\"data\": {\"%UUID%\": [%STARTBLOCK%[\"%TIMESTAMP%\", %VALUE%]%ENDBLOCK%]}}";

    
    
}
