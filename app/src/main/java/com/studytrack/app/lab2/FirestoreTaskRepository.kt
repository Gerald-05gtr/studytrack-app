package com.studytrack.app.lab2

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirestoreTaskRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) : TaskRepository {
    private fun uid() = auth.currentUser?.uid ?: error("Please sign in")
    private val tasks get() = db.collection("tasks")
    override fun observeMine(): Flow<List<CloudTask>> = callbackFlow {
        val registration = tasks.whereEqualTo("ownerId", uid())
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val result = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(CloudTask::class.java)?.copy(id = doc.id)
                }.orEmpty()
                trySend(result.sortedBy { it.deadline?.seconds ?: Long.MAX_VALUE })
            }
        awaitClose { registration.remove() }
    }
    override suspend fun categories(): List<TaskCategory> = db.collection("categories")
        .orderBy("sortOrder", Query.Direction.ASCENDING).get().await().documents
        .mapNotNull { it.toObject(TaskCategory::class.java)?.copy(id = it.id) }
    override suspend fun get(id: String): CloudTask {
        val doc = tasks.document(id).get().await()
        val item = doc.toObject(CloudTask::class.java)?.copy(id = doc.id)
            ?: error("Task not found")
        check(item.ownerId == uid()) { "Not permitted" }
        return item
    }
    override suspend fun create(input: TaskInput) {
        tasks.add(mapOf(
            "ownerId" to uid(), "title" to input.title.trim(),
            "description" to input.description.trim(), "categoryId" to input.categoryId,
            "categoryName" to input.categoryName,
            "deadline" to Timestamp(input.deadlineMillis / 1000, 0),
            "priority" to input.priority, "completed" to false,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )).await()
    }
    override suspend fun update(id: String, input: TaskInput) {
        // Never rewrite ownerId or createdAt.
        tasks.document(id).update(mapOf(
            "title" to input.title.trim(), "description" to input.description.trim(),
            "categoryId" to input.categoryId, "categoryName" to input.categoryName,
            "deadline" to Timestamp(input.deadlineMillis / 1000, 0),
            "priority" to input.priority,
            "updatedAt" to FieldValue.serverTimestamp()
        )).await()
    }
    override suspend fun setCompleted(id: String, completed: Boolean) {
        tasks.document(id).update(mapOf(
            "completed" to completed, "updatedAt" to FieldValue.serverTimestamp()
        )).await()
    }
    override suspend fun delete(id: String) { tasks.document(id).delete().await() }
}
