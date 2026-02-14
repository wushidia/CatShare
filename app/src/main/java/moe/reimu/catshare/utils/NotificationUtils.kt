package moe.reimu.catshare.utils

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import moe.reimu.catshare.R

object NotificationUtils {
    const val RECEIVER_FG_CHAN_ID = "RECEIVER_FG"
    const val SENDER_CHAN_ID = "SENDER"
    const val RECEIVER_CHAN_ID = "RECEIVER"
    const val OTHER_CHAN_ID = "OTHER"
    
    // Android 16+ 实时通知通道
    const val LIVE_TRANSFER_CHAN_ID = "LIVE_TRANSFER"

    const val GATT_SERVER_FG_ID = 1
    const val RECEIVER_FG_ID = 2
    const val SENDER_FG_ID = 3

    fun createChannels(context: Context) {
        val manager = NotificationManagerCompat.from(context)

        val channels = mutableListOf(
            NotificationChannelCompat.Builder(
                RECEIVER_FG_CHAN_ID,
                NotificationManagerCompat.IMPORTANCE_LOW
            ).setName("Receiver persistent notification (can be disabled)").build(),
            NotificationChannelCompat.Builder(
                SENDER_CHAN_ID,
                NotificationManagerCompat.IMPORTANCE_HIGH
            ).setName("Sending files").build(),
            NotificationChannelCompat.Builder(
                RECEIVER_CHAN_ID,
                NotificationManagerCompat.IMPORTANCE_HIGH
            ).setName("Receiving files").build(),
            NotificationChannelCompat.Builder(
                OTHER_CHAN_ID,
                NotificationManagerCompat.IMPORTANCE_DEFAULT
            ).setName("Other notifications").build(),
        )
        
        // Android 16+ 实时通知通道
        if (Build.VERSION.SDK_INT >= 35) {
            channels.add(
                NotificationChannelCompat.Builder(
                    LIVE_TRANSFER_CHAN_ID,
                    NotificationManagerCompat.IMPORTANCE_HIGH
                ).setName("Live file transfer updates")
                    .setDescription("Real-time progress updates for file transfers")
                    .build()
            )
        }

        manager.createNotificationChannelsCompat(channels)
    }

    fun showBusyToast(context: Context) {
        Toast.makeText(context, R.string.app_busy_toast, Toast.LENGTH_LONG).show()
    }

    fun showBluetoothToast(context: Context) {
        Toast.makeText(context, R.string.bluetooth_disabled, Toast.LENGTH_LONG).show()
    }

    fun showWifiToast(context: Context) {
        Toast.makeText(context, R.string.wifi_disabled, Toast.LENGTH_LONG).show()
    }
    
    /**
     * 为 Android 16+ 创建实时通知构建器
     */
    fun createLiveNotificationBuilder(context: Context): NotificationCompat.Builder {
        val channelId = if (Build.VERSION.SDK_INT >= 35) {
            LIVE_TRANSFER_CHAN_ID
        } else {
            SENDER_CHAN_ID
        }
        
        return NotificationCompat.Builder(context, channelId).apply {
            setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            setPriority(NotificationCompat.PRIORITY_HIGH)
            setOnlyAlertOnce(true)
            
            // Android 16+ 实时通知优化
            if (Build.VERSION.SDK_INT >= 35) {
                setShowWhen(true)
                setUsesChronometer(false)
                // 启用实时更新模式
                setOngoing(true)
            }
        }
    }
}
