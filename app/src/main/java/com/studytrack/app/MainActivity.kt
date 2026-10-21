
package com.studytrack.app

import android.content.Context
import android.content.Intent
import android.os.Bundle

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions

import androidx.compose.material3.*

import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

import androidx.lifecycle.viewmodel.compose.viewModel

import com.studytrack.app.ui.theme.StudyTrackTheme

import org.json.JSONArray
import org.json.JSONObject

// ============================================
// STUDYTRACK - STUDENT TASK MANAGER
// ============================================

// Task data model
data class StudentTask(
    val title: String,
    val completed: Boolean = false,
    val deadline: String = ""
)

// ============================================
// MAIN ACTIVITY
// ============================================

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            StudyTrackTheme {
                StudyTrackApp()
            }
        }
    }
}

// ============================================
// AUTHENTICATION SCREENS
// ============================================

@Composable
fun StudyTrackApp(
    authViewModel: AuthViewModel = viewModel()
) {
    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->

        if (authViewModel.loggedIn) {

            StudyTrackHome(
                modifier = Modifier.padding(innerPadding),
                email = authViewModel.currentEmail,
                onLogout = {
                    authViewModel.logout()
                }
            )

        } else {

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                verticalArrangement =
                    Arrangement.spacedBy(12.dp)
            ) {

                Text(
                    text = "StudyTrack",
                    style = MaterialTheme.typography.headlineLarge
                )

                Text("Manage student tasks and deadlines")

                Text(
                    text = if (authViewModel.registerMode) {
                        "Create Account"
                    } else {
                        "Log In"
                    },
                    style = MaterialTheme.typography.titleLarge
                )

                // Email input
                OutlinedTextField(
                    value = authViewModel.email,
                    onValueChange = {
                        authViewModel.email = it
                    },
                    label = {
                        Text("Email")
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Password input
                OutlinedTextField(
                    value = authViewModel.password,
                    onValueChange = {
                        authViewModel.password = it
                    },
                    label = {
                        Text("Password")
                    },
                    visualTransformation =
                        PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Registration confirmation
                if (authViewModel.registerMode) {

                    OutlinedTextField(
                        value = authViewModel.confirmPassword,
                        onValueChange = {
                            authViewModel.confirmPassword = it
                        },
                        label = {
                            Text("Confirm Password")
                        },
                        visualTransformation =
                            PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Authentication error
                if (authViewModel.error.isNotBlank()) {

                    Text(
                        text = authViewModel.error,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                // Loading indicator
                if (authViewModel.loading) {
                    CircularProgressIndicator()
                }

                // Login / Register button
                Button(
                    enabled = !authViewModel.loading,
                    onClick = {
                        authViewModel.submit()
                    }
                ) {
                    Text(
                        if (authViewModel.registerMode) {
                            "Create Account"
                        } else {
                            "Log In"
                        }
                    )
                }

                // Switch between login and registration
                TextButton(
                    onClick = {
                        authViewModel.switchMode()
                    }
                ) {
                    Text(
                        if (authViewModel.registerMode) {
                            "Already have an account? Log In"
                        } else {
                            "No account? Register"
                        }
                    )
                }
            }
        }
    }
}

// ============================================
// STUDENT DASHBOARD
// ============================================

@Composable
fun StudyTrackHome(
    modifier: Modifier = Modifier,
    email: String,
    onLogout: () -> Unit
) {

    val context = LocalContext.current

    // Load tasks from local storage
    val tasks = remember {
        mutableStateListOf<StudentTask>().apply {
            addAll(loadTasks(context))
        }
    }

    var taskName by remember {
        mutableStateOf("")
    }

    var taskDeadline by remember {
        mutableStateOf("")
    }

    var showDialog by remember {
        mutableStateOf(false)
    }

    // Save task changes automatically
    LaunchedEffect(tasks.toList()) {
        saveTasks(context, tasks)
    }

    val completedCount = tasks.count {
        it.completed
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement =
            Arrangement.spacedBy(10.dp)
    ) {

        // App title
        Text(
            text = "StudyTrack",
            style = MaterialTheme.typography.headlineLarge
        )

        Text("Student task manager")

        Text(
            text = "Logged in as $email",
            style = MaterialTheme.typography.bodySmall
        )

        // Logout button
        OutlinedButton(
            onClick = onLogout
        ) {
            Text("Log Out")
        }

        // Task section
        Text(
            text = "Your Tasks",
            style = MaterialTheme.typography.titleLarge
        )

        Text(
            "$completedCount of ${tasks.size} tasks completed"
        )

        Button(
            onClick = {
                showDialog = true
            }
        ) {
            Text("Add Task")
        }

        // Task list
        if (tasks.isEmpty()) {

            Text("No tasks available yet.")

        } else {

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {

                itemsIndexed(tasks) { index, task ->

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {

                        // Completion checkbox
                        Checkbox(
                            checked = task.completed,
                            onCheckedChange = { checked ->

                                tasks[index] = task.copy(
                                    completed = checked
                                )
                            }
                        )

                        Column(
                            modifier = Modifier.weight(1f)
                        ) {

                            // Task title
                            Text(
                                text = task.title,
                                style =
                                    MaterialTheme.typography.bodyLarge
                            )

                            // Deadline
                            if (task.deadline.isNotBlank()) {

                                Text(
                                    text = "Deadline: ${task.deadline}",
                                    style =
                                        MaterialTheme.typography.bodySmall,
                                    color =
                                        MaterialTheme.colorScheme.secondary
                                )
                            }

                            // Android Intent - Share Task
                            TextButton(
                                onClick = {

                                    val shareText = buildString {

                                        append(
                                            "StudyTrack Assignment\n"
                                        )

                                        append(
                                            "Task: ${task.title}\n"
                                        )

                                        if (task.deadline.isNotBlank()) {
                                            append(
                                                "Deadline: ${task.deadline}\n"
                                            )
                                        }

                                        append(
                                            "Status: " +
                                                    if (task.completed) {
                                                        "Completed"
                                                    } else {
                                                        "Pending"
                                                    }
                                        )
                                    }

                                    val shareIntent =
                                        Intent(Intent.ACTION_SEND).apply {

                                            type = "text/plain"

                                            putExtra(
                                                Intent.EXTRA_TEXT,
                                                shareText
                                            )
                                        }

                                    context.startActivity(
                                        Intent.createChooser(
                                            shareIntent,
                                            "Share assignment using"
                                        )
                                    )
                                }
                            ) {
                                Text("Share Task")
                            }
                        }
                    }
                }
            }
        }
    }

    // ========================================
    // ADD TASK DIALOG
    // ========================================

    if (showDialog) {

        AlertDialog(
            onDismissRequest = {
                showDialog = false
            },

            title = {
                Text("Add New Task")
            },

            text = {

                Column(
                    verticalArrangement =
                        Arrangement.spacedBy(12.dp)
                ) {

                    OutlinedTextField(
                        value = taskName,
                        onValueChange = {
                            taskName = it
                        },
                        label = {
                            Text("Task Name")
                        },
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = taskDeadline,
                        onValueChange = {
                            taskDeadline = it
                        },
                        label = {
                            Text("Deadline (YYYY-MM-DD)")
                        },
                        placeholder = {
                            Text("2026-10-23")
                        },
                        singleLine = true
                    )
                }
            },

            confirmButton = {

                TextButton(
                    onClick = {

                        if (taskName.isNotBlank()) {

                            tasks.add(
                                StudentTask(
                                    title = taskName.trim(),
                                    deadline = taskDeadline.trim()
                                )
                            )

                            taskName = ""
                            taskDeadline = ""
                            showDialog = false
                        }
                    }
                ) {
                    Text("Save")
                }
            },

            dismissButton = {

                TextButton(
                    onClick = {
                        showDialog = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

// ============================================
// LOCAL TASK STORAGE
// ============================================

// Save tasks to SharedPreferences
fun saveTasks(
    context: Context,
    tasks: List<StudentTask>
) {

    val jsonArray = JSONArray()

    tasks.forEach { task ->

        val jsonObject = JSONObject()

        jsonObject.put("title", task.title)
        jsonObject.put("completed", task.completed)
        jsonObject.put("deadline", task.deadline)

        jsonArray.put(jsonObject)
    }

    context.getSharedPreferences(
        "StudyTrackPrefs",
        Context.MODE_PRIVATE
    ).edit()
        .putString(
            "tasks",
            jsonArray.toString()
        )
        .apply()
}

// Load saved tasks
fun loadTasks(context: Context): List<StudentTask> {

    val savedData = context.getSharedPreferences(
        "StudyTrackPrefs",
        Context.MODE_PRIVATE
    ).getString(
        "tasks",
        "[]"
    ) ?: "[]"

    return try {

        val jsonArray = JSONArray(savedData)

        List(jsonArray.length()) { index ->

            val task = jsonArray.getJSONObject(index)

            StudentTask(
                title = task.getString("title"),
                completed = task.getBoolean("completed"),
                deadline = task.optString("deadline", "")
            )
        }

    } catch (e: Exception) {
        emptyList()
    }
}
