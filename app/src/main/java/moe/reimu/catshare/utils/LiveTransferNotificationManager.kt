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
        private const val UPDATE_INTERVAL_MS = 300L // 每300ms更新一次，更频繁
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
        val builder = createLiveNotificationBuilder()
        
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
        val progressText = "$f1 / $f2"
        
        // 估算剩余时间
        val remainingText = if (transferSpeed > 0 && processedSize < totalSize) {
            val remainingBytes = totalSize - processedSize
            val remainingSeconds = remainingBytes / transferSpeed
            " · ${formatTime(remainingSeconds)}"
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
            
            // Android 16+ 实时通知关键配置
            if (Build.VERSION.SDK_INT >= 35) {
                // 设置为进度类别
                setCategory(NotificationCompat.CATEGORY_PROGRESS)
                
                // 启用实时更新
                setShowWhen(false)
                setUsesChronometer(false)
                
                // 使用 BigTextStyle 显示更多信息
                setStyle(NotificationCompat.BigTextStyle()
                    .bigText("文件: $fileName\n进度: $progressText ($progress%)\n速度: $speedText$remainingText"))
                
                // 设置优先级为高
                priority = NotificationCompat.PRIORITY_HIGH
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
        val builder = createLiveNotificationBuilder()
        
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
            
            // Android 16+ 实时通知关键配置
            if (Build.VERSION.SDK_INT >= 35) {
                setCategory(NotificationCompat.CATEGORY_PROGRESS)
                setShowWhen(false)
                setUsesChronometer(false)
                priority = NotificationCompat.PRIORITY_HIGH
            }
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
            val progressText = "$f1 / $f2"
            
            val remainingText = if (transferSpeed > 0 && processedSize < totalSize) {
                val remainingBytes = totalSize - processedSize
                val remainingSeconds = remainingBytes / transferSpeed
                " · ${formatTime(remainingSeconds)}"
            } else ""
            
            builder.apply {
                setContentText("$progressText · $speedText$remainingText")
                setProgress(100, progress, false)
                
                if (Build.VERSION.SDK_INT >= 35) {
                    setStyle(NotificationCompat.BigTextStyle()
                        .bigText("文件: $fileName\n进度: $progressText ($progress%)\n速度: $speedText$remainingText"))
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
     * 创建实时通知构建器
     */
    private fun createLiveNotificationBuilder(): NotificationCompat.Builder {
        val channelId = if (Build.VERSION.SDK_INT >= 35) {
            NotificationUtils.LIVE_TRANSFER_CHAN_ID
        } else {
            NotificationUtils.SENDER_CHAN_ID
        }
        
        return NotificationCompat.Builder(context, channelId).apply {
            setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            setOnlyAlertOnce(true)
            
            if (Build.VERSION.SDK_INT >= 35) {
                // Android 16+ 特定配置
                priority = NotificationCompat.PRIORITY_HIGH
                setCategory(NotificationCompat.CATEGORY_PROGRESS)
                setShowWhen(false)
                setOngoing(true)
            } else {
                priority = NotificationCompat.PRIORITY_DEFAULT
            }
        }
    }
    
    /**
     * 更新通知（带节流控制）
     */
    @SuppressLint("MissingPermission")
    fun updateNotificationThrottled(notificationId: Int, notification: Notification): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastUpdateTime >= UPDATE_INTERVAL_MS) {
            try {
                notificationManager.notify(notificationId, notification)
                lastUpdateTime = now
                return true
            } catch (e: SecurityException) {
                // 权限被拒绝
                e.printStackTrace()
            }
        }
        return false
    }
    
    /**
     * 强制更新通知（无节流）
     */
    @SuppressLint("MissingPermission")
    fun updateNotificationImmediate(notificationId: Int, notification: Notification) {
        try {
            notificationManager.notify(notificationId, notification)
            lastUpdateTime = System.currentTimeMillis()
        } catch (e: SecurityException) {
            // 权限被拒绝
            e.printStackTrace()
        }
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
            seconds < 60 -> "剩余${seconds}秒"
            seconds < 3600 -> "剩余${seconds / 60}分钟"
            else -> "剩余${seconds / 3600}小时"
        }
    }
}
