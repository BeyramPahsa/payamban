package ir.payamban.app.sms

import android.app.Service
import android.content.Intent
import android.os.IBinder

/** پاسخ سریع از صفحهٔ تماس — وجودش برای اپِ پیش‌فرض پیامک الزامی است. */
class HeadlessSmsSendService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        stopSelf(startId)
        return START_NOT_STICKY
    }
}
