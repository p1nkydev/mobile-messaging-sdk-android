package org.infobip.mobile.messaging;

import android.content.BroadcastReceiver;

/**
 * Enumerates all events generated by the Mobile Messaging library.
 * It is intended to be used in <i>BroadcastReceivers</i> registered locally or at the application level.
 * <p>
 * You can register receivers in AndroidManifest.xml by adding:
 * <pre>
 * {@code <receiver android:name=".MyMessageReceiver" android:exported="false">
 *       <intent-filter>
 *           <action android:name="org.infobip.mobile.messaging.MESSAGE_RECEIVED"/>
 *        </intent-filter>
 *   </receiver>}
 * </pre>
 * <p>
 * You can also register receivers in you Activity by adding:
 * <pre>
 * {@code
 * public class MyActivity extends AppCompatActivity {
 *        private boolean isReceiverRegistered;
 *        private BroadcastReceiver messageReceiver = new BroadcastReceiver() {
 *            public void onReceive(Context context, Intent intent) {
 *                Message message = new Message(intent.getExtras());
 *                ... process your message here
 *            }
 *        };
 *
 *        protected void onCreate(Bundle savedInstanceState) {
 *            super.onCreate(savedInstanceState);
 *
 *            registerReceiver();
 *        }
 *
 *        protected void onResume() {
 *            super.onResume();
 *            registerReceiver();
 *        }
 *
 *        protected void onPause() {
 *            LocalBroadcastManager.getInstance(this).unregisterReceiver(messageReceiver);
 *            isReceiverRegistered = false;
 *            super.onPause();
 *        }
 *
 *        private void registerReceiver() {
 *            if (isReceiverRegistered) {
 *                return;
 *            }
 *            LocalBroadcastManager.getInstance(this).registerReceiver(messageReceiver,
 *            new IntentFilter(Event.MESSAGE_RECEIVED.getKey()));
 *            isReceiverRegistered = true;
 *        }
 *    }}
 * </pre>
 *
 * @author mstipanov
 * @see BroadcastReceiver
 * @see Event#MESSAGE_RECEIVED
 * @see Event#REGISTRATION_ACQUIRED
 * @see Event#REGISTRATION_CREATED
 * @see Event#DELIVERY_REPORTS_SENT
 * @see Event#API_COMMUNICATION_ERROR
 * @since 01.03.2016.
 */
public enum Event {
    /**
     * It is triggered when GCM registration token is received.
     * <p>
     * Contains the GCM registration token.
     * <pre>
     * {@code
     * String registrationId = intent.getStringExtra("registrationId");
     * }
     * </pre>
     */
    REGISTRATION_ACQUIRED("org.infobip.mobile.messaging.REGISTRATION_ACQUIRED"),

    /**
     * It is triggered when GCM registration token successfully stored on the registration server.
     * <p>
     * Contains the GCM registration token and the device application instance ID
     * (which identifies every application instance).
     * <pre>
     * {@code
     * String registrationId = intent.getStringExtra("registrationId");
     *        String deviceApplicationInstanceId = intent.getStringExtra("deviceApplicationInstanceId");
     * }
     * </pre>
     */
    REGISTRATION_CREATED("org.infobip.mobile.messaging.REGISTRATION_CREATED"),

    /**
     * It is triggered when message is received.
     * <p>
     * Contains the received message information.
     * <pre>
     * {@code
     * Message message = new Message(intent.getExtras());
     * }
     * </pre>
     *
     * @see Message
     */
    MESSAGE_RECEIVED("org.infobip.mobile.messaging.MESSAGE_RECEIVED"),

    /**
     * It is triggered on every error returned by API.
     * <p>
     * Contains the exception information.
     * <pre>
     * {@code
     * Throwable exception = (Throwable) intent.getSerializableExtra("exception");
     * }
     * </pre>
     */
    API_COMMUNICATION_ERROR("org.infobip.mobile.messaging.API_COMMUNICATION_ERROR"),

    /**
     * It is triggered when message delivery is reported.
     * <p>
     * Contains the list of all reported message IDs.
     * <pre>
     * {@code
     * String[] messageIDs = intent.getStringArrayExtra("messageIDs");
     * }
     * </pre>
     */
    DELIVERY_REPORTS_SENT("org.infobip.mobile.messaging.DELIVERY_REPORTS_SENT");

    private final String key;

    Event(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }
}
