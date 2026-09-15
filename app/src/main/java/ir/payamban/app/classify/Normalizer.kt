package ir.payamban.app.classify

/**
 * یکسان‌سازی متن فارسی/عربی قبل از تحلیل.
 * بدون این مرحله، «لغو۱۱» با ارقام فارسی و «لغو 11» دو چیز متفاوت دیده می‌شوند.
 */
object Normalizer {

    private const val PERSIAN_ZERO = '۰'   // ۰
    private const val ARABIC_ZERO = '٠'    // ٠

    /** حروف اعرابِ عربی که باید حذف شوند */
    private val DIACRITICS = Regex("[ً-ٰٟـ]")

    fun normalize(input: String?): String {
        if (input.isNullOrEmpty()) return ""
        val sb = StringBuilder(input.length)
        for (ch in input) {
            val c = when {
                ch in PERSIAN_ZERO..(PERSIAN_ZERO + 9) -> '0' + (ch - PERSIAN_ZERO)
                ch in ARABIC_ZERO..(ARABIC_ZERO + 9) -> '0' + (ch - ARABIC_ZERO)
                ch == 'ي' || ch == 'ى' -> 'ی'   // ي ,ى  ->  ی
                ch == 'ك' -> 'ک'                     // ك -> ک
                ch == 'ة' -> 'ه'                     // ة -> ه
                ch == 'أ' || ch == 'إ' || ch == 'آ' -> 'ا' // أإآ -> ا
                ch == '‌' || ch == '‏' || ch == '‎' -> ' '      // نیم‌فاصله و کنترل‌ها
                ch == '٫' -> '.'
                ch == '٬' -> ','
                else -> ch
            }
            sb.append(c)
        }
        return DIACRITICS.replace(sb.toString(), "")
            .replace(Regex("[ \t ]+"), " ")
            .trim()
    }

    /** فقط ارقام لاتین یک رشته (برای مقایسه شماره‌ها) */
    fun digitsOnly(input: String?): String =
        normalize(input).filter { it.isDigit() }

    /**
     * شماره را به شکل قابل مقایسه درمی‌آورد:
     * +989121234567 و 00989121234567 و 09121234567 همگی «9121234567» می‌شوند.
     */
    fun canonicalSender(raw: String?): String {
        val n = normalize(raw)
        if (n.isEmpty()) return ""
        // سرشماره‌های حرفی (مثل bank-melli) همان‌طور می‌مانند
        if (n.any { it.isLetter() }) return n.lowercase()
        var d = n.filter { it.isDigit() }
        if (d.startsWith("0098")) d = d.removePrefix("0098")
        if (d.startsWith("98") && d.length > 10) d = d.removePrefix("98")
        if (d.startsWith("0") && d.length == 11) d = d.substring(1)
        return d
    }


    /**
     * تطبیق واژه‌ای — نه صرفاً «رشته درون رشته».
     * بدون این، «پایا» داخل «پایان» و «چک» داخل «کوچک» پیدا می‌شود و
     * پیام‌ها اشتباه دسته‌بندی می‌شوند.
     *
     * واژه‌های کوتاه (تا ۴ حرف) باید کاملاً مستقل باشند؛ واژه‌های بلندتر
     * اجازه دارند پسوند بگیرند («تخفیف» در «تخفیفات»).
     */
    fun containsWord(text: String, word: String): Boolean {
        if (word.isEmpty() || text.isEmpty()) return false
        val strict = word.length <= 4
        var idx = text.indexOf(word)
        while (idx >= 0) {
            val beforeOk = idx == 0 || !text[idx - 1].isLetterOrDigit()
            val end = idx + word.length
            val afterOk = !strict || end >= text.length || !text[end].isLetterOrDigit()
            if (beforeOk && afterOk) return true
            idx = text.indexOf(word, idx + 1)
        }
        return false
    }

    fun countWords(text: String, words: List<String>): Int =
        words.count { containsWord(text, it) }

    /** آیا این فرستنده یک شماره موبایل شخصی ایرانی است؟ */
    fun isPersonalMobile(raw: String?): Boolean {
        val c = canonicalSender(raw)
        return c.length == 10 && c.startsWith("9") && c.all { it.isDigit() }
    }

    /** آیا فرستنده یک سرشماره تبلیغاتی/خدماتی است؟ */
    fun isShortCode(raw: String?): Boolean {
        val n = normalize(raw)
        if (n.isEmpty()) return false
        if (n.any { it.isLetter() }) return true          // سرشماره حرفی
        if (isPersonalMobile(raw)) return false
        val d = n.filter { it.isDigit() }
        return d.length in 3..15
    }
}
