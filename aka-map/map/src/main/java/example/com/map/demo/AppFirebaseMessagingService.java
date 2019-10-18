package example.com.map.demo;

import com.akamai.android.sdk.AkaCommon;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class AppFirebaseMessagingService extends FirebaseMessagingService {

    @Override
    public void onMessageReceived(RemoteMessage message) {
        // If handleFirebaseMessage returns true, the message is for map sdk.
        if (AkaCommon.getInstance().handlePushNotification(message.getData())) {
            return;
        }

        // Handling for app notification.
    }

    @Override
    public void onNewToken(String s) {
        // App handling for tokens.
    }
}
