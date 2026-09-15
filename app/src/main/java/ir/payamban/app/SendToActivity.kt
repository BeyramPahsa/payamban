package ir.payamban.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

/**
 * فقط برای اینکه اندروید اپ را واجد شرایط «اپ پیش‌فرض پیامک» بداند.
 * هر درخواست ارسال پیامک از بیرون، به صفحهٔ اصلی هدایت می‌شود.
 */
class SendToActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
        finish()
    }
}
