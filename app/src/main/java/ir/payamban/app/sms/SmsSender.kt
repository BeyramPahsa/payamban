package ir.payamban.app.sms

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager

object SmsSender {

    fun send(context: Context, address: String, body: String): Boolean = runCatching {
        val manager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
        val parts = manager.divideMessage(body)
        manager.sendMultipartTextMessage(address, null, parts, null, null)

        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, System.currentTimeMillis())
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
        }
        context.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
        true
    }.getOrDefault(false)
}
