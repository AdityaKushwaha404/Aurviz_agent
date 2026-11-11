package com.aurviz.agent.domain.model

data class Memory(
    val id: Long = 0,
    val key: String,
    val content: String,
    val category: String = "general",
    val createdAt: Long = System.currentTimeMillis(),
    val importance: Int = 1
)
