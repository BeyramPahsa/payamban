package ir.payamban.app.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import ir.payamban.app.classify.Category
import ir.payamban.app.classify.Classifier
import ir.payamban.app.classify.Normalizer

class SmsRepository(private val context: Context) {

    private val store = Store(context)

    /** نگاشت شمارهٔ نرمال‌شده -> نام مخاطب */
    fun loadContacts(): Map<String, String> {
        val map = HashMap<String, String>()
        runCatching {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                ),
                null, null, null
            )?.use { c ->
                val iNum = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val iName = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                while (c.moveToNext()) {
                    val key = Normalizer.canonicalSender(c.getString(iNum))
                    if (key.isNotEmpty()) map[key] = c.getString(iName) ?: key
                }
            }
        }
        return map
    }

    /** خواندن همهٔ پیامک‌های گوشی */
    fun loadMessages(limit: Int = 5000): List<Message> {
        val out = ArrayList<Message>()
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE,
            Telephony.Sms.READ
        )
        runCatching {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                projection,
                null, null,
                Telephony.Sms.DATE + " DESC"
            )?.use { c ->
                val iId = c.getColumnIndexOrThrow(Telephony.Sms._ID)
                val iThread = c.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
                val iAddr = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val iBody = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val iDate = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val iType = c.getColumnIndexOrThrow(Telephony.Sms.TYPE)
                val iRead = c.getColumnIndexOrThrow(Telephony.Sms.READ)
                while (c.moveToNext() && out.size < limit) {
                    val addr = c.getString(iAddr) ?: continue
                    out += Message(
                        id = c.getLong(iId),
                        threadId = c.getLong(iThread),
                        sender = addr,
                        canonicalSender = Normalizer.canonicalSender(addr),
                        body = c.getString(iBody) ?: "",
                        date = c.getLong(iDate),
                        incoming = c.getInt(iType) == Telephony.Sms.MESSAGE_TYPE_INBOX,
                        read = c.getInt(iRead) == 1
                    )
                }
            }
        }
        return out
    }

    /** خواندن + دسته‌بندی همهٔ پیامک‌ها */
    fun loadClassified(): List<ClassifiedMessage> {
        val contacts = loadContacts()
        val sens = store.sensitivity
        return loadMessages().map { m ->
            val name = contacts[m.canonicalSender]
            val base = Classifier.classify(
                sender = m.sender,
                body = m.body,
                isInContacts = name != null,
                sensitivity = sens
            )
            val override = store.overrideFor(m.canonicalSender)
            val verdict = if (override != null) base.copy(
                category = override,
                reasons = listOf("دستهٔ این فرستنده را خودتان تعیین کرده‌اید")
            ) else base
            ClassifiedMessage(m, verdict, name)
        }
    }

    fun groupBySender(items: List<ClassifiedMessage>): List<SenderGroup> =
        items.groupBy { it.message.canonicalSender }
            .map { (key, msgs) ->
                SenderGroup(
                    canonicalSender = key,
                    displayName = msgs.firstNotNullOfOrNull { it.contactName } ?: msgs.first().message.sender,
                    messages = msgs.sortedByDescending { it.message.date }
                )
            }
            .sortedByDescending { it.lastDate }

    /** حذف پیام‌ها از حافظهٔ گوشی — فقط وقتی اپ، اپِ پیش‌فرض پیامک باشد کار می‌کند */
    fun deleteMessages(ids: Collection<Long>): Int {
        var n = 0
        for (id in ids) {
            val uri: Uri = Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, id.toString())
            n += runCatching { context.contentResolver.delete(uri, null, null) }.getOrDefault(0)
        }
        return n
    }

    fun markRead(ids: Collection<Long>) {
        val cv = ContentValues().apply { put(Telephony.Sms.READ, 1) }
        for (id in ids) {
            val uri = Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, id.toString())
            runCatching { context.contentResolver.update(uri, cv, null, null) }
        }
    }

    companion object {
        /** دسته‌هایی که هرگز نباید پیشنهادِ حذف شوند */
        val PROTECTED = setOf(Category.CONTACT, Category.PERSONAL, Category.BANK, Category.OTP)
    }
}
