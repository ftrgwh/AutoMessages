package com.ftrgwh.automessages

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.ftrgwh.automessages.alarm.Scheduler
import com.ftrgwh.automessages.auto.SendDispatcher
import com.ftrgwh.automessages.data.Task
import com.ftrgwh.automessages.data.TaskStore
import com.ftrgwh.automessages.ui.EditTaskScreen
import com.ftrgwh.automessages.ui.TaskListScreen
import com.ftrgwh.automessages.ui.theme.AutoMessagesTheme
import com.ftrgwh.automessages.util.Perms

class MainActivity : ComponentActivity() {

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* 拒绝也不影响核心功能,仅收不到结果通知 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            AutoMessagesTheme {
                AppRoot()
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun AppRoot() {
    val context = LocalContext.current
    var tasks by remember { mutableStateOf(TaskStore.load(context)) }
    var editing by remember { mutableStateOf<Task?>(null) } // null=列表页;非null=编辑/新建

    // 回到前台时:重排闹钟兜底 + 刷新列表(展示后台写入的发送结果)
    val lifecycleOwner = context as? LifecycleOwner
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                Scheduler.syncAll(context)
                tasks = TaskStore.load(context)
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }

    if (editing == null) {
        TaskListScreen(
            tasks = tasks,
            onAdd = { editing = Task() },
            onEdit = { editing = it },
            onToggle = { task, enabled ->
                TaskStore.setEnabled(context, task.id, enabled)
                Scheduler.syncAll(context)
                tasks = TaskStore.load(context)
            },
        )
    } else {
        val initial = editing ?: return
        EditTaskScreen(
            initial = if (initial.id == 0L) null else initial,
            onClose = {
                editing = null
                tasks = TaskStore.load(context)
            },
            onDelete = { task ->
                TaskStore.remove(context, task.id)
                Scheduler.cancel(context, task.id)
                editing = null
                tasks = TaskStore.load(context)
            },
            onTest = { task ->
                if (!Perms.notifListenerEnabled(context) && !Perms.accessibilityEnabled(context)) {
                    Toast.makeText(context, "请先开启「通知使用权」或「无障碍服务」", Toast.LENGTH_SHORT).show()
                } else {
                    SendDispatcher.dispatch(context, task)
                    Toast.makeText(context, "已触发,请在通知栏或QQ中查看结果", Toast.LENGTH_SHORT).show()
                }
            },
            onSave = { task ->
                TaskStore.upsert(context, task)
                Scheduler.syncAll(context)
                editing = null
                tasks = TaskStore.load(context)
            },
        )
    }
}
