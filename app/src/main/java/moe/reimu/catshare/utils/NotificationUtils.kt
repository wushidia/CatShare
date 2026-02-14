package moe.reimu.catshare.utils

import android.app.NotificationChannel
import android.app.NotificationManager
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
            ).setName("Receiver persistent notification").build(),
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
        
        // Android 16+ 实时通知通道 - 使用原生 API 以支持更多特性
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val systemManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // 创建实时传输通道
            if (Build.VERSION.SDK_INT >= 35) {
                val liveChannel = NotificationChannel(
                    LIVE_TRANSFER_CHAN_ID,
                    "实时文件传输",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "显示文件传输的实时进度更新"
                    enableLights(false)
                    enableVibration(false)
                    setShowBadge(false)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                }
                systemManager.createNotificationChannel(liveChannel)
            }
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
            setOnlyAlertOnce(true)
            
            if (Build.VERSION.SDK_INT >= 35) {
                priority = NotificationCompat.PRIORITY_HIGH
                setCategory(NotificationCompat.CATEGORY_PROGRESS)
                setShowWhen(false)
                setOngoing(true)
            } else {
                priority = NotificationCompat.PRIORITY_DEFAULT
            }
        }
    }
}
