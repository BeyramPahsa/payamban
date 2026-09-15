package ir.payamban.app.data

import android.content.Context
import android.content.SharedPreferences
import ir.payamban.app.classify.Category
import ir.payamban.app.classify.Sensitivity
import org.json.JSONArray
import org.json.JSONObject

/** حافظهٔ محلی اپ: لیست بلاک، بازنویسی دستهٔ فرستنده‌ها و تنظیمات. */
class Store(context: Context) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("payamban", Context.MODE_PRIVATE)

    // ---------- لیست بلاک ----------
    var blockedSenders: Set<String>
        get() = sp.getStringSet(KEY_BLOCKED, emptySet()) ?: emptySet()
        private set(v) = sp.edit().putStringSet(KEY_BLOCKED, v).apply()

    fun block(canonical: String) {
        if (canonical.isBlank()) return
        blockedSenders = blockedSenders + canonical
    }

    fun unblock(canonical: String) {
        blockedSenders = blockedSenders - canonical
    }

    fun isBlocked(canonical: String): Boolean = blockedSenders.contains(canonical)

    // ---------- بازنویسی دستی دسته‌بندی ----------
    fun overrideFor(canonical: String): Category? =
        sp.getString(KEY_OVERRIDE + canonical, null)?.let { name ->
            runCatching { Category.valueOf(name) }.getOrNull()
        }

    fun setOverride(canonical: String, category: Category?) {
        sp.edit().apply {
            if (category == null) remove(KEY_OVERRIDE + canonical)
            else putString(KEY_OVERRIDE + canonical, category.name)
        }.apply()
    }

    // ---------- تنظیمات ----------
    var sensitivity: Sensitivity
        get() = runCatching {
            Sensitivity.valueOf(sp.getString(KEY_SENS, Sensitivity.BALANCED.name)!!)
        }.getOrDefault(Sensitivity.BALANCED)
        set(v) = sp.edit().putString(KEY_SENS, v.name).apply()

    /** بلاک خودکار فرستنده هنگام حذف پیام تبلیغاتی */
    var autoBlockOnDelete: Boolean
        get() = sp.getBoolean(KEY_AUTOBLOCK, true)
        set(v) = sp.edit().putBoolean(KEY_AUTOBLOCK, v).apply()

    /** آیا پیام فرستندهٔ بلاک‌شده اصلاً در گوشی ذخیره نشود */
    var dropBlocked: Boolean
        get() = sp.getBoolean(KEY_DROP, true)
        set(v) = sp.edit().putBoolean(KEY_DROP, v).apply()

    var onboarded: Boolean
        get() = sp.getBoolean(KEY_ONBOARDED, false)
        set(v) = sp.edit().putBoolean(KEY_ONBOARDED, v).apply()

    // ---------- کدهای تخفیف ذخیره‌شده (جدا از پیام، تا با حذف پیام از بین نروند) ----------
    fun savedCodesJson(): JSONArray =
        runCatching { JSONArray(sp.getString(KEY_CODES, "[]")) }.getOrDefault(JSONArray())

    fun saveCode(
        code: String,
        percent: Int?,
        expiry: String?,
        sourceName: String,
        sourceSender: String,
        receivedAt: Long,
        body: String
    ) {
        val arr = savedCodesJson()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optString("code") == code && o.optString("sender") == sourceSender) return
        }
        arr.put(JSONObject().apply {
            put("code", code)
            put("percent", percent ?: JSONObject.NULL)
            put("expiry", expiry ?: JSONObject.NULL)
            put("name", sourceName)
            put("sender", sourceSender)
            put("date", receivedAt)
            put("body", body)
        })
        sp.edit().putString(KEY_CODES, arr.toString()).apply()
    }

    fun removeCode(code: String, sender: String) {
        val arr = savedCodesJson()
        val out = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optString("code") == code && o.optString("sender") == sender) continue
            out.put(o)
        }
        sp.edit().putString(KEY_CODES, out.toString()).apply()
    }

    private companion object {
        const val KEY_BLOCKED = "blocked_senders"
        const val KEY_OVERRIDE = "override_"
        const val KEY_SENS = "sensitivity"
        const val KEY_AUTOBLOCK = "auto_block"
        const val KEY_DROP = "drop_blocked"
        const val KEY_CODES = "saved_codes"
        const val KEY_ONBOARDED = "onboarded"
    }
}
