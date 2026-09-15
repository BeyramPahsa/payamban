@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package ir.payamban.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.payamban.app.classify.Category
import ir.payamban.app.classify.Sensitivity
import ir.payamban.app.data.SavedCode
import ir.payamban.app.data.SenderGroup
import ir.payamban.app.sms.SmsSender
import ir.payamban.app.util.PersianDate

private enum class Tab(val label: String, val icon: ImageVector) {
    CHATS("گفتگوها", Icons.Filled.Chat),
    PROMO("تبلیغات", Icons.Filled.Campaign),
    CODES("کد تخفیف", Icons.Filled.LocalOffer),
    SETTINGS("تنظیمات", Icons.Filled.Settings)
}

@Composable
fun AppRoot(
    vm: AppViewModel,
    onRequestPermissions: () -> Unit,
    onRequestDefaultApp: () -> Unit
) {
    val state = vm.state

    if (!state.hasPermission || !state.isDefaultSmsApp) {
        SetupScreen(
            hasPermission = state.hasPermission,
            isDefault = state.isDefaultSmsApp,
            onRequestPermissions = onRequestPermissions,
            onRequestDefaultApp = onRequestDefaultApp
        )
        return
    }

    var tab by remember { mutableStateOf(Tab.CHATS) }
    var openThread by remember { mutableStateOf<SenderGroup?>(null) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.lastActionMessage) {
        state.lastActionMessage?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(tab.label, fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = { vm.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "به‌روزرسانی")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t; vm.clearSelection() },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label) }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (state.loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            when (tab) {
                Tab.CHATS -> ChatsTab(vm) { openThread = it }
                Tab.PROMO -> PromoTab(vm) { openThread = it }
                Tab.CODES -> CodesTab(vm)
                Tab.SETTINGS -> SettingsTab(vm)
            }
        }
    }

    openThread?.let { group ->
        ThreadDialog(
            group = group,
            canReply = group.messages.none { it.category == Category.PROMO },
            onDismiss = { openThread = null },
            onBlock = { vm.blockSender(group.canonicalSender); openThread = null },
            onMarkPromo = { vm.overrideCategory(group.canonicalSender, Category.PROMO); openThread = null },
            onMarkSafe = { vm.overrideCategory(group.canonicalSender, Category.SERVICE); openThread = null },
            onSent = { vm.refresh() }
        )
    }
}

/* ---------------------------------------------------------------- راه‌اندازی */

@Composable
private fun SetupScreen(
    hasPermission: Boolean,
    isDefault: Boolean,
    onRequestPermissions: () -> Unit,
    onRequestDefaultApp: () -> Unit
) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(32.dp))
            Text("پیام‌بان", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "پیامک‌های گوشی را مرتب می‌کند: گفتگوهای واقعی را نگه می‌دارد، " +
                    "کدهای تخفیف را جدا می‌کند و تبلیغات را برای بازبینی کنار می‌گذارد.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(8.dp))

            StepCard(
                index = 1,
                title = "دسترسی به پیامک و مخاطبین",
                body = "برای خواندن پیامک‌ها و تشخیص اینکه فرستنده در مخاطبین شماست.",
                done = hasPermission,
                actionLabel = "اجازه بده",
                onAction = onRequestPermissions
            )

            StepCard(
                index = 2,
                title = "انتخاب به‌عنوان اپ پیش‌فرض پیامک",
                body = "اندروید فقط به اپ پیش‌فرض اجازهٔ حذف پیامک و جلوگیری از پیام‌های " +
                    "بلاک‌شده را می‌دهد. بدون این مرحله، برنامه فقط می‌تواند نگاه کند.",
                done = isDefault,
                actionLabel = "انتخاب کن",
                onAction = onRequestDefaultApp
            )

            Spacer(Modifier.height(8.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "هیچ پیامکی از گوشی شما بیرون نمی‌رود. همهٔ تحلیل روی خود دستگاه انجام " +
                            "می‌شود و برنامه به اینترنت وصل نمی‌شود.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun StepCard(
    index: Int,
    title: String,
    body: String,
    done: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (done) {
                    Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                } else {
                    Box(
                        Modifier
                            .size(24.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) { Text("$index", style = MaterialTheme.typography.labelMedium) }
                }
                Spacer(Modifier.width(12.dp))
                Text(title, fontWeight = FontWeight.SemiBold)
            }
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!done) {
                Button(onClick = onAction, modifier = Modifier.fillMaxWidth()) { Text(actionLabel) }
            }
        }
    }
}

/* ---------------------------------------------------------------- گفتگوها */

@Composable
private fun ChatsTab(vm: AppViewModel, onOpen: (SenderGroup) -> Unit) {
    val state = vm.state
    var filter by remember { mutableStateOf<Category?>(null) }

    val groups = remember(state.chats, filter) {
        if (filter == null) state.chats
        else state.chats.filter { g -> g.messages.any { it.category == filter } }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .chipRow(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = filter == null,
                onClick = { filter = null },
                label = { Text("همه") }
            )
            listOf(Category.CONTACT, Category.PERSONAL, Category.BANK, Category.OTP, Category.SERVICE)
                .forEach { c ->
                    FilterChip(
                        selected = filter == c,
                        onClick = { filter = if (filter == c) null else c },
                        label = { Text(c.fa) }
                    )
                }
        }

        if (groups.isEmpty() && !state.loading) {
            EmptyState("هنوز گفتگویی برای نمایش نیست.")
            return@Column
        }

        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
            items(groups, key = { it.canonicalSender }) { g ->
                SenderRow(
                    group = g,
                    trailing = { Text(PersianDate.short(g.lastDate), style = MaterialTheme.typography.labelSmall) },
                    onClick = { onOpen(g) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
        }
    }
}

/* ---------------------------------------------------------------- تبلیغات */

@Composable
private fun PromoTab(vm: AppViewModel, onOpen: (SenderGroup) -> Unit) {
    val state = vm.state
    val selected = vm.selected
    var confirm by remember { mutableStateOf<Boolean?>(null) }   // true = حذف + بلاک

    Column(Modifier.fillMaxSize()) {
        Card(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "${state.promo.size} فرستنده، ${state.promoMessageCount} پیام تبلیغاتی",
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    "هیچ‌چیز بدون تأیید شما حذف نمی‌شود. اول فرستنده‌ها را انتخاب کنید.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().chipRow(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AssistChip(onClick = { vm.selectAllPromo() }, label = { Text("انتخاب همه") })
            AssistChip(onClick = { vm.clearSelection() }, label = { Text("لغو انتخاب") })
        }

        if (state.promo.isEmpty() && !state.loading) {
            EmptyState("پیامک تبلیغاتی‌ای پیدا نشد. 🎉")
            return@Column
        }

        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 8.dp)) {
            items(state.promo, key = { it.canonicalSender }) { g ->
                SenderRow(
                    group = g,
                    leading = {
                        Checkbox(
                            checked = selected.contains(g.canonicalSender),
                            onCheckedChange = { vm.toggleSelect(g.canonicalSender) }
                        )
                    },
                    subtitle = g.messages.firstOrNull()?.verdict?.reasons?.firstOrNull(),
                    trailing = { Text("${g.count} پیام", style = MaterialTheme.typography.labelSmall) },
                    onClick = { onOpen(g) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
        }

        if (selected.isNotEmpty()) {
            Surface(tonalElevation = 3.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(onClick = { confirm = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Block, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("حذف و بلاک")
                    }
                    OutlinedButton(onClick = { confirm = false }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Delete, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("فقط حذف")
                    }
                }
            }
        }
    }

    confirm?.let { alsoBlock ->
        val count = state.promo.filter { it.canonicalSender in selected }.sumOf { it.count }
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(if (alsoBlock) "حذف و بلاک" else "حذف پیام‌ها") },
            text = {
                Text(
                    buildString {
                        append("$count پیام از ${selected.size} فرستنده حذف می‌شود.")
                        if (alsoBlock) append(" از این به بعد پیام‌های این فرستنده‌ها اصلاً روی گوشی ذخیره نمی‌شوند.")
                        append("\n\nکدهای تخفیفِ داخل این پیام‌ها قبل از حذف در تب «کد تخفیف» ذخیره می‌شوند.")
                        append("\nاین کار برگشت‌پذیر نیست.")
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.deleteSelected(alsoBlock); confirm = null }) {
                    Text("تأیید و حذف", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("انصراف") } }
        )
    }
}

/* ---------------------------------------------------------------- کد تخفیف */

@Composable
private fun CodesTab(vm: AppViewModel) {
    val codes = vm.state.codes
    val clipboard = LocalClipboardManager.current

    if (codes.isEmpty() && !vm.state.loading) {
        EmptyState("هنوز کد تخفیفی پیدا نشده است.")
        return
    }

    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(codes, key = { it.sourceSender + "|" + it.code.code }) { item ->
            CodeCard(
                item = item,
                onCopy = { clipboard.setText(AnnotatedString(item.code.code)) },
                onRemove = { vm.removeCode(item.code.code, item.sourceSender) }
            )
        }
    }
}

@Composable
private fun CodeCard(item: SavedCode, onCopy: () -> Unit, onRemove: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.code.code,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.weight(1f))
                item.code.percent?.let {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            "$it٪",
                            Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
            Text(item.sourceName, style = MaterialTheme.typography.bodyMedium)
            Text(
                item.body.take(140),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.code.expiry?.let { "مهلت: $it" } ?: PersianDate.format(item.receivedAt, false),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.Close, "حذف از فهرست", Modifier.size(18.dp))
                }
                TextButton(onClick = onCopy) {
                    Icon(Icons.Filled.ContentCopy, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("کپی")
                }
            }
        }
    }
}

/* ---------------------------------------------------------------- تنظیمات */

@Composable
private fun SettingsTab(vm: AppViewModel) {
    val state = vm.state
    var dropBlocked by remember { mutableStateOf(vm.store.dropBlocked) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("وضعیت", fontWeight = FontWeight.SemiBold)
                Text("پیام‌های بررسی‌شده: ${state.all.size}", style = MaterialTheme.typography.bodySmall)
                Text("گفتگوها: ${state.chatMessageCount} پیام از ${state.chats.size} فرستنده", style = MaterialTheme.typography.bodySmall)
                Text("تبلیغات: ${state.promoMessageCount} پیام از ${state.promo.size} فرستنده", style = MaterialTheme.typography.bodySmall)
                Text("کدهای تخفیف: ${state.codes.size}", style = MaterialTheme.typography.bodySmall)
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("حساسیت تشخیص تبلیغات", fontWeight = FontWeight.SemiBold)
                Text(
                    "هرچه سخت‌گیرانه‌تر، پیام‌های بیشتری تبلیغاتی شمرده می‌شوند — و احتمال " +
                        "اشتباه هم بیشتر می‌شود.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Sensitivity.entries.forEach { s ->
                        FilterChip(
                            selected = vm.sensitivity == s,
                            onClick = { vm.setSensitivity(s) },
                            label = { Text(s.fa) }
                        )
                    }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SwitchRow(
                    title = "پیام فرستندهٔ بلاک‌شده ذخیره نشود",
                    body = "پیام‌های تازه از فرستنده‌های بلاک‌شده اصلاً روی گوشی نوشته نمی‌شوند.",
                    checked = dropBlocked,
                    onChange = { dropBlocked = it; vm.setDropBlocked(it) }
                )
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("فرستنده‌های بلاک‌شده (${state.blocked.size})", fontWeight = FontWeight.SemiBold)
                if (state.blocked.isEmpty()) {
                    Text(
                        "هنوز کسی بلاک نشده است.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    state.blocked.sorted().forEach { s ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(s, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = { vm.unblockSender(s) }) { Text("رفع بلاک") }
                        }
                    }
                }
            }
        }

        Text(
            "پیام‌بان روی خود گوشی کار می‌کند و هیچ داده‌ای را جایی نمی‌فرستد.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SwitchRow(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/* ---------------------------------------------------------------- جزئیات گفتگو */

@Composable
private fun ThreadDialog(
    group: SenderGroup,
    canReply: Boolean,
    onDismiss: () -> Unit,
    onBlock: () -> Unit,
    onMarkPromo: () -> Unit,
    onMarkSafe: () -> Unit,
    onSent: () -> Unit
) {
    val context = LocalContext.current
    var reply by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(group.displayName, fontWeight = FontWeight.SemiBold) },
        text = {
          Column {
            LazyColumn(
                Modifier.height(300.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(group.messages.take(60)) { m ->
                    Column {
                        Text(
                            PersianDate.format(m.message.date),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(m.message.body, style = MaterialTheme.typography.bodySmall)
                        m.verdict.reasons.firstOrNull()?.let {
                            Text(
                                "• $it",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        HorizontalDivider(Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
            }
            if (canReply) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = reply,
                        onValueChange = { reply = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("پاسخ...") },
                        maxLines = 3
                    )
                    IconButton(
                        onClick = {
                            val to = group.messages.firstOrNull()?.message?.sender
                            if (to != null && reply.isNotBlank() &&
                                SmsSender.send(context, to, reply)
                            ) {
                                reply = ""
                                onSent()
                            }
                        }
                    ) { Icon(Icons.Filled.Send, contentDescription = "ارسال") }
                }
            }
          }
        },
        confirmButton = {
            TextButton(onClick = onBlock) { Text("بلاک فرستنده", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onMarkSafe) { Text("تبلیغ نیست") }
                TextButton(onClick = onMarkPromo) { Text("تبلیغ است") }
                TextButton(onClick = onDismiss) { Text("بستن") }
            }
        }
    )
}

/* ---------------------------------------------------------------- اجزای مشترک */

@Composable
private fun SenderRow(
    group: SenderGroup,
    leading: (@Composable () -> Unit)? = null,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leading?.invoke()
        if (leading != null) Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            Text(
                group.displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                group.preview,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        trailing?.invoke()
    }
}

@Composable
private fun EmptyState(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun Modifier.chipRow(): Modifier =
    this
        .horizontalScroll(rememberScrollState())
        .padding(horizontal = 12.dp, vertical = 8.dp)
