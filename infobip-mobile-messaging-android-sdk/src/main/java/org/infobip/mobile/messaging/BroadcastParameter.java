package org.infobip.mobile.messaging;

/**
 * @author pandric
 * @since 19.05.16.
 */
public final class BroadcastParameter {

    public static final String EXTRA_EXCEPTION = "org.infobip.mobile.messaging.exception";
    public static final String EXTRA_GCM_TOKEN = "org.infobip.mobile.messaging.gcm.token";
    public static final String EXTRA_INFOBIP_ID = "org.infobip.mobile.messaging.infobip.token";
    public static final String EXTRA_MESSAGE_IDS = "org.infobip.mobile.messaging.message.ids";
    public static final String EXTRA_GEOFENCE_AREAS = "org.infobip.mobile.messaging.geofenceAreas";
    public static final String EXTRA_MESSAGE = "org.infobip.mobile.messaging.message";
    public static final String EXTRA_USER_DATA = "org.infobip.mobile.messaging.userdata";
    public static final String EXTRA_SYSTEM_DATA = "org.infobip.mobile.messaging.systemdata";
    public static final String EXTRA_MESSAGES = "org.infobip.mobile.messaging.messages";
    public static final String EXTRA_PLAY_SERVICES_ERROR_CODE = "org.infobip.mobile.messaging.playServices";

    private BroadcastParameter() {
    }
}
