package org.infobip.mobile.messaging.reporters;

import android.content.Context;
import android.util.Log;

import org.infobip.mobile.messaging.Message;
import org.infobip.mobile.messaging.MobileMessagingCore;
import org.infobip.mobile.messaging.stats.MobileMessagingError;
import org.infobip.mobile.messaging.stats.MobileMessagingStats;
import org.infobip.mobile.messaging.storage.MessageStore;
import org.infobip.mobile.messaging.tasks.SyncMessagesResult;
import org.infobip.mobile.messaging.tasks.SyncMessagesTask;

import java.util.List;
import java.util.concurrent.Executor;

import static org.infobip.mobile.messaging.MobileMessaging.TAG;

/**
 * @author pandric on 09/09/16.
 */
public class MessagesSynchronizer {

    public void synchronize(Context context, MobileMessagingStats stats, Executor executor) {
        syncMessages(context, stats, executor);
    }

    private void syncMessages(final Context context, final MobileMessagingStats stats, Executor executor) {
        new SyncMessagesTask(context) {

            @Override
            protected void onPostExecute(SyncMessagesResult syncMessagesResult) {
                if (syncMessagesResult.hasError()) {
                    Log.e(TAG, "MobileMessaging API returned error!");
                    stats.reportError(MobileMessagingError.SYNC_MESSAGES_ERROR);
                    return;
                }

                saveMessages(syncMessagesResult, context);
            }

            @Override
            protected void onCancelled() {
                Log.e(TAG, "Error syncing messages!");
                stats.reportError(MobileMessagingError.SYNC_MESSAGES_ERROR);
            }
        }.executeOnExecutor(executor);
    }

    private void saveMessages(SyncMessagesResult syncMessagesResult, Context context) {
        MobileMessagingCore mobileMessagingCore = MobileMessagingCore.getInstance(context);

        List<Message> messages = syncMessagesResult.getMessages();
        if (messages == null || messages.isEmpty()) {
            return;
        }

        for (Message message : messages) {
            MessageStore messageStore = mobileMessagingCore.getMessageStore();
            if (message != null) {
                messageStore.save(context, message);
            }
        }
    }
}
