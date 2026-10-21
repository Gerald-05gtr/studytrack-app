
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
import com.google.firebase.auth.FirebaseAuth
import com.studytrack.app.ui.theme.StudyTrackTheme
import org.json.JSONArray
import org.json.JSONObject

data class StudentTask(
    val title: String,
    val completed: Boolean = false,
    val deadline: String = ""
)

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

@Composable
fun StudyTrackApp() {
    val auth = remember { FirebaseAuth.getInstance() }
    var loggedIn by remember {
        mutableStateOf(auth.currentUser != null)
    }
    var registerMode by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener {
            loggedIn = it.currentUser != null
        }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        if (loggedIn) {
            StudyTrackHome(
                modifier = Modifier.padding(padding),
                email = auth.currentUser?.email.orEmpty(),
                onLogout = {
                    auth.signOut()
                    loggedIn = false
                    password = ""
                    confirmPassword = ""
                    error = ""
                }
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "StudyTrack",
                    style = MaterialTheme.typography.headlineLarge
                )
                Text("Manage student tasks and deadlines")

                Text(
                    if (registerMode) "Create Account" else "Log In",
                    style = MaterialTheme.typography.titleLarge
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    visualTransformation =
                        PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (registerMode) {
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("Confirm Password") },
                        visualTransformation =
                            PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (error.isNotBlank()) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                if (loading) {
                    CircularProgressIndicator()
                }

                Button(
                    enabled = !loading,
                    onClick = {
                        val cleanEmail = email.trim()

                        error = when {
                            !cleanEmail.matches(
                                Regex(
                                    "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
                                )
                            ) -> "Enter a valid email address"

                            password.length < 6 ->
                                "Password must have at least 6 characters"

                            registerMode &&
                                    password != confirmPassword ->
                                "Passwords do not match"

                            else -> ""
                        }

                        if (error.isEmpty()) {
                            loading = true

                            val result = if (registerMode) {
                                auth.createUserWithEmailAndPassword(
                                    cleanEmail,
                                    password
                                )
                            } else {
                                auth.signInWithEmailAndPassword(
                                    cleanEmail,
                                    password
                                )
                            }

                            result.addOnCompleteListener { task ->
                                loading = false

                                if (task.isSuccessful) {
                                    loggedIn = true
                                    error = ""
                                } else {
                                    error = if (registerMode) {
                                        "Registration failed. Check your email " +
                                                "or try another account."
                                    } else {
                                        "Login failed. Check your email " +
                                                "and password."
                                    }
                                }
                            }
                        }
                    }
                ) {
                    Text(
                        if (registerMode) "Create Account" else "Log In"
                    )
                }

                TextButton(
                    onClick = {
                        registerMode = !registerMode
                        error = ""
                        password = ""
                        confirmPassword = ""
                    }
                ) {
                    Text(
                        if (registerMode)
                            "Already have an account? Log In"
                        else
                            "No account? Register"
                    )
                }
            }
        }
    }
}

@Composable
fun StudyTrackHome(
    modifier: Modifier = Modifier,
    email: String,
    onLogout: () -> Unit
) {
    val context = LocalContext.current

    val tasks = remember {
        mutableStateListOf<StudentTask>().apply {
            addAll(loadTasks(context))
        }
    }

    var taskName by remember { mutableStateOf("") }
    var taskDeadline by remember { mutableStateOf("") }
    var showDialog by remember { mutableStateOf(false) }

    LaunchedEffect(tasks.toList()) {
        saveTasks(context, tasks)
    }

    val completedCount = tasks.count { it.completed }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "StudyTrack",
            style = MaterialTheme.typography.headlineLarge
        )

        Text("Student task manager")
        Text("Logged in as $email")

        OutlinedButton(onClick = onLogout) {
            Text("Log Out")
        }

        Text(
            "Your Tasks",
            style = MaterialTheme.typography.titleLarge
        )

        Text("$completedCount of ${tasks.size} tasks completed")

        Button(onClick = { showDialog = true }) {
            Text("Add Task")
        }

        if (tasks.isEmpty()) {
            Text("No tasks available yet.")
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(tasks) { index, task ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Checkbox(
                            checked = task.completed,
                            onCheckedChange = { checked ->
                                tasks[index] = task.copy(
                                    completed = checked
                                )
                            }
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                task.title,
                                style = MaterialTheme.typography.bodyLarge
                            )

                            if (task.deadline.isNotBlank()) {
                                Text(
                                    "Deadline: ${task.deadline}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            TextButton(
                                onClick = {
                                    val shareText = buildString {
                                        append("StudyTrack Assignment\n")
                                        append("Task: ${task.title}\n")
                                        if (task.deadline.isNotBlank()) {
                                            append(
                                                "Deadline: ${task.deadline}\n"
                                            )
                                        }
                                        append(
                                            "Status: " +
                                                    if (task.completed)
                                                        "Completed"
                                                    else
                                                        "Pending"
                                        )
                                    }

                                    val intent =
                                        Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(
                                                Intent.EXTRA_TEXT,
                                                shareText
                                            )
                                        }

                                    context.startActivity(
                                        Intent.createChooser(
                                            intent,
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

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Add New Task") },
            text = {
                Column(
                    verticalArrangement =
                        Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = taskName,
                        onValueChange = { taskName = it },
                        label = { Text("Task Name") },
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = taskDeadline,
                        onValueChange = { taskDeadline = it },
                        label = { Text("Deadline (YYYY-MM-DD)") },
                        placeholder = { Text("2026-10-23") },
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
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

fun saveTasks(context: Context, tasks: List<StudentTask>) {
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
        .putString("tasks", jsonArray.toString())
        .apply()
}

fun loadTasks(context: Context): List<StudentTask> {
    val savedData = context.getSharedPreferences(
        "StudyTrackPrefs",
        Context.MODE_PRIVATE
    ).getString("tasks", "[]") ?: "[]"

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
