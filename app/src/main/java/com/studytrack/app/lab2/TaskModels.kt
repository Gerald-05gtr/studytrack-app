package com.studytrack.app.lab2

import com.google.firebase.Timestamp

// Firestore requires a no-argument constructor and default values.
data class CloudTask(
    val id: String = "",
    val ownerId: String = "",
    val title: String = "",
    val description: String = "",
    val categoryId: String = "",
    val categoryName: String = "",
    val deadline: Timestamp? = null,
    val priority: Int = 1,
    val completed: Boolean = false,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)
data class TaskCategory(val id: String = "", val name: String = "", val sortOrder: Int = 0)
data class TaskInput(
    val title: String, val description: String, val categoryId: String,
    val categoryName: String, val deadlineMillis: Long, val priority: Int
)
