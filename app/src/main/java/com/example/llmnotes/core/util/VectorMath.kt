package com.example.llmnotes.core.util

import kotlin.math.sqrt

object VectorMath {
    fun cosineSimilarity(v1: FloatArray, v2: FloatArray): Float {
        if (v1.size != v2.size) return 0f
        
        var dotProduct = 0f
        var normA = 0f
        var normB = 0f
        
        for (i in v1.indices) {
            dotProduct += v1[i] * v2[i]
            normA += v1[i] * v1[i]
            normB += v2[i] * v2[i]
        }
        
        if (normA == 0f || normB == 0f) return 0f
        
        return dotProduct / (sqrt(normA) * sqrt(normB))
    }
}
