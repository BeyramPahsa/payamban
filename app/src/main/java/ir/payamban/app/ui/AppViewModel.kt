package ir.payamban.app.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ir.payamban.app.classify.Category
import ir.payamban.app.classify.Sensitivity
import ir.payamban.app.data.ClassifiedMessage
import ir.payamban.app.data.SavedCode
import ir.payamban.app.data.SenderGroup
import ir.payamban.app.data.SmsRepository
import ir.payamban.app.data.Store
import ir.payamban.app.classify.DiscountCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UiState(
    val loading: Boolean = true,
    val hasPermission: Boolean = false,
    val isDefaultSmsApp: Boolean = false,
    val all: List<ClassifiedMessage> = emptyList(),
    val chats: List<SenderGroup> = emptyList(),
    val promo: List<SenderGroup> = emptyList(),
    val codes: List<SavedCode> = emptyList(),
    val blocked: Set<String> = emptySet(),
    val lastActionMessage: String? = null
) {
    val promoMessageCount: Int get() = promo.sumOf { it.count }
    val chatMessageCount: Int get() = chats.sumOf { it.count }
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = SmsRepository(app)
    val store = Store(app)

    var state by mutableStateOf(UiState())
        private set

    /** فرستنده‌هایی که کاربر در صفحهٔ تبلیغات انتخاب کرده */
    var selected by mutableStateOf<Set<String>>(emptySet())
        private set

    var sensitivity by mutableStateOf(store.sensitivity)
        private set

    fun setPermission(granted: Boolean, isDefault: Boolean) {
        state = state.copy(hasPermission = granted, isDefaultSmsApp = isDefault)
        if (granted) refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            state = state.copy(loading = true)
            val items = withContext(Dispatchers.IO) { repo.loadClassified() }
            val groups = withContext(Dispatchers.IO) { repo.groupBySender(items) }

            // هر فرستنده بر اساس دستهٔ غالبِ پیام‌هایش در یکی از دو تب می‌نشیند
            val (promo, chats) = groups.partition { g -> isPromoSender(g) }
            val codes = withContext(Dispatchers.IO) { collectCodes(items) }

            state = state.copy(
                loading = false,
                all = items,
                chats = chats,
                promo = promo,
                codes = codes,
                blocked = store.blockedSenders
            )
        }
    }

    /** فرستنده وقتی تبلیغاتی است که بیشترِ پیام‌هایش تبلیغاتی تشخیص داده شده باشند */
    private fun isPromoSender(g: SenderGroup): Boolean {
        val promoCount = g.messages.count { it.category == Category.PROMO }
        val protectedCount = g.messages.count { it.category in SmsRepository.PROTECTED }
        if (protectedCount > 0) return false
        return promoCount * 2 > g.count
    }

    /** کدهای تخفیف: هم آنچه در حافظهٔ اپ ذخیره شده، هم آنچه در پیام‌های فعلی پیدا می‌شود */
    private fun collectCodes(items: List<ClassifiedMessage>): List<SavedCode> {
        val out = LinkedHashMap<String, SavedCode>()

        val saved = store.savedCodesJson()
        for (i in 0 until saved.length()) {
            val o = saved.optJSONObject(i) ?: continue
            val code = o.optString("code")
            val sender = o.optString("sender")
            out["$sender|$code"] = SavedCode(
                code = DiscountCode(
                    code = code,
                    percent = if (o.isNull("percent")) null else o.optInt("percent"),
                    expiry = if (o.isNull("expiry")) null else o.optString("expiry")
                ),
                sourceName = o.optString("name"),
                sourceSender = sender,
                receivedAt = o.optLong("date"),
                body = o.optString("body")
            )
        }

        for (m in items) {
            for (c in m.verdict.codes) {
                val key = "${m.message.canonicalSender}|${c.code}"
                if (out.containsKey(key)) continue
                out[key] = SavedCode(
                    code = c,
                    sourceName = m.displayName,
                    sourceSender = m.message.canonicalSender,
                    receivedAt = m.message.date,
                    body = m.message.body
                )
            }
        }
        return out.values.sortedByDescending { it.receivedAt }
    }

    // ---------- انتخاب ----------
    fun toggleSelect(sender: String) {
        selected = if (selected.contains(sender)) selected - sender else selected + sender
    }

    fun selectAllPromo() {
        selected = state.promo.map { it.canonicalSender }.toSet()
    }

    fun clearSelection() {
        selected = emptySet()
    }

    // ---------- عملیات ----------
    /** حذف پیام‌های تبلیغاتیِ فرستنده‌های انتخاب‌شده (و در صورت تمایل، بلاک آن‌ها) */
    fun deleteSelected(alsoBlock: Boolean) {
        val senders = selected
        if (senders.isEmpty()) return
        viewModelScope.launch {
            val ids = state.promo
                .filter { it.canonicalSender in senders }
                .flatMap { g -> g.messages.filter { it.category == Category.PROMO } }
                .map { it.message.id }

            // کد تخفیف را قبل از حذف پیام، دائمی کن
            withContext(Dispatchers.IO) {
                state.promo.filter { it.canonicalSender in senders }.forEach { g ->
                    g.messages.forEach { m ->
                        m.verdict.codes.forEach { c ->
                            store.saveCode(
                                c.code, c.percent, c.expiry,
                                m.displayName, m.message.canonicalSender,
                                m.message.date, m.message.body
                            )
                        }
                    }
                }
                if (alsoBlock) senders.forEach { store.block(it) }
                repo.deleteMessages(ids)
            }
            clearSelection()
            state = state.copy(
                lastActionMessage = if (alsoBlock)
                    "${ids.size} پیام حذف شد و ${senders.size} فرستنده بلاک شدند"
                else "${ids.size} پیام حذف شد"
            )
            refresh()
        }
    }

    fun blockSender(sender: String) {
        store.block(sender)
        state = state.copy(blocked = store.blockedSenders, lastActionMessage = "فرستنده بلاک شد")
        refresh()
    }

    fun unblockSender(sender: String) {
        store.unblock(sender)
        state = state.copy(blocked = store.blockedSenders, lastActionMessage = "بلاک برداشته شد")
        refresh()
    }

    /** کاربر می‌گوید این فرستنده تبلیغاتی نیست (یا هست) */
    fun overrideCategory(sender: String, category: Category?) {
        store.setOverride(sender, category)
        refresh()
    }

    fun applySensitivity(s: Sensitivity) {
        store.sensitivity = s
        sensitivity = s
        refresh()
    }

    fun setAutoBlock(v: Boolean) { store.autoBlockOnDelete = v }
    fun setDropBlocked(v: Boolean) { store.dropBlocked = v }

    fun removeCode(code: String, sender: String) {
        store.removeCode(code, sender)
        refresh()
    }

    fun consumeMessage() {
        state = state.copy(lastActionMessage = null)
    }
}
