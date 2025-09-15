package com.kareemessam09.bankMessagetokinizer

import ai.onnxruntime.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.kareemessam09.bankMessagetokinizer.model.EntityType
import com.kareemessam09.bankMessagetokinizer.model.FinancialEntity
import com.kareemessam09.bankMessagetokinizer.tokenizer.BertTokenizer
import java.io.File
import java.nio.file.Path
import kotlin.math.exp

class FinancialNERModel private constructor(
    private val session: OrtSession,
    private val tokenizer: BertTokenizer,
    private val id2Label: Map<Int, String>
) : AutoCloseable {

    companion object {
        fun create(
            modelPath: Path,
            vocabPath: String,
            specialTokensPath: String,
            tokenizerConfigPath: String,
            configPath: String
        ): FinancialNERModel {
            val env = OrtEnvironment.getEnvironment()
            val sessionOptions = OrtSession.SessionOptions()
            val modelConfig = loadModelConfig(configPath)
            val tokenizer = BertTokenizer(vocabPath, specialTokensPath, tokenizerConfigPath, modelConfig)
            val id2Label = modelConfig["id2label"]?.let { map ->
                (map as Map<String, Any>).mapValues { it.value.toString() }.mapKeys { it.key.toInt() }
            } ?: defaultId2Label()

            // Validate vocab size
            if (tokenizer.vocab.size != (modelConfig["vocab_size"] as? Double)?.toInt()) {
                println("Warning: Vocab size mismatch. Config: ${(modelConfig["vocab_size"] as? Double)?.toInt()}, Loaded: ${tokenizer.vocab.size}")
            }

            // Validate num_labels
            val numLabels = (modelConfig["num_labels"] as? Double)?.toInt() ?: id2Label.size
            if (numLabels != id2Label.size) {
                throw RuntimeException("Label count mismatch. Config: $numLabels, Code: ${id2Label.size}")
            }

            println("Model config loaded: ${modelConfig["model_type"]}, Num labels: $numLabels, Vocab size: ${tokenizer.vocab.size}")

            sessionOptions.use {
                val session = env.createSession(modelPath.toString(), it)
                return FinancialNERModel(session, tokenizer, id2Label)
            }
        }

        private fun loadModelConfig(path: String): Map<String, Any> {
            val gson = Gson()
            val type = object : TypeToken<Map<String, Any>>() {}.type
            return try {
                File(path).reader().use { reader ->
                    val config: Map<String, Any> = gson.fromJson(reader, type)
                    println("Loaded model config: ${config["model_type"]}, vocab_size: ${config["vocab_size"]}, num_labels: ${config["num_labels"]}")
                    config
                }
            } catch (e: Exception) {
                throw RuntimeException("Failed to load model config from $path: ${e.message}", e)
            }
        }

        private fun defaultId2Label(): Map<Int, String> = mapOf(
            0 to "B-ACCOUNT",
            1 to "B-AMOUNT",
            2 to "B-BANK",
            3 to "B-CARD",
            4 to "B-DATE",
            5 to "B-MERCHANT",
            6 to "B-REF",
            7 to "B-TRANSACTION_TYPE",
            8 to "I-ACCOUNT",
            9 to "I-AMOUNT",
            10 to "I-BANK",
            11 to "I-CARD",
            12 to "I-DATE",
            13 to "I-MERCHANT",
            14 to "I-REF",
            15 to "I-TRANSACTION_TYPE",
            16 to "O"
        )
    }

    fun extractEntities(text: String): List<FinancialEntity> {
        val tokenization = tokenizer.tokenize(text, maxLength = 512)

        val inputInfo = session.inputInfo
        // Reduce verbose output - only show if needed
        // println("Model expects ${inputInfo.size} inputs:")
        // inputInfo.forEach { (name, info) -> println("  Input: $name, Type: $info") }

        val inputIds = OnnxTensor.createTensor(
            OrtEnvironment.getEnvironment(),
            arrayOf(tokenization.inputIds.map { it.toLong() }.toLongArray())
        )

        val inputs = if (inputInfo.size == 1) {
            val inputName = inputInfo.keys.first()
            // println("Using single input: $inputName")
            mapOf(inputName to inputIds)
        } else {
            val attentionMask = OnnxTensor.createTensor(
                OrtEnvironment.getEnvironment(),
                arrayOf(tokenization.attentionMask.map { it.toLong() }.toLongArray())
            )
            // println("Using multiple inputs: ${inputInfo.keys}")
            val inputsMap = mutableMapOf<String, OnnxTensor>()
            inputInfo.keys.forEach { inputName ->
                when {
                    inputName.contains("input_ids") || inputName == "input_ids" -> {
                        inputsMap[inputName] = inputIds
                    }
                    inputName.contains("attention") || inputName == "attention_mask" -> {
                        inputsMap[inputName] = attentionMask
                    }
                    inputName.contains("token_type") || inputName == "token_type_ids" -> {
                        val tokenTypeIds = OnnxTensor.createTensor(
                            OrtEnvironment.getEnvironment(),
                            arrayOf(IntArray(tokenization.inputIds.size) { 0 }.map { it.toLong() }.toLongArray())
                        )
                        inputsMap[inputName] = tokenTypeIds
                    }
                }
            }
            if (!inputsMap.containsValue(attentionMask)) {
                attentionMask.close()
            }
            inputsMap
        }

        val results = session.run(inputs)

        val logits = results[0].value as Array<Array<FloatArray>>
        val probabilities = logits[0].map { tokenLogits ->
            softmax(tokenLogits)
        }

        // Slice to actual sequence length to avoid out-of-bounds access
        val actualSeqLen = tokenization.tokens.size
        val slicedProbabilities = probabilities.take(actualSeqLen)

        val predictions = slicedProbabilities.mapIndexed { idx, probs ->
            val maxIdx = probs.indices.maxByOrNull { probs[it] } ?: 16
            val label = id2Label[maxIdx] ?: "Unknown"
            // Only show debug info for non-O labels or when explicitly needed
            // println("Token ${tokenization.tokens[idx]}: Label=$label, Confidence=${probs[maxIdx]}")
            maxIdx
        }

        val modelEntities = extractEntitiesFromPredictions(
            text = text,
            tokens = tokenization.tokens,
            predictions = predictions,
            probabilities = slicedProbabilities
        )

        // Add rule-based entities for common patterns the model might miss
        val ruleBasedEntities = extractRuleBasedEntities(text, tokenization.tokens, slicedProbabilities)

        // Merge and deduplicate entities with smart prioritization
        val allEntities = mergeAndDeduplicateEntities(modelEntities + ruleBasedEntities)

        inputs.values.forEach { it.close() }

        return allEntities
    }

    private fun softmax(logits: FloatArray): FloatArray {
        val maxLogit = logits.maxOrNull() ?: 0f
        val expLogits = logits.map { exp(it - maxLogit) }
        val sumExp = expLogits.sum()
        return expLogits.map { it / sumExp }.toFloatArray()
    }

    private fun extractEntitiesFromPredictions(
        text: String,
        tokens: Array<String>,
        predictions: List<Int>,
        probabilities: List<FloatArray>
    ): List<FinancialEntity> {
        val entities = mutableListOf<FinancialEntity>()
        var currentEntity: MutableList<Pair<String, Int>>? = null
        var currentLabel: String? = null
        val confidenceThreshold = 0.3f // Lower threshold to catch more entities

        // Reduce verbose debug output - only show summary
        // println("\n=== ENTITY EXTRACTION DEBUG ===")
        // println("Total tokens: ${tokens.size}")
        // println("Total predictions: ${predictions.size}")

        for (i in tokens.indices) {
            val token = tokens[i]
            val prediction = predictions.getOrNull(i) ?: 16
            val label = id2Label[prediction] ?: "O"
            val confidence = probabilities.getOrNull(i)?.get(prediction) ?: 0f
            val allProbs = probabilities.getOrNull(i)

            // Only show debug for detected entities, not all tokens
            // if (allProbs != null) {
            //     val topPredictions = allProbs.mapIndexed { idx, prob -> idx to prob }
            //         .sortedByDescending { it.second }
            //         .take(3)
            //         .map { (idx, prob) -> "${id2Label[idx] ?: "UNK"}(${String.format("%.3f", prob)})" }
            //     println("Token[$i] '$token': Top predictions: ${topPredictions.joinToString(", ")}")
            // }

            if (token in listOf("[CLS]", "[SEP]", "[PAD]")) {
                continue
            }

            // Check if this might be a financial entity even with low confidence
            val isPotentialFinancialEntity = when {
                token.matches(Regex("\\d+")) -> true // Numbers
                token.matches(Regex("\\*+")) -> true // Masked parts
                token.lowercase() in listOf("sar", "usd", "eur", "gbp", "aed") -> true // Currencies
                token.lowercase() in listOf("atm", "pos", "online", "transfer", "debit", "credit") -> true // Transaction types
                token.lowercase().contains("bank") || token.lowercase().contains("hsbc") -> true // Banks
                token.matches(Regex("\\d{2}-\\d{2}-\\d{4}")) -> true // Dates
                token.contains("##") && token.drop(2).matches(Regex("\\d+")) -> true // Subword numbers
                else -> false
            }

            // Removed debug output for cleaner console
            // if (isPotentialFinancialEntity) {
            //     println("  → Potential financial entity detected!")
            // }

            // Use lower threshold for potential financial entities
            val effectiveThreshold = if (isPotentialFinancialEntity) 0.1f else confidenceThreshold

            if (confidence < effectiveThreshold && label != "O") {
                // println("  → Skipping token '$token' with low confidence: $confidence (threshold: $effectiveThreshold)")
                continue
            }

            when {
                label.startsWith("B-") -> {
                    // Finish previous entity
                    currentEntity?.let { entity ->
                        entities.add(createEntity(entity, currentLabel!!, text, probabilities))
                        // println("  → Created entity: ${currentLabel} with ${entity.size} tokens")
                    }
                    // Start new entity
                    currentLabel = label.substring(2)
                    currentEntity = mutableListOf(token to i)
                    // println("  → Started new entity: $currentLabel")
                }
                label.startsWith("I-") && currentLabel == label.substring(2) -> {
                    // Continue current entity
                    currentEntity?.add(token to i)
                    // println("  → Continued entity: $currentLabel")
                }
                else -> {
                    // End current entity
                    currentEntity?.let { entity ->
                        entities.add(createEntity(entity, currentLabel!!, text, probabilities))
                        // println("  → Ended entity: ${currentLabel} with ${entity.size} tokens")
                    }
                    currentEntity = null
                    currentLabel = null
                }
            }
        }

        // Handle final entity
        currentEntity?.let { entity ->
            entities.add(createEntity(entity, currentLabel!!, text, probabilities))
            // println("  → Final entity: ${currentLabel} with ${entity.size} tokens")
        }

        // println("=== EXTRACTION COMPLETE: ${entities.size} entities found ===\n")
        return entities
    }

    private fun createEntity(
        tokenPositions: List<Pair<String, Int>>,
        label: String,
        originalText: String,
        probabilities: List<FloatArray>?
    ): FinancialEntity {
        val entityText = tokenPositions.joinToString(" ") { it.first }
        val tokenIndices = tokenPositions.map { it.second }
        val startPos = tokenIndices.firstOrNull() ?: 0
        val endPos = tokenIndices.lastOrNull() ?: 0

        val confidence = if (probabilities != null && tokenIndices.isNotEmpty()) {
            tokenIndices.mapNotNull { idx ->
                probabilities.getOrNull(idx)?.maxOrNull()
            }.average().toFloat().takeIf { !it.isNaN() } ?: 0f
        } else 0.8f

        return FinancialEntity(
            text = entityText,
            label = EntityType.fromBIOLabel(label),
            startPosition = startPos,
            endPosition = endPos,
            confidence = confidence
        )
    }

    private fun extractRuleBasedEntities(
        text: String,
        tokens: Array<String>,
        probabilities: List<FloatArray>
    ): List<FinancialEntity> {
        val ruleEntities = mutableListOf<FinancialEntity>()

        // Reduced verbose output for cleaner console
        // println("\n=== RULE-BASED ENTITY EXTRACTION ===")

        for (i in tokens.indices) {
            val token = tokens[i]
            if (token in listOf("[CLS]", "[SEP]", "[PAD]")) continue

            // Enhanced Currency detection with lookahead for amounts (including Arabic currencies)
            if (token.uppercase() in listOf("SAR", "USD", "EUR", "GBP", "AED", "EGP") ||
                (token == "E" && i + 1 < tokens.size && tokens[i + 1] == "##GP") ||
                token in listOf("جنيه", "جن", "ريال", "درهم", "دولار") ||
                (token == "جن" && i + 1 < tokens.size && tokens[i + 1] == "##يه")) {

                val amountTokens = mutableListOf<Pair<String, Int>>()

                // Handle Arabic currency "جنيه" split across tokens
                if (token == "جن" && i + 1 < tokens.size && tokens[i + 1] == "##يه") {
                    // Look backward for amount before currency
                    var j = i - 1
                    while (j >= 0 && j > i - 10) {
                        val prevToken = tokens[j]
                        if (prevToken.matches(Regex("\\d+")) || prevToken == "." || prevToken == "," ||
                            (prevToken.startsWith("##") && (prevToken.drop(2).matches(Regex("\\d+")) || prevToken.drop(2) == "."))) {
                            amountTokens.add(0, prevToken to j)
                            j--
                        } else {
                            break
                        }
                    }
                    amountTokens.add(token to i)
                    amountTokens.add(tokens[i + 1] to i + 1)
                }
                // Handle EGP split across tokens
                else if (token == "E" && i + 1 < tokens.size && tokens[i + 1] == "##GP") {
                    amountTokens.add(token to i)
                    amountTokens.add(tokens[i + 1] to i + 1)
                    var j = i + 2

                    // Look for amount after EGP
                    while (j < tokens.size && j < i + 12) {
                        val nextToken = tokens[j]
                        if (nextToken.matches(Regex("\\d+")) || nextToken == "." || nextToken == "," ||
                            (nextToken.startsWith("##") && (nextToken.drop(2).matches(Regex("\\d+")) || nextToken.drop(2) == "."))) {
                            amountTokens.add(nextToken to j)
                            j++
                        } else {
                            break
                        }
                    }
                }
                // Handle other currencies
                else if (token.uppercase() in listOf("SAR", "USD", "EUR", "GBP", "AED", "EGP") ||
                         token in listOf("جنيه", "ريال", "درهم", "دولار")) {
                    amountTokens.add(token to i)
                    var j = i + 1
                    while (j < tokens.size && j < i + 10) {
                        val nextToken = tokens[j]
                        if (nextToken.matches(Regex("\\d+")) || nextToken == "." || nextToken == "," ||
                            (nextToken.startsWith("##") && (nextToken.drop(2).matches(Regex("\\d+")) || nextToken.drop(2) == "."))) {
                            amountTokens.add(nextToken to j)
                            j++
                        } else {
                            break
                        }
                    }
                }

                if (amountTokens.size > 1) { // Currency + at least one number
                    ruleEntities.add(createEntity(amountTokens, "AMOUNT", text, probabilities))
                    // println("Rule-based AMOUNT entity: ${amountTokens.joinToString(" ") { it.first }}")
                }
            }

            // Enhanced Card number detection (masked)
            if (token.contains("*")) {
                val cardTokens = mutableListOf<Pair<String, Int>>()
                cardTokens.add(token to i)

                // Look for following digits
                var j = i + 1
                while (j < tokens.size && j < i + 5) {
                    val nextToken = tokens[j]
                    if (nextToken.matches(Regex("\\d+")) || (nextToken.startsWith("##") && nextToken.drop(2).matches(Regex("\\d+")))) {
                        cardTokens.add(nextToken to j)
                        j++
                    } else {
                        break
                    }
                }

                if (cardTokens.size > 1) {
                    ruleEntities.add(createEntity(cardTokens, "CARD", text, probabilities))
                    // println("Rule-based CARD entity: ${cardTokens.joinToString(" ") { it.first }}")
                }
            }

            // Enhanced Date detection for Arabic date formats (DD/MM/YYYY)
            if (token.matches(Regex("\\d{1,2}")) && i + 4 < tokens.size) {
                val datePattern = listOf(
                    tokens.getOrNull(i), tokens.getOrNull(i + 1), tokens.getOrNull(i + 2),
                    tokens.getOrNull(i + 3), tokens.getOrNull(i + 4)
                )

                // Check for DD/MM/YYYY pattern (Arabic style)
                if (datePattern.size == 5 &&
                    datePattern[1] == "##/" &&
                    datePattern[2]?.startsWith("##") == true &&
                    datePattern[3] == "##/" &&
                    datePattern[4]?.startsWith("##") == true) {

                    val dateTokens = (0..4).map { offset -> tokens[i + offset] to (i + offset) }
                    ruleEntities.add(createEntity(dateTokens, "DATE", text, probabilities))
                    // println("Rule-based DATE entity: ${dateTokens.joinToString(" ") { it.first }}")
                }
            }

            // Also check for YYYY-MM-DD format
            if (token.matches(Regex("\\d{4}")) && i + 4 < tokens.size) {
                val datePattern = listOf(
                    tokens.getOrNull(i), tokens.getOrNull(i + 1), tokens.getOrNull(i + 2),
                    tokens.getOrNull(i + 3), tokens.getOrNull(i + 4)
                )

                if (datePattern.size == 5 &&
                    datePattern[1] == "##-" &&
                    datePattern[2]?.startsWith("##") == true &&
                    datePattern[3] == "##-" &&
                    datePattern[4]?.startsWith("##") == true) {

                    val dateTokens = (0..4).map { offset -> tokens[i + offset] to (i + offset) }
                    ruleEntities.add(createEntity(dateTokens, "DATE", text, probabilities))
                    // println("Rule-based DATE entity: ${dateTokens.joinToString(" ") { it.first }}")
                }
            }

            // Enhanced Bank name detection for Arabic banks
            when {
                // Handle Arabic "البنك الأهلي" (Al Ahli Bank)
                token == "البنك" && i + 1 < tokens.size && tokens[i + 1] == "ال" -> {
                    val bankTokens = mutableListOf<Pair<String, Int>>()
                    bankTokens.add(token to i)

                    // Look for following tokens that are part of bank name
                    var j = i + 1
                    while (j < tokens.size && j < i + 5) {
                        val nextToken = tokens[j]
                        if (nextToken.startsWith("##") && nextToken.drop(2) in listOf("أهل", "ي") ||
                            nextToken in listOf("ال", "الأهلي", "أهلي")) {
                            bankTokens.add(nextToken to j)
                            j++
                        } else {
                            break
                        }
                    }

                    if (bankTokens.size > 1) {
                        ruleEntities.add(createEntity(bankTokens, "BANK", text, probabilities))
                        // println("Rule-based BANK entity: ${bankTokens.joinToString(" ") { it.first }}")
                    }
                }
                // Handle CIB bank
                token == "C" && i + 1 < tokens.size && tokens[i + 1] == "##IB" -> {
                    val bankTokens = listOf(token to i, tokens[i + 1] to i + 1)
                    ruleEntities.add(createEntity(bankTokens, "BANK", text, probabilities))
                    // println("Rule-based BANK entity: ${bankTokens.joinToString(" ") { it.first }}")
                }
                // Handle HSBC bank
                token == "HS" && i + 1 < tokens.size && tokens[i + 1] == "##BC" -> {
                    val bankTokens = listOf(token to i, tokens[i + 1] to i + 1)
                    ruleEntities.add(createEntity(bankTokens, "BANK", text, probabilities))
                    // println("Rule-based BANK entity: ${bankTokens.joinToString(" ") { it.first }}")
                }
            }

            // Enhanced Merchant detection for Arabic merchants
            when {
                // Handle Vodafone
                token == "Voda" && i + 1 < tokens.size && tokens[i + 1] == "##fone" -> {
                    val merchantTokens = mutableListOf<Pair<String, Int>>()
                    merchantTokens.add(token to i)
                    merchantTokens.add(tokens[i + 1] to i + 1)

                    // Look for "Store" or other merchant descriptors
                    var j = i + 2
                    while (j < tokens.size && j < i + 5) {
                        val nextToken = tokens[j]
                        if (nextToken.lowercase() in listOf("store", "shop", "mall", "center", "atm", "branch") ||
                            nextToken.startsWith("##") && nextToken.drop(2).lowercase() in listOf("store", "shop", "mall", "center")) {
                            merchantTokens.add(nextToken to j)
                            j++
                        } else {
                            break
                        }
                    }

                    ruleEntities.add(createEntity(merchantTokens, "MERCHANT", text, probabilities))
                    // println("Rule-based MERCHANT entity: ${merchantTokens.joinToString(" ") { it.first }}")
                }
                // Handle Carrefour in Arabic "كارفور"
                token == "ك" && i + 2 < tokens.size &&
                tokens[i + 1] == "##ارف" && tokens[i + 2] == "##ور" -> {
                    val merchantTokens = listOf(
                        token to i,
                        tokens[i + 1] to i + 1,
                        tokens[i + 2] to i + 2
                    )
                    ruleEntities.add(createEntity(merchantTokens, "MERCHANT", text, probabilities))
                    // println("Rule-based MERCHANT entity: ${merchantTokens.joinToString(" ") { it.first }}")
                }
            }

            // Enhanced Transaction type detection for Arabic
            if (token.lowercase() in listOf("transfer", "debit", "credit", "payment", "deposit", "withdrawal") ||
                (token.lowercase() == "transfer" && i + 1 < tokens.size && tokens[i + 1] == "##red") ||
                token in listOf("تم", "خصم", "إيداع", "تحويل", "سحب") ||
                (token == "تم" && i + 3 < tokens.size && tokens[i + 1] == "خ" && tokens[i + 2] == "##ص" && tokens[i + 3] == "##م")) {

                val transactionTokens = mutableListOf<Pair<String, Int>>()

                // Handle Arabic "تم خصم" (was debited)
                if (token == "تم" && i + 3 < tokens.size &&
                    tokens[i + 1] == "خ" && tokens[i + 2] == "##ص" && tokens[i + 3] == "##م") {
                    transactionTokens.add(token to i)
                    transactionTokens.add(tokens[i + 1] to i + 1)
                    transactionTokens.add(tokens[i + 2] to i + 2)
                    transactionTokens.add(tokens[i + 3] to i + 3)
                } else {
                    transactionTokens.add(token to i)

                    // Look for related suffixes for English
                    if (i + 1 < tokens.size && tokens[i + 1].startsWith("##")) {
                        val suffix = tokens[i + 1].drop(2).lowercase()
                        if (suffix in listOf("red", "ed", "ing", "ment", "al")) {
                            transactionTokens.add(tokens[i + 1] to i + 1)
                        }
                    }

                    // Look backward for prefixes like "De"
                    if (i > 0 && tokens[i - 1].lowercase() in listOf("de", "pre", "re")) {
                        transactionTokens.add(0, tokens[i - 1] to i - 1)
                    }
                }

                if (transactionTokens.size >= 1) {
                    transactionTokens.sortBy { it.second }
                    ruleEntities.add(createEntity(transactionTokens, "TRANSACTION_TYPE", text, probabilities))
                    // println("Rule-based TRANSACTION_TYPE entity: ${transactionTokens.joinToString(" ") { it.first }}")
                }
            }

            // Website/Bank URL detection (enhanced for Arabic banks)
            if ((token.lowercase().contains("bank") || token.lowercase() == "cibeg" || token.lowercase() == "ahlibank") &&
                i + 1 < tokens.size && tokens[i + 1] == "com") {

                val refTokens = mutableListOf<Pair<String, Int>>()

                // Look backward for https
                if (i > 0 && tokens[i - 1] == "https") {
                    refTokens.add(tokens[i - 1] to i - 1)
                }

                refTokens.add(token to i)
                refTokens.add(tokens[i + 1] to i + 1)

                // Look forward for domain extensions
                var j = i + 2
                while (j < tokens.size && j < i + 5) {
                    val nextToken = tokens[j]
                    if (nextToken.lowercase() in listOf("eg", "sa", "ae", "com", "org", "net") ||
                        nextToken.lowercase().contains("online") || nextToken.lowercase().contains("services")) {
                        refTokens.add(nextToken to j)
                        j++
                    } else {
                        break
                    }
                }

                if (refTokens.size > 2) { // At least domain + extension
                    ruleEntities.add(createEntity(refTokens, "REF", text, probabilities))
                    // println("Rule-based REF entity: ${refTokens.joinToString(" ") { it.first }}")
                }
            }
        }

        // println("=== RULE-BASED EXTRACTION COMPLETE: ${ruleEntities.size} entities found ===\n")
        return ruleEntities
    }

    private fun mergeAndDeduplicateEntities(entities: List<FinancialEntity>): List<FinancialEntity> {
        if (entities.isEmpty()) return entities

        val sortedEntities = entities.sortedBy { it.startPosition }
        val mergedEntities = mutableListOf<FinancialEntity>()

        for (entity in sortedEntities) {
            val overlappingEntities = mergedEntities.filter { existing ->
                // Check if entities overlap in position and are of the same type
                existing.label == entity.label &&
                (entity.startPosition <= existing.endPosition && entity.endPosition >= existing.startPosition)
            }

            if (overlappingEntities.isEmpty()) {
                // No overlap, add the entity
                mergedEntities.add(entity)
            } else {
                // Handle overlapping entities by prioritizing better ones
                val bestEntity = chooseBestEntity(entity, overlappingEntities)

                // Remove all overlapping entities and add the best one
                mergedEntities.removeAll(overlappingEntities)
                mergedEntities.add(bestEntity)
            }
        }

        return mergedEntities.sortedBy { it.startPosition }
    }

    private fun chooseBestEntity(
        newEntity: FinancialEntity,
        existingEntities: List<FinancialEntity>
    ): FinancialEntity {
        val allEntities = existingEntities + newEntity

        return allEntities.maxByOrNull { entity ->
            var score = 0.0

            // Prioritize longer, more complete text (e.g., "HS BC" over "HS")
            score += entity.text.replace("##", "").trim().length * 2.0

            // Prioritize higher confidence
            score += entity.confidence * 1.0

            // Bonus for complete bank names
            val cleanText = entity.text.replace("##", "").replace(Regex("\\s+"), "").uppercase()
            when {
                cleanText.contains("HSBC") -> score += 10.0
                cleanText.contains("CIB") -> score += 10.0
                cleanText.contains("RAJHI") -> score += 10.0
                cleanText.contains("VODAFONE") -> score += 10.0
            }

            // Penalty for partial/fragmented entities
            if (entity.text.length <= 3 && !entity.text.matches(Regex("\\d+"))) {
                score -= 5.0
            }

            score
        } ?: newEntity
    }

    override fun close() {
        session.close()
    }
}