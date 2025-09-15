package com.kareemessam09.bankMessagetokinizer

import com.kareemessam09.bankMessagetokinizer.model.FinancialEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path

/**
 * Main entry point for the Financial NER Library
 */
class FinancialNERLibrary private constructor(
    private val model: FinancialNERModel
) : AutoCloseable {

    companion object {
        /**
         * Initialize the library with model files and tokenizer files
         */
        fun initialize(
            modelPath: Path,
            vocabPath: String,
            specialTokensPath: String,
            tokenizerConfigPath: String,
            configPath: String
        ): FinancialNERLibrary {
            val model = FinancialNERModel.create(modelPath, vocabPath, specialTokensPath, tokenizerConfigPath, configPath)
            return FinancialNERLibrary(model)
        }
    }

    /**
     * Extract financial entities from text synchronously
     */
    fun extractEntities(text: String): List<FinancialEntity> {
        return model.extractEntities(text)
    }

    /**
     * Extract financial entities from text asynchronously
     */
    suspend fun extractEntitiesAsync(text: String): List<FinancialEntity> = withContext(Dispatchers.IO) {
        model.extractEntities(text)
    }

    /**
     * Process multiple texts in batch
     */
    suspend fun extractEntitiesBatch(texts: List<String>): List<List<FinancialEntity>> = withContext(Dispatchers.IO) {
        texts.map { text ->
            model.extractEntities(text)
        }
    }

    override fun close() {
        model.close()
    }
}