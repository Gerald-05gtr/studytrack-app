package com.studytrack.app.lab2

import android.app.DatePickerDialog
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Add this Activity to AndroidManifest.xml, then launch it, or replace the Lab 1
// authenticated home destination with StudyTrackLab2Home().
class Lab2MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { StudyTrackLab2Root() } }
    }
}
@Composable
fun StudyTrackLab2Root() {
    val auth = remember { FirebaseAuth.getInstance() }
    var signedIn by remember { mutableStateOf(auth.currentUser != null) }
    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { signedIn = it.currentUser != null }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }
    if (signedIn) {
        key(auth.currentUser?.uid) { StudyTrackLab2Home(onLogout = { auth.signOut() }) }
    } else { Lab2AuthScreen(auth) }
}
@Composable
private fun Lab2AuthScreen(auth: FirebaseAuth) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("StudyTrack", style = MaterialTheme.typography.headlineMedium)
        Text("Sign in or register to manage your cloud tasks")
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true)
        OutlinedTextField(password, { password = it }, label = { Text("Password") }, singleLine = true)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(12.dp))
        Button(enabled = !busy, onClick = {
            busy = true; error = null
            auth.signInWithEmailAndPassword(email.trim(), password)
                .addOnCompleteListener { busy = false; if (!it.isSuccessful) error = it.exception?.localizedMessage ?: "Sign in failed" }
        }) { Text("Log in") }
        TextButton(enabled = !busy, onClick = {
            busy = true; error = null
            auth.createUserWithEmailAndPassword(email.trim(), password)
                .addOnCompleteListener { busy = false; if (!it.isSuccessful) error = it.exception?.localizedMessage ?: "Registration failed" }
        }) { Text("Register") }
    }
}
@Composable
fun StudyTrackLab2Home(onLogout: () -> Unit) {
    val repo = remember { FirestoreTaskRepository() }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var inForm by rememberSaveable { mutableStateOf(false) }
    BackHandler(inForm) { inForm = false; editingId = null }
    if (inForm) {
        key(editingId) {
            Lab2ScreenViewModels {
                TaskForm(repo, editingId, onDone = { inForm = false; editingId = null })
            }
        }
    } else {
        Lab2ScreenViewModels {
            TaskList(repo, onLogout, onAdd = { editingId = null; inForm = true },
                onEdit = { editingId = it; inForm = true })
        }
    }
}

// Scope each screen's ViewModels to that screen, not to the Activity.
// Closing the list cancels its Firestore snapshot listener; opening a new form
// gets fresh validation/saving state. This also prevents leaking user A's list
// when user B signs in on the same device.
@Composable
private fun Lab2ScreenViewModels(content: @Composable () -> Unit) {
    val store = remember { ViewModelStore() }
    val owner = remember(store) {
        object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore get() = store
        }
    }
    DisposableEffect(store) {
        onDispose { store.clear() }
    }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
        content()
    }
}

@Composable
private fun TaskList(repo: TaskRepository, onLogout: () -> Unit, onAdd: () -> Unit, onEdit: (String) -> Unit) {
    val vm: TaskListViewModel = viewModel(factory = taskFactory { TaskListViewModel(repo) })
    val tasks by vm.tasks.collectAsState()
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()
    val message by vm.message.collectAsState()
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(message) { message?.let { snack.showSnackbar(it); vm.message.value = null } }
    var query by rememberSaveable { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<CloudTask?>(null) }
    val filtered = remember(tasks, query) { TaskListLogic.search(tasks, query) }
    Scaffold(snackbarHost = { SnackbarHost(snack) }) { insets -> Column(Modifier.fillMaxSize().padding(insets).padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("My cloud tasks", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = onLogout) { Text("Log out") }
        }
        Text("${TaskListLogic.pendingCount(tasks)} pending · ${TaskListLogic.completedCount(tasks)} completed")
        Row {
            OutlinedTextField(value = query, onValueChange = { query = it },
                label = { Text("Search title or description") }, modifier = Modifier.weight(1f))
            TextButton(onClick = { query = "" }) { Text("Clear") }
        }
        Button(onClick = onAdd) { Text("+ Add task") }
        if (loading) { CircularProgressIndicator() }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (!loading && error == null && tasks.isEmpty()) Text("No tasks yet. Tap Add task to create your first one.")
        else if (!loading && filtered.isEmpty() && query.isNotBlank()) Text("No tasks match your search.")
        LazyColumn {
            items(filtered, key = { it.id }) { item ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.clickable { onEdit(item.id) })
                        Text("${item.categoryName} · Due ${displayDate(item.deadline?.toDate()?.time ?: 0)}")
                        Text(item.description, maxLines = 2)
                        Row {
                            TextButton(onClick = { vm.complete(item) }) { Text(if (item.completed) "Reopen" else "Done") }
                            TextButton(onClick = { onEdit(item.id) }) { Text("Edit") }
                            TextButton(onClick = { deleteTarget = item }) { Text("Delete") }
                        }
                    }
                }
            }
        }
    }
    }
    deleteTarget?.let { target ->
        AlertDialog(onDismissRequest = { deleteTarget = null }, title = { Text("Delete task?") },
            text = { Text("Delete '${target.title}' permanently?") },
            confirmButton = { TextButton(onClick = { vm.delete(target.id); deleteTarget = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } })
    }
}
private fun displayDate(millis: Long): String = if (millis <= 0L) "Not set" else
    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(millis))

@Composable
private fun TaskForm(repo: TaskRepository, id: String?, onDone: () -> Unit) {
    val vm: TaskEditViewModel = viewModel(factory = taskFactory { TaskEditViewModel(repo, id) })
    val categories by vm.categories.collectAsState()
    val original by vm.original.collectAsState()
    val loading by vm.loading.collectAsState()
    val saving by vm.saving.collectAsState()
    val saved by vm.saved.collectAsState()
    val errors by vm.errors.collectAsState()
    val error by vm.error.collectAsState()
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var categoryId by rememberSaveable { mutableStateOf("") }
    var deadline by rememberSaveable { mutableLongStateOf(0L) }
    var priority by rememberSaveable { mutableIntStateOf(1) }
    var initialized by rememberSaveable(id) { mutableStateOf(false) }
    LaunchedEffect(loading, original) {
        if (!loading && !initialized) {
            original?.let { title = it.title; description = it.description; categoryId = it.categoryId
                deadline = it.deadline?.toDate()?.time ?: 0L; priority = it.priority }
            initialized = true
        }
    }
    LaunchedEffect(saved) { if (saved) onDone() }
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(if (id == null) "Add task" else "Edit task", style = MaterialTheme.typography.headlineSmall)
        if (loading) CircularProgressIndicator()
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        OutlinedTextField(title, { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
        errors["title"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        OutlinedTextField(description, { description = it }, label = { Text("Description") },
            minLines = 2, modifier = Modifier.fillMaxWidth())
        errors["description"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Text("Category")
        categories.forEach { cat ->
            Row(Modifier.fillMaxWidth().clickable { categoryId = cat.id }) {
                RadioButton(selected = categoryId == cat.id, onClick = { categoryId = cat.id })
                Text(cat.name, modifier = Modifier.padding(top = 12.dp))
            }
        }
        errors["category"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Text("Priority")
        Row { (1..3).forEach { level ->
            FilterChip(selected = priority == level, onClick = { priority = level },
                label = { Text(level.toString()) }, modifier = Modifier.padding(end = 6.dp))
        } }
        errors["priority"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = {
            val cal = Calendar.getInstance().apply { if (deadline > 0L) timeInMillis = deadline }
            DatePickerDialog(context, { _, year, month, day ->
                deadline = Calendar.getInstance().apply { set(year, month, day, 12, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }) { Text("Deadline: ${displayDate(deadline)}") }
        errors["deadline"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row {
            Button(enabled = !loading && !saving, onClick = {
                vm.save(TaskInput(title, description, categoryId,
                    categories.firstOrNull { it.id == categoryId }?.name.orEmpty(), deadline, priority))
            }) { Text(if (saving) "Saving..." else "Save") }
            TextButton(enabled = !saving, onClick = onDone) { Text("Cancel") }
        }
    }
}
