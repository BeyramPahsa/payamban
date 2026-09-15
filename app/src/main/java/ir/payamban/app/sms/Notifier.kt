package ir.payamban.app.sms

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import ir.payamban.app.MainActivity
import ir.payamban.app.R

object Notifier {

    private const val CHANNEL_ID = "payamban_messages"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(NotificationManager::class.java)
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "پیام‌های مهم",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply { description = "فقط پیام‌هایی که تبلیغاتی تشخیص داده نشده‌اند" }
                )
            }
        }
    }

    fun notifyMessage(context: Context, title: String, body: String, id: Int) {
        ensureChannel(context)
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pi = PendingIntent.getActivity(context, id, intent, flags)

        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_message)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pi)
            .build()

        runCatching { NotificationManagerCompat.from(context).notify(id, n) }
    }
}
