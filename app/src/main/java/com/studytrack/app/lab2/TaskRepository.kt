package com.studytrack.app.lab2

import kotlinx.coroutines.flow.Flow

interface TaskRepository {
    fun observeMine(): Flow<List<CloudTask>>
    suspend fun categories(): List<TaskCategory>
    suspend fun get(id: String): CloudTask
    suspend fun create(input: TaskInput)
    suspend fun update(id: String, input: TaskInput)
    suspend fun setCompleted(id: String, completed: Boolean)
    suspend fun delete(id: String)
}
