package ir.payamban.app.util

import java.util.Calendar
import java.util.Date

/** تبدیل تاریخ میلادی به شمسی برای نمایش. */
object PersianDate {

    private val MONTHS = arrayOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
    )

    private fun toJalali(gy: Int, gm: Int, gd: Int): Triple<Int, Int, Int> {
        val gDaysInMonth = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        val jDaysInMonth = intArrayOf(31, 31, 31, 31, 31, 31, 30, 30, 30, 30, 30, 29)

        val gy2 = gy - 1600
        val gm2 = gm - 1
        val gd2 = gd - 1

        var gDayNo = 365 * gy2 + (gy2 + 3) / 4 - (gy2 + 99) / 100 + (gy2 + 399) / 400
        for (i in 0 until gm2) gDayNo += gDaysInMonth[i]
        if (gm2 > 1 && ((gy % 4 == 0 && gy % 100 != 0) || gy % 400 == 0)) gDayNo++
        gDayNo += gd2

        var jDayNo = gDayNo - 79
        val jNp = jDayNo / 12053
        jDayNo %= 12053

        var jy = 979 + 33 * jNp + 4 * (jDayNo / 1461)
        jDayNo %= 1461
        if (jDayNo >= 366) {
            jy += (jDayNo - 1) / 365
            jDayNo = (jDayNo - 1) % 365
        }

        var i = 0
        while (i < 11 && jDayNo >= jDaysInMonth[i]) {
            jDayNo -= jDaysInMonth[i]
            i++
        }
        return Triple(jy, i + 1, jDayNo + 1)
    }

    /** مثل «۱۴ شهریور ۱۴۰۵ ساعت ۱۸:۰۳» یا برای امروز فقط ساعت */
    fun format(millis: Long, withTime: Boolean = true): String {
        if (millis <= 0) return ""
        val c = Calendar.getInstance().apply { time = Date(millis) }
        val (jy, jm, jd) = toJalali(
            c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH)
        )
        val hh = c.get(Calendar.HOUR_OF_DAY).toString().padStart(2, '0')
        val mm = c.get(Calendar.MINUTE).toString().padStart(2, '0')
        val date = "$jd ${MONTHS[jm - 1]} $jy"
        return if (withTime) "$date، $hh:$mm" else date
    }

    fun short(millis: Long): String {
        if (millis <= 0) return ""
        val now = Calendar.getInstance()
        val c = Calendar.getInstance().apply { time = Date(millis) }
        val sameDay = now.get(Calendar.YEAR) == c.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == c.get(Calendar.DAY_OF_YEAR)
        return if (sameDay) {
            val hh = c.get(Calendar.HOUR_OF_DAY).toString().padStart(2, '0')
            val mm = c.get(Calendar.MINUTE).toString().padStart(2, '0')
            "$hh:$mm"
        } else {
            val (_, jm, jd) = toJalali(
                c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH)
            )
            "$jd ${MONTHS[jm - 1]}"
        }
    }
}
