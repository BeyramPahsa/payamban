package ir.payamban.app.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * برای اینکه اندروید اپ را به عنوان اپ پیش‌فرض پیامک بپذیرد، وجود این گیرنده الزامی است.
 * پیام‌بان روی SMS تمرکز دارد، بنابراین MMS فقط دریافت و نادیده گرفته می‌شود.
 */
class MmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Unit
}
