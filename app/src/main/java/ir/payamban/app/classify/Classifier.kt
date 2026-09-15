package ir.payamban.app.classify

enum class Category(val fa: String) {
    CONTACT("مخاطبین"),
    PERSONAL("شخصی"),
    OTP("کد تأیید"),
    BANK("بانکی"),
    SERVICE("خدماتی"),
    PROMO("تبلیغاتی"),
    UNKNOWN("نامشخص")
}

data class DiscountCode(
    val code: String,
    val percent: Int?,
    val expiry: String?
)

data class Verdict(
    val category: Category,
    val score: Int,
    val reasons: List<String>,
    val codes: List<DiscountCode>
)

/** حساسیت تشخیص تبلیغات — آستانهٔ امتیاز */
enum class Sensitivity(val fa: String, val threshold: Int) {
    STRICT("سخت‌گیر", 35),
    BALANCED("متعادل", 45),
    SAFE("محافظه‌کار", 62)
}

object Classifier {

    private val optOut = Rules.OPT_OUT
    private val otp = Rules.OTP.map { Normalizer.normalize(it) }
    private val bank = Rules.BANK.map { Normalizer.normalize(it) }
    private val bankNames = Rules.BANK_NAMES.map { Normalizer.normalize(it) }
    private val promo = Rules.PROMO.map { Normalizer.normalize(it) }
    private val keep = Rules.KEEP.map { Normalizer.normalize(it) }

    fun classify(
        sender: String?,
        body: String?,
        isInContacts: Boolean,
        sensitivity: Sensitivity = Sensitivity.BALANCED
    ): Verdict {
        val text = Normalizer.normalize(body)
        val low = text.lowercase()
        val reasons = mutableListOf<String>()
        val codes = extractCodes(text)

        if (isInContacts) {
            return Verdict(Category.CONTACT, 0, listOf("فرستنده در مخاطبین شماست"), emptyList())
        }

        // --- کد تأیید: همیشه قبل از بقیه، چون هرگز نباید تبلیغات شمرده شود
        if (otp.any { Normalizer.containsWord(low, it) }) {
            return Verdict(Category.OTP, 0, listOf("حاوی کد تأیید / رمز یکبارمصرف"), emptyList())
        }

        val hasMoney = Rules.MONEY.containsMatchIn(text)
        val bankHits = Normalizer.countWords(text, bank) + Normalizer.countWords(text, bankNames)
        if (hasMoney && bankHits > 0) {
            return Verdict(Category.BANK, 0, listOf("پیام تراکنش بانکی (مبلغ + نام/واژهٔ بانکی)"), emptyList())
        }

        val keepHits = keep.filter { Normalizer.containsWord(text, it) }

        // --- امتیازدهی تبلیغاتی
        var score = 0
        if (optOut.any { it.containsMatchIn(text) }) {
            score += 60
            reasons += "دارای «لغو ...» — امضای قانونی پیامک تبلیغاتی"
        }
        if (Normalizer.isShortCode(sender)) {
            score += 12
            reasons += "از سرشمارهٔ انبوه ارسال شده، نه شمارهٔ شخصی"
        }
        val promoHits = promo.filter { Normalizer.containsWord(text, it) }
        if (promoHits.isNotEmpty()) {
            val add = minOf(promoHits.size * 10, 40)
            score += add
            reasons += "واژگان تبلیغاتی: " + promoHits.take(4).joinToString("، ")
        }
        if (Rules.URL.containsMatchIn(text)) {
            score += 12
            reasons += "حاوی لینک"
        }
        if (text.length > 170) {
            score += 5
            reasons += "متن بلند (مشخصهٔ پیام انبوه)"
        }
        if (Normalizer.isPersonalMobile(sender)) {
            score -= 35
            reasons += "از شمارهٔ موبایل شخصی"
        }
        if (bankHits > 0) score -= 20
        if (keepHits.isNotEmpty()) {
            score -= 30
            reasons += "واژهٔ مهم: " + keepHits.take(3).joinToString("، ")
        }

        if (score >= sensitivity.threshold) {
            return Verdict(Category.PROMO, score, reasons, codes)
        }

        return when {
            Normalizer.isPersonalMobile(sender) ->
                Verdict(Category.PERSONAL, score, listOf("شمارهٔ شخصی، ذخیره‌نشده در مخاطبین"), emptyList())
            Normalizer.isShortCode(sender) ->
                Verdict(Category.SERVICE, score, listOf("پیام خدماتی از سرشماره"), codes)
            else ->
                Verdict(Category.UNKNOWN, score, reasons, codes)
        }
    }

    /** استخراج کد تخفیف، درصد و مهلت از متنِ نرمال‌شده */
    fun extractCodes(text: String): List<DiscountCode> {
        if (text.isEmpty()) return emptyList()
        val mentionsDiscount = text.contains("تخفیف") || text.contains("کوپن") ||
            text.contains("بن خرید") || Regex("(?i)off|discount|promo").containsMatchIn(text)
        if (!mentionsDiscount) return emptyList()

        val percent = Rules.PERCENT.find(text)?.groupValues?.get(1)?.toIntOrNull()
        val expiry = Rules.EXPIRY.firstNotNullOfOrNull { it.find(text)?.groupValues?.get(1)?.trim() }

        val found = LinkedHashSet<String>()
        for (p in Rules.CODE_PATTERNS) {
            for (m in p.findAll(text)) {
                val raw = m.groupValues.getOrNull(1)?.trim()?.trim('.', '،', ':', '-', '_') ?: continue
                if (raw.length < 3 || raw.length > 20) continue
                if (raw.all { it.isDigit() } && raw.length in 4..6 && !text.contains("کد تخفیف")) continue
                if (Rules.URL.matches(raw)) continue
                found += raw
            }
        }
        if (found.isEmpty() && percent != null) {
            // تخفیف بدون کد مشخص — باز هم ارزش نگه داشتن دارد
            return listOf(DiscountCode(code = "—", percent = percent, expiry = expiry))
        }
        return found.map { DiscountCode(it, percent, expiry) }
    }
}
