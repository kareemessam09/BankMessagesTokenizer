package com.kareemessam09.bankMessagetokinizer

import java.nio.file.Path
import com.kareemessam09.bankMessagetokinizer.FinancialNERLibrary
import java.nio.file.Files
import kotlin.system.exitProcess

/**
 * Example usage of the BankMessageTokinizer library
 * This demonstrates basic entity extraction from banking messages
 */
fun main() {
    try {
        println("🏦 BankMessageTokinizer - Financial NER Library Example")
        println("=".repeat(60))

        // Check if model and tokenizer files exist
        val modelPath = Path.of("src/main/resources/model.onnx")
        val vocabPath = "src/main/resources/vocab.txt"
        val specialTokensPath = "src/main/resources/special_tokens_map.json"
        val tokenizerConfigPath = "src/main/resources/tokenizer_config.json"
        val configPath = "src/main/resources/config.json"

        println("Initializing Financial NER Library...")

        // Validate required files
        val requiredFiles = listOf(
            modelPath to "Model file",
            Path.of(vocabPath) to "Vocabulary file",
            Path.of(specialTokensPath) to "Special tokens file",
            Path.of(tokenizerConfigPath) to "Tokenizer config file",
            Path.of(configPath) to "Model config file"
        )

        for ((file, description) in requiredFiles) {
            if (!Files.exists(file)) {
                println("❌ ERROR: $description not found at: ${file.toAbsolutePath()}")
                println("Please ensure all model files are present in src/main/resources/")
                exitProcess(1)
            }
        }

        // Initialize the library
        val library = FinancialNERLibrary.initialize(
            modelPath = modelPath,
            vocabPath = vocabPath,
            specialTokensPath = specialTokensPath,
            tokenizerConfigPath = tokenizerConfigPath,
            configPath = configPath
        )

        println("✅ Library initialized successfully!")
        println()

        library.use { ner ->
            // Example banking messages for demonstration
            val exampleMessages = listOf(
                "Thank you for using HSBC card ****9273 now Debited by SAR 280.45 at Al Rajhi ATM on 03-11-2024",
                "شكراً لك لاستخدام البنك الأهلي كارت ****3921 تم خصم 543.25 جنيه في كارفور يوم 15/10/2025"
            )

            println("Processing example banking messages...")
            println("-" .repeat(60))

            exampleMessages.forEachIndexed { index, message ->
                println("\n📱 Example ${index + 1}:")
                println("Message: ${if (message.length > 80) message.take(80) + "..." else message}")

                val entities = ner.extractEntities(message)

                if (entities.isNotEmpty()) {
                    println("\nNER Results:")
                    entities.forEach { entity ->
                        val cleanText = entity.text
                            .replace("##", "")
                            .replace(Regex("\\s+"), " ")
                            .trim()
                        println("  Entity: $cleanText, Label: ${entity.label}")
                    }

                    val avgConfidence = entities.map { it.confidence }.average()
                    println("  Average Confidence: ${String.format("%.1f%%", avgConfidence * 100)}")
                } else {
                    println("  No entities detected")
                }

                if (index < exampleMessages.size - 1) {
                    println()
                }
            }

            println("\n" + "=" .repeat(60))
            println("✨ Example completed successfully!")
            println("📚 For more usage examples, see the API documentation")
        }

    } catch (e: Exception) {
        println("❌ ERROR: ${e.message}")
        println("Please check your configuration and model files")
        exitProcess(1)
    }
}