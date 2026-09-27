@file:OptIn(ExperimentalMaterial3Api::class)

package com.ftrgwh.automessages.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ftrgwh.automessages.data.Task
import com.ftrgwh.automessages.util.Perms

@Composable
fun TaskListScreen(
    tasks: List<Task>,
    onAdd: () -> Unit,
    onEdit: (Task) -> Unit,
    onToggle: (Task, Boolean) -> Unit,
) {
    val context = LocalContext.current
    var showGuide by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("定时消息") },
                actions = {
                    IconButton(onClick = { showGuide = true }) {
                        Icon(Icons.Filled.Info, contentDescription = "使用指南")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = "新建任务")
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            PermissionPanel()
            if (tasks.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        "还没有定时任务\n点击右下角 + 新建一个",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                    items(tasks, key = { it.id }) { task ->
                        TaskCard(task, onEdit, onToggle)
                    }
                }
            }
        }
    }

    if (showGuide) {
        AlertDialog(
            onDismissRequest = { showGuide = false },
            title = { Text("使用指南") },
            text = {
                Text(
                    "聊天定位仅支持QQ号(纯数字),发送方式自动选择:\n\n" +
                        "① 后台直发(推荐):通过QQ通知栏的快捷回复直接发送,全程后台、不打开QQ。前提:通知栏中存在该联系人的未读消息,且能从通知中识别出该QQ号(对方昵称/备注/群名片含QQ号时最可靠)。\n\n" +
                        "② QQ号直达聊天:自动通过 mqq 协议打开与该QQ号的聊天窗口,填入消息并点击发送(会短暂把QQ切到前台,需手机解锁)。\n\n" +
                        "准备步骤:\n\n" +
                        "1. 安装并登录手机QQ;\n\n" +
                        "2. 按权限卡片开启:通知使用权、无障碍服务、悬浮窗权限、精确闹钟;\n\n" +
                        "3. 新建任务:填写对方QQ号、消息内容、时间与重复方式。\n\n" +
                        "注意事项:\n" +
                        "• 勾选「仅后台直发」的任务绝不打开QQ,但无法从通知识别QQ号时会发送失败;\n" +
                        "• 走②路径时手机需处于解锁状态,锁屏无法自动操作QQ;\n" +
                        "• 单次任务发送后自动停用,每天/每周任务自动排下一次;\n" +
                        "• 请合理使用,避免对他人造成骚扰。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                FilledTonalButton(onClick = { showGuide = false }) { Text("知道了") }
            },
        )
    }
}

@Composable
private fun PermissionPanel() {
    val context = LocalContext.current
    val a11y = Perms.accessibilityEnabled(context)
    val overlay = Perms.overlayGranted(context)
    val exact = Perms.exactAlarmGranted(context)
    val notif = Perms.notifGranted(context)

    val rows = buildList {
        add(PermRow("通知使用权", "后台直发消息,无需打开QQ", Perms.notifListenerEnabled(context)) { Perms.openNotifListenerSettings(context) })
        add(PermRow("无障碍服务", "无未读通知时回退:自动操作QQ发送", a11y) { Perms.openAccessibilitySettings(context) })
        add(PermRow("悬浮窗权限", "允许在后台把QQ切到前台", overlay) { Perms.openOverlaySettings(context) })
        if (!exact) add(PermRow("精确闹钟", "保证定时准点触发", exact) { Perms.openExactAlarmSettings(context) })
        if (!notif) add(PermRow("通知权限", "接收发送结果通知", notif) { Perms.openNotifSettings(context) })
    }

    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            if (rows.all { it.granted }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.CheckCircle, null,
                        tint = Color(0xFF2E7D32), modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.size(6.dp))
                    Text("权限已就绪", style = MaterialTheme.typography.labelMedium)
                }
            } else {
                rows.filter { !it.granted }.forEachIndexed { index, row ->
                    if (index > 0) Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(row.label, style = MaterialTheme.typography.labelLarge)
                            Text(
                                row.desc,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        FilledTonalButton(onClick = row.action) { Text("去开启") }
                    }
                }
            }
        }
    }
}

private class PermRow(
    val label: String,
    val desc: String,
    val granted: Boolean,
    val action: () -> Unit,
)

@Composable
private fun TaskCard(task: Task, onEdit: (Task) -> Unit, onToggle: (Task, Boolean) -> Unit) {
    ElevatedCard(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clickable { onEdit(task) },
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "→ ${task.target}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        task.scheduleText(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        task.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Switch(checked = task.enabled, onCheckedChange = { onToggle(task, it) })
            }
            task.lastResult?.let { result ->
                Spacer(Modifier.height(4.dp))
                val ok = result.startsWith("成功")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (ok) Icons.Filled.CheckCircle else Icons.Filled.Close,
                        contentDescription = null,
                        tint = if (ok) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(
                        result,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (ok) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
