package ir.payamban.app

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
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
        Telephony.Sms.getDefaultSmsPackage(this) == packageName

    private fun requestPermissions() {
        val all = REQUIRED.toMutableList()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            all += Manifest.permission.POST_NOTIFICATIONS
        }
        permissionLauncher.launch(all.toTypedArray())
    }

    private fun requestDefaultSmsApp() {
        if (isDefaultSmsApp()) return
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_SMS)) {
                rm.createRequestRoleIntent(RoleManager.ROLE_SMS)
            } else null
        } else {
            Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                .putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
        }
        if (intent != null) roleLauncher.launch(intent)
    }

    private companion object {
        val REQUIRED = listOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_CONTACTS
        )
    }
}
