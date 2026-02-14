package moe.reimu.catshare.utils

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.text.format.Formatter
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import moe.reimu.catshare.BuildConfig
import moe.reimu.catshare.R
import java.util.concurrent.TimeUnit

/**
 * Android 16 实时通知管理器
 * 专门处理文件传输的实时进度更新
 */
class LiveTransferNotificationManager(private val context: Context) {
    
    private val notificationManager = NotificationManagerCompat.from(context)
    private var lastUpdateTime = 0L
    private var lastProcessedSize = 0L
    private var transferSpeed = 0L // bytes per second
    
    companion object {
        private const val UPDATE_INTERVAL_MS = 500L // 每500ms更新一次
        private const val SPEED_CALCULATION_WINDOW = 1000L // 1秒计算速度
    }
    
    /**
     * 创建发送文件的实时通知
     */
    fun createSendingLiveNotification(
        taskId: Int,
        targetName: String,
        fileName: String,
        totalSize: Long,
        processedSize: Long
    ): Notification {
        val builder = if (Build.VERSION.SDK_INT >= 35) {
            NotificationUtils.createLiveNotificationBuilder(context)
        } else {
            NotificationCompat.Builder(context, NotificationUtils.SENDER_CHAN_ID)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
        }
        
        val cancelIntent = PendingIntent.getBroadcast(
            context,
            taskId,
            Intent("${BuildConfig.APPLICATION_ID}.CANCEL_SENDING")
                .putExtra("taskId", taskId),
            PendingIntent.FLAG_IMMUTABLE
        )
        
        val progress = if (totalSize > 0) {
            (100.0 * processedSize / totalSize).toInt()
        } else 0
        
        // 计算传输速度
        calculateSpeed(processedSize)
        
        val speedText = if (transferSpeed > 0) {
            "${Formatter.formatShortFileSize(context, transferSpeed)}/s"
        } else {
            "计算中..."
        }
        
        val f1 = Formatter.formatShortFileSize(context, processedSize)
        val f2 = Formatter.formatShortFileSize(context, totalSize)
        val progressText = "$f1 / $f2 | $progress%"
        
        // 估算剩余时间
        val remainingText = if (transferSpeed > 0 && processedSize < totalSize) {
            val remainingBytes = totalSize - processedSize
            val remainingSeconds = remainingBytes / transferSpeed
            " · 剩余 ${formatTime(remainingSeconds)}"
        } else ""
        
        builder.apply {
            setSmallIcon(R.drawable.ic_upload_file)
            setContentTitle(context.getString(R.string.sending))
            setSubText(targetName)
            setContentText("$progressText · $speedText$remainingText")
            setProgress(100, progress, false)
            setOngoing(true)
            setOnlyAlertOnce(true)
            addAction(R.drawable.ic_close, context.getString(android.R.string.cancel), cancelIntent)
            
            // Android 16+ 增强样式
            if (Build.VERSION.SDK_INT >= 35) {
                setStyle(NotificationCompat.BigTextStyle()
                    .bigText("文件: $fileName\n$progressText\n速度: $speedText$remainingText"))
            }
        }
        
        return builder.build()
    }
    
    /**
     * 创建接收文件的实时通知
     */
    fun createReceivingLiveNotification(
        taskId: Int,
        senderName: String,
        fileName: String,
        totalSize: Long,
        processedSize: Long?
    ): Notification {
        val builder = if (Build.VERSION.SDK_INT >= 35) {
            NotificationUtils.createLiveNotificationBuilder(context)
        } else {
            NotificationCompat.Builder(context, NotificationUtils.RECEIVER_CHAN_ID)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
        }
        
        val cancelIntent = PendingIntent.getBroadcast(
            context,
            taskId,
            Intent("${BuildConfig.APPLICATION_ID}.CANCEL_RECEIVING")
                .putExtra("taskId", taskId),
            PendingIntent.FLAG_IMMUTABLE
        )
        
        builder.apply {
            setSmallIcon(R.drawable.ic_downloading)
            setContentTitle(context.getString(R.string.receiving))
            setSubText(senderName)
            setOngoing(true)
            setOnlyAlertOnce(true)
            addAction(R.drawable.ic_close, context.getString(android.R.string.cancel), cancelIntent)
        }
        
        if (processedSize != null && totalSize > 0) {
            val progress = (100.0 * processedSize / totalSize).toInt()
            calculateSpeed(processedSize)
            
            val speedText = if (transferSpeed > 0) {
                "${Formatter.formatShortFileSize(context, transferSpeed)}/s"
            } else {
                "计算中..."
            }
            
            val f1 = Formatter.formatShortFileSize(context, processedSize)
            val f2 = Formatter.formatShortFileSize(context, totalSize)
            val progressText = "$f1 / $f2 | $progress%"
            
            val remainingText = if (transferSpeed > 0 && processedSize < totalSize) {
                val remainingBytes = totalSize - processedSize
                val remainingSeconds = remainingBytes / transferSpeed
                " · 剩余 ${formatTime(remainingSeconds)}"
            } else ""
            
            builder.apply {
                setContentText("$progressText · $speedText$remainingText")
                setProgress(100, progress, false)
                
                if (Build.VERSION.SDK_INT >= 35) {
                    setStyle(NotificationCompat.BigTextStyle()
                        .bigText("文件: $fileName\n$progressText\n速度: $speedText$remainingText"))
                }
            }
        } else {
            builder.apply {
                setContentText(context.getString(R.string.preparing))
                setProgress(0, 0, true)
            }
        }
        
        return builder.build()
    }
    
    /**
     * 更新通知（带节流控制）
     */
    @SuppressLint("MissingPermission")
    fun updateNotificationThrottled(notificationId: Int, notification: Notification): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastUpdateTime >= UPDATE_INTERVAL_MS) {
            notificationManager.notify(notificationId, notification)
            lastUpdateTime = now
            return true
        }
        return false
    }
    
    /**
     * 强制更新通知（无节流）
     */
    @SuppressLint("MissingPermission")
    fun updateNotificationImmediate(notificationId: Int, notification: Notification) {
        notificationManager.notify(notificationId, notification)
        lastUpdateTime = System.currentTimeMillis()
    }
    
    /**
     * 计算传输速度
     */
    private fun calculateSpeed(currentProcessedSize: Long) {
        val now = System.currentTimeMillis()
        val timeDiff = now - lastUpdateTime
        
        if (timeDiff >= SPEED_CALCULATION_WINDOW && lastProcessedSize > 0) {
            val sizeDiff = currentProcessedSize - lastProcessedSize
            transferSpeed = (sizeDiff * 1000) / timeDiff // bytes per second
        }
        
        lastProcessedSize = currentProcessedSize
    }
    
    /**
     * 重置速度计算
     */
    fun resetSpeedCalculation() {
        lastUpdateTime = System.currentTimeMillis()
        lastProcessedSize = 0L
        transferSpeed = 0L
    }
    
    /**
     * 格式化时间
     */
    private fun formatTime(seconds: Long): String {
        return when {
            seconds < 60 -> "${seconds}秒"
            seconds < 3600 -> "${seconds / 60}分${seconds % 60}秒"
            else -> "${seconds / 3600}时${(seconds % 3600) / 60}分"
        }
    }
}
