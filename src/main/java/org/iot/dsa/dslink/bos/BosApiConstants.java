package org.iot.dsa.dslink.bos;

public class BosApiConstants {
    
    public static final String URL = "url";
    public static final String DATA = "data";
    public static final String META = "meta";
    public static final String DEFINITIONS = "definitions";
    
    public static final String ID = "id";
    public static final String NAME = "name";
    public static final String DISP_NAME = "displayName";
    
    public static final String BUILDINGS = "buildings";
    
    public static final String VENDOR_BUILDING_ID = "vendorBuildingId";
    public static final String ADDRESS = "address";
    public static final String DESCRIPTION = "description";
    public static final String POST_CODE = "postalCode";
    public static final String COUNTRY_CODE = "countryCode";
    public static final String AREA = "area";
    public static final String LONGITUDE = "longitude";
    public static final String LATITUDE = "latitude";
    public static final String GEOCODED = "geocoded";
    public static final String ORGANIZATION = "organization";
    public static final String METERS = "meters";
    
    public static final String UUID = "uuid";
    public static final String BUILDING = "building";
    public static final String GATEWAY = "gateway";
    public static final String STORAGE_UNIT = "storageUnit";
    public static final String VENDOR_METER_ID = "vendorMeterId";
    
    public static final String START = "start";
    public static final String END = "end";
    public static final String ORDER = "order";
    public static final String RESOLUTION = "resolution";
    public static final String INCLUDE = "include";
    
    public static final String RECORD_TS = "localtime";
    public static final String RECORD_VALUE = "value";
    
    public static enum OrderOptions {
        asc,
        desc
    }
    
    public static enum ResolutionOptions {
        live,
        quarterhour,
        hour,
        day,
        month
    }
    
    public static enum IncludeOptions {
        cost,
        consumption,
        none
    }
}
