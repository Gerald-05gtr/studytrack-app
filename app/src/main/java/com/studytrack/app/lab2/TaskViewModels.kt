package com.studytrack.app.lab2

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class TaskListViewModel(private val repo: TaskRepository) : ViewModel() {
    val tasks = MutableStateFlow<List<CloudTask>>(emptyList())
    val loading = MutableStateFlow(true)
    val error = MutableStateFlow<String?>(null)
    val message = MutableStateFlow<String?>(null)
    init {
        viewModelScope.launch {
            try { repo.observeMine().collect { tasks.value = it; loading.value = false } }
            catch (e: Exception) { error.value = e.message ?: "Could not load tasks"; loading.value = false }
        }
    }
    fun complete(item: CloudTask) = viewModelScope.launch {
        try { repo.setCompleted(item.id, !item.completed) }
        catch (e: Exception) { error.value = e.message ?: "Could not update task" }
    }
    fun delete(id: String) = viewModelScope.launch {
        try { repo.delete(id); message.value = "Task deleted" }
        catch (e: Exception) { error.value = e.message ?: "Could not delete task" }
    }
}
class TaskEditViewModel(private val repo: TaskRepository, private val id: String?) : ViewModel() {
    val categories = MutableStateFlow<List<TaskCategory>>(emptyList())
    val original = MutableStateFlow<CloudTask?>(null)
    val loading = MutableStateFlow(true)
    val saving = MutableStateFlow(false)
    val errors = MutableStateFlow<Map<String, String>>(emptyMap())
    val error = MutableStateFlow<String?>(null)
    val saved = MutableStateFlow(false)
    init {
        viewModelScope.launch {
            try {
                categories.value = repo.categories()
                if (id != null) original.value = repo.get(id)
            } catch (e: Exception) { error.value = e.message ?: "Could not load form" }
            finally { loading.value = false }
        }
    }
    fun save(input: TaskInput) {
        val validation = TaskValidator.validate(input, categories.value.map { it.id }.toSet())
        errors.value = validation
        if (validation.isNotEmpty() || saving.value) return
        viewModelScope.launch {
            saving.value = true
            try { if (id == null) repo.create(input) else repo.update(id, input); saved.value = true }
            catch (e: Exception) { error.value = e.message ?: "Could not save" }
            finally { saving.value = false }
        }
    }
}
@Suppress("UNCHECKED_CAST")
fun <T : ViewModel> taskFactory(create: () -> T): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        override fun <V : ViewModel> create(modelClass: Class<V>): V = create() as V
    }
