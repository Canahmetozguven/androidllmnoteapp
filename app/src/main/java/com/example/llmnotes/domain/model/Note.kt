package com.example.llmnotes.domain.model

data class Note(
    val id: String,
    val title: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
    val tags: List<String>,
    val embedding: List<Float>?
)
