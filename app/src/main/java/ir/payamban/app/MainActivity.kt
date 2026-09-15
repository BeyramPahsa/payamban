package ir.payamban.app

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.provider.Telephony
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import ir.payamban.app.sms.Notifier
import ir.payamban.app.ui.AppRoot
import ir.payamban.app.ui.AppViewModel
import ir.payamban.app.ui.PayamBanTheme

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            syncStatus()
        }

    private val roleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            syncStatus()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifier.ensureChannel(this)
        setContent {
            PayamBanTheme {
                AppRoot(
                    vm = vm,
                    onRequestPermissions = { requestPermissions() },
                    onRequestDefaultApp = { requestDefaultSmsApp() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        syncStatus()
    }

    private fun syncStatus() {
        vm.setPermission(granted = hasCorePermissions(), isDefault = isDefaultSmsApp())
    }

    private fun hasCorePermissions(): Boolean = REQUIRED.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun isDefaultSmsApp(): Boolean =
        runCatching { Telephony.Sms.getDefaultSmsPackage(this) == packageName }.getOrDefault(false)

    private fun requestPermissions() {
        val all = REQUIRED.toMutableList()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            all += Manifest.permission.POST_NOTIFICATIONS
        }
        runCatching { permissionLauncher.launch(all.toTypedArray()) }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    /**
     * درخواست «اپ پیش‌فرض پیامک».
     * هر سازندهٔ گوشی این را جای دیگری گذاشته، پس چند مسیر پشت سر هم امتحان می‌شود
     * و اگر هیچ‌کدام باز نشد، کاربر بدون بازخورد نمی‌ماند.
     */
    private fun requestDefaultSmsApp() {
        if (isDefaultSmsApp()) {
            syncStatus()
            toast("پیام‌بان همین حالا اپ پیش‌فرض پیامک است.")
            return
        }

        // ۱) مسیر رسمی اندروید ۱۰ به بالا
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleIntent = runCatching {
                val rm = getSystemService(RoleManager::class.java)
                if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_SMS)) {
                    rm.createRequestRoleIntent(RoleManager.ROLE_SMS)
                } else null
            }.getOrNull()
            if (roleIntent != null && tryLaunch(roleIntent)) return
        }

        // ۲) مسیر قدیمی‌تر اندروید
        val legacy = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
            .putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
        if (tryLaunch(legacy)) return

        // ۳) صفحهٔ «اپ‌های پیش‌فرض» در تنظیمات
        if (tryLaunch(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))) {
            toast("در این صفحه «اپ پیامک» را باز کنید و پیام‌بان را انتخاب کنید.")
            return
        }

        // ۴) صفحهٔ تنظیمات خودِ اپ
        val appSettings = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null)
        )
        if (tryLaunch(appSettings)) {
            toast("از بخش «تنظیم به‌عنوان پیش‌فرض» پیام‌بان را اپ پیامک کنید.")
            return
        }

        // ۵) هیچ‌کدام باز نشد
        toast(
            "گوشی شما این صفحه را باز نکرد. دستی انجام دهید:\n" +
                "تنظیمات ← برنامه‌ها ← برنامه‌های پیش‌فرض ← برنامه پیامک ← پیام‌بان"
        )
    }

    private fun tryLaunch(intent: Intent): Boolean = runCatching {
        roleLauncher.launch(intent)
        true
    }.getOrDefault(false)

    private companion object {
        val REQUIRED = listOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_CONTACTS
        )
    }
}
