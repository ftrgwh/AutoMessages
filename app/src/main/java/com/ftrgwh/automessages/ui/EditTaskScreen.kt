@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.ftrgwh.automessages.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ftrgwh.automessages.data.Repeat
import com.ftrgwh.automessages.data.Task
import com.ftrgwh.automessages.data.TaskStore
import com.ftrgwh.automessages.data.WEEKDAY_NAMES
import java.util.Calendar

@Composable
fun EditTaskScreen(
    initial: Task?,
    onClose: () -> Unit,
    onDelete: (Task) -> Unit,
    onTest: (Task) -> Unit,
    onSave: (Task) -> Unit,
) {
    val context = LocalContext.current
    var target by remember { mutableStateOf(initial?.target ?: "") }
    var message by remember { mutableStateOf(initial?.message ?: "") }
    var repeat by remember { mutableStateOf(initial?.repeat ?: Repeat.ONCE) }
    var weekday by remember { mutableStateOf(initial?.weekday ?: 1) }
    val defaultCal = remember { Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 1) } }
    var hour by remember { mutableStateOf(initial?.hour ?: defaultCal.get(Calendar.HOUR_OF_DAY)) }
    var minute by remember { mutableStateOf(initial?.minute ?: defaultCal.get(Calendar.MINUTE)) }
    var dateCal by remember {
        mutableStateOf(
            Calendar.getInstance().apply {
                if (initial != null && initial.onceAt > 0) timeInMillis = initial.onceAt
            },
        )
    }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var backgroundOnly by remember { mutableStateOf(initial?.backgroundOnly ?: false) }

    LaunchedEffect(showDate) {
        if (showDate) {
            DatePickerDialog(
                context,
                { _, y, m, d ->
                    dateCal = Calendar.getInstance().apply { set(y, m, d) }
                    showDate = false
                },
                dateCal.get(Calendar.YEAR),
                dateCal.get(Calendar.MONTH),
                dateCal.get(Calendar.DAY_OF_MONTH),
            ).apply { setOnDismissListener { showDate = false } }.show()
        }
    }
    LaunchedEffect(showTime) {
        if (showTime) {
            TimePickerDialog(
                context,
                { _, h, m ->
                    hour = h
                    minute = m
                    showTime = false
                },
                hour, minute, true,
            ).apply { setOnDismissListener { showTime = false } }.show()
        }
    }

    fun buildTask(): Task? {
        val t = target.trim()
        val msg = message.trim()
        if (t.isEmpty() || !t.all { it.isDigit() } || t.length !in 5..12) {
            error = "请填写正确的QQ号(5-12位纯数字)"
            return null
        }
        if (msg.isEmpty()) { error = "请填写消息内容"; return null }
        val onceAt: Long = if (repeat == Repeat.ONCE) {
            Calendar.getInstance().apply {
                timeInMillis = dateCal.timeInMillis
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        } else 0
        if (repeat == Repeat.ONCE && onceAt <= System.currentTimeMillis()) {
            error = "发送时间必须晚于当前时间"; return null
        }
        error = null
        return Task(
            id = initial?.id ?: TaskStore.nextId(context),
            target = t,
            message = msg,
            hour = hour,
            minute = minute,
            repeat = repeat,
            weekday = weekday,
            onceAt = onceAt,
            backgroundOnly = backgroundOnly,
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (initial == null) "新建任务" else "编辑任务") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (initial != null) {
                        IconButton(onClick = { showDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "删除")
                        }
                    }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = target,
                onValueChange = { target = it.filter { c -> c.isDigit() } },
                label = { Text("对方QQ号") },
                supportingText = { Text("仅支持QQ号定位聊天:通过QQ协议直达聊天窗口,后台直发也按QQ号识别通知") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                label = { Text("消息内容") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            Text("重复方式", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Repeat.entries.forEach { r ->
                    FilterChip(selected = repeat == r, onClick = { repeat = r }, label = { Text(r.label) })
                }
            }
            if (repeat == Repeat.WEEKLY) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WEEKDAY_NAMES.forEachIndexed { i, name ->
                        FilterChip(
                            selected = weekday == i + 1,
                            onClick = { weekday = i + 1 },
                            label = { Text(name) },
                        )
                    }
                }
            }

            if (repeat == Repeat.ONCE) {
                OutlinedButton(onClick = { showDate = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("发送日期:%d月%d日".format(dateCal.get(Calendar.MONTH) + 1, dateCal.get(Calendar.DAY_OF_MONTH)))
                }
            }
            OutlinedButton(onClick = { showTime = true }, modifier = Modifier.fillMaxWidth()) {
                Text("发送时间:%02d:%02d".format(hour, minute))
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("仅后台直发,不打开QQ", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "通过通知栏快捷回复发送;需要通知栏中存在该QQ号的未读消息(能从通知中识别出该QQ号)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = backgroundOnly,
                    onCheckedChange = { backgroundOnly = it },
                )
            }

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { buildTask()?.let(onSave) }, modifier = Modifier.weight(1f)) {
                    Text("保存")
                }
                OutlinedButton(onClick = { buildTask()?.let(onTest) }, modifier = Modifier.weight(1f)) {
                    Text("立即发送测试")
                }
            }
        }
    }

    if (showDelete && initial != null) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("删除任务") },
            text = { Text("确定删除该定时任务吗?") },
            confirmButton = {
                TextButton(onClick = { showDelete = false; onDelete(initial) }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("取消") }
            },
        )
    }
}
