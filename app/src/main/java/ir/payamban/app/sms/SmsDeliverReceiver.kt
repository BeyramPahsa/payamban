package ir.payamban.app.sms

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import ir.payamban.app.classify.Category
import ir.payamban.app.classify.Classifier
import ir.payamban.app.classify.Normalizer
import ir.payamban.app.data.SmsRepository
import ir.payamban.app.data.Store

/**
 * قلب برنامه: وقتی «پیام‌بان» اپِ پیش‌فرض پیامک باشد، اندروید هر پیامک ورودی را
 * به همین گیرنده می‌دهد و *فقط* همین‌جا تصمیم گرفته می‌شود که پیام اصلاً در گوشی
 * ذخیره شود یا نه. بلاک واقعی یعنی همین: پیام هرگز نوشته نمی‌شود.
 */
class SmsDeliverReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return

        val parts = runCatching { Telephony.Sms.Intents.getMessagesFromIntent(intent) }
            .getOrNull() ?: return
        if (parts.isEmpty()) return

        val sender = parts[0].displayOriginatingAddress ?: return
        val body = parts.joinToString("") { it.displayMessageBody ?: "" }
        val timestamp = parts[0].timestampMillis.takeIf { it > 0 } ?: System.currentTimeMillis()
        val canonical = Normalizer.canonicalSender(sender)

        val store = Store(context)

        // ۱) فرستندهٔ بلاک‌شده: پیام اصلاً ذخیره نمی‌شود
        if (store.isBlocked(canonical) && store.dropBlocked) return

        // ۲) دسته‌بندی
        val repo = SmsRepository(context)
        val contactName = runCatching { repo.loadContacts()[canonical] }.getOrNull()
        val verdict = Classifier.classify(
            sender = sender,
            body = body,
            isInContacts = contactName != null,
            sensitivity = store.sensitivity
        )
        val category = store.overrideFor(canonical) ?: verdict.category

        // ۳) نوشتن در صندوق ورودی گوشی
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, sender)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, timestamp)
            put(Telephony.Sms.DATE_SENT, timestamp)
            put(Telephony.Sms.READ, if (category == Category.PROMO) 1 else 0)
            put(Telephony.Sms.SEEN, if (category == Category.PROMO) 1 else 0)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
        }
        runCatching { context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values) }

        // ۴) کد تخفیف را جدا نگه دار تا با حذف پیام از بین نرود
        for (code in verdict.codes) {
            store.saveCode(
                code = code.code,
                percent = code.percent,
                expiry = code.expiry,
                sourceName = contactName ?: sender,
                sourceSender = canonical,
                receivedAt = timestamp,
                body = body
            )
        }

        // ۵) اعلان فقط برای پیام‌های غیرتبلیغاتی
        if (category != Category.PROMO) {
            Notifier.notifyMessage(
                context = context,
                title = contactName ?: sender,
                body = body,
                id = (canonical.hashCode() and 0x7FFFFFFF)
            )
        }
    }
}
