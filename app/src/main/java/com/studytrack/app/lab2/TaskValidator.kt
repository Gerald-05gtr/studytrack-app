package com.studytrack.app.lab2

// Pure validation: no Compose, Android or Firebase imports. Good for Lab 3 tests.
object TaskValidator {
    fun validate(input: TaskInput, categoryIds: Set<String>): Map<String, String> {
        val errors = mutableMapOf<String, String>()
        if (input.title.trim().length !in 3..100) errors["title"] = "Title must contain 3–100 characters"
        if (input.description.length > 200) errors["description"] = "Description must be at most 200 characters"
        if (input.categoryId !in categoryIds) errors["category"] = "Choose a valid category"
        if (input.deadlineMillis <= 0L) errors["deadline"] = "Choose a deadline"
        if (input.priority !in 1..3) errors["priority"] = "Priority must be 1, 2 or 3"
        // Cross-field rule: high-priority tasks need a descriptive explanation.
        if (input.priority == 3 && input.description.trim().length < 10)
            errors["description"] = "High-priority tasks need at least 10 description characters"
        return errors
    }
}
object TaskListLogic {
    fun search(tasks: List<CloudTask>, query: String): List<CloudTask> {
        val q = query.trim()
        return if (q.isEmpty()) tasks else tasks.filter {
            it.title.contains(q, ignoreCase = true) || it.description.contains(q, ignoreCase = true)
        }
    }
    fun completedCount(tasks: List<CloudTask>) = tasks.count { it.completed }
    fun pendingCount(tasks: List<CloudTask>) = tasks.count { !it.completed }
}
