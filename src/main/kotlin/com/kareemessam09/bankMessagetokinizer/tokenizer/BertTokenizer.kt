package com.kareemessam09.bankMessagetokinizer.tokenizer

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

data class TokenizationResult(
    val inputIds: IntArray,
    val attentionMask: IntArray,
    val tokens: Array<String>
)

class BertTokenizer(
    private val vocabPath: String,
    private val specialTokensPath: String,
    private val tokenizerConfigPath: String,
    private val modelConfig: Map<String, Any> // From config.json
) {
    val vocab: Map<String, Int>
    private val reverseVocab: Map<Int, String>
    private val specialTokens: Map<String, String>
    private val doLowerCase: Boolean

    private val clsToken: String
    private val sepToken: String
    private val padToken: String
    private val unkToken: String

    init {
        // Load vocabulary from vocab.txt
        vocab = loadVocab(vocabPath)
        reverseVocab = vocab.entries.associate { it.value to it.key }

        // Validate vocab size against model config
        val configVocabSize = (modelConfig["vocab_size"] as? Double)?.toInt() ?: 0
        if (vocab.size != configVocabSize) {
            println("Warning: Vocab size mismatch. Config: $configVocabSize, Loaded: ${vocab.size}")
        }

        // Load special tokens from special_tokens_map.json
        specialTokens = loadSpecialTokens(specialTokensPath)

        // Load tokenizer config from tokenizer_config.json for do_lower_case
        doLowerCase = loadTokenizerConfig(tokenizerConfigPath)

        clsToken = specialTokens["cls_token"] ?: "[CLS]"
        sepToken = specialTokens["sep_token"] ?: "[SEP]"
        padToken = specialTokens["pad_token"] ?: "[PAD]"
        unkToken = specialTokens["unk_token"] ?: "[UNK]"

        println("Loaded ${vocab.size} tokens from $vocabPath")
        println("Special tokens: $specialTokens")
        println("Do lower case: $doLowerCase")
    }

    private fun loadVocab(path: String): Map<String, Int> {
        val vocabMap = mutableMapOf<String, Int>()
        try {
            File(path).readLines().forEachIndexed { index, token ->
                vocabMap[token.trim()] = index
            }
            println("Loaded ${vocabMap.size} tokens from vocabulary")
        } catch (e: Exception) {
            throw RuntimeException("Failed to load vocabulary from $path: ${e.message}", e)
        }
        return vocabMap
    }

    private fun loadSpecialTokens(path: String): Map<String, String> {
        val gson = Gson()
        val type = object : TypeToken<Map<String, String>>() {}.type
        return try {
            File(path).reader().use { reader ->
                val tokens: Map<String, String> = gson.fromJson(reader, type)
                println("Loaded special tokens: $tokens")
                tokens
            }
        } catch (e: Exception) {
            throw RuntimeException("Failed to load special tokens from $path: ${e.message}", e)
        }
    }

    private fun loadTokenizerConfig(path: String): Boolean {
        val gson = Gson()
        val type = object : TypeToken<Map<String, Any>>() {}.type
        return try {
            File(path).reader().use { reader ->
                val config: Map<String, Any> = gson.fromJson(reader, type)
                println("Loaded tokenizer config: $config")
                config["do_lower_case"] as? Boolean ?: false
            }
        } catch (e: Exception) {
            println("Warning: Failed to load tokenizer config from $path, defaulting to do_lower_case=false")
            false
        }
    }

    fun tokenize(
        text: String,
        maxLength: Int = 512,
        padding: Boolean = true,
        truncation: Boolean = true
    ): TokenizationResult {
        // Reduced verbose output for cleaner console

        val processedText = convertArabicNumerals(text)
        // println("After Arabic numeral conversion: '$processedText'")

        val tokens = wordPieceTokenize(processedText)

        val allTokens = mutableListOf(clsToken)
        allTokens.addAll(tokens)
        allTokens.add(sepToken)

        if (truncation && allTokens.size > maxLength) {
            val truncated = allTokens.take(maxLength - 1).toMutableList()
            truncated.add(sepToken)
            allTokens.clear()
            allTokens.addAll(truncated)
        }

        val inputIds = allTokens.map { token ->
            vocab[token] ?: run {
                // Only show warnings for important missing tokens

                vocab[unkToken] ?: 0
            }
        }.toIntArray()

        val attentionMask = IntArray(inputIds.size) { 1 }

        val finalInputIds: IntArray
        val finalAttentionMask: IntArray

        if (padding && inputIds.size < maxLength) {
            val padId = vocab[padToken] ?: 0
            finalInputIds = IntArray(maxLength) { i ->
                if (i < inputIds.size) inputIds[i] else padId
            }
            finalAttentionMask = IntArray(maxLength) { i ->
                if (i < inputIds.size) attentionMask[i] else 0
            }
        } else {
            finalInputIds = inputIds
            finalAttentionMask = attentionMask
        }

        // Removed verbose token and ID output for cleaner console
        // println("Final tokens: ${allTokens.joinToString()}")
        // println("Input IDs: ${finalInputIds.joinToString()}")

        return TokenizationResult(
            inputIds = finalInputIds,
            attentionMask = finalAttentionMask,
            tokens = allTokens.toTypedArray()
        )
    }

    private fun convertArabicNumerals(text: String): String {
        val arabicToEnglish = mapOf(
            '٠' to '0', '١' to '1', '٢' to '2', '٣' to '3', '٤' to '4',
            '٥' to '5', '٦' to '6', '٧' to '7', '٨' to '8', '٩' to '9',
            '٫' to '.', '،' to ','
        )
        return arabicToEnglish.entries.fold(text) { acc, (arabic, english) ->
            acc.replace(arabic, english)
        }
    }

    private fun wordPieceTokenize(text: String): List<String> {
        val tokens = mutableListOf<String>()
        val words = text.trim().split(Regex("\\s+"))

        for (word in words) {
            if (word.isEmpty()) continue

            when {
                // Handle URLs by splitting on common delimiters
                word.startsWith("http") -> {
                    val urlParts = word.split(Regex("[:/.?=&]")).filter { it.isNotEmpty() }
                    tokens.addAll(urlParts)
                }
                // Handle masked card numbers - treat as separate tokens
                word.contains("*") -> {
                    var temp = word
                    // Split asterisks and numbers separately
                    temp = temp.replace(Regex("(\\*+)"), " $1 ")
                    temp = temp.replace(Regex("(\\d+)"), " $1 ")
                    temp = temp.replace(Regex("\\s+"), " ")
                    val parts = temp.trim().split(" ").filter { it.isNotEmpty() }
                    for (part in parts) {
                        if (part.matches(Regex("\\*+"))) {
                            tokens.add(part) // Keep asterisks as is
                        } else if (part.matches(Regex("\\d+"))) {
                            // For digits, try to tokenize or use as single token
                            if (vocab.containsKey(part)) {
                                tokens.add(part)
                            } else {
                                // Split digits into smaller chunks if possible
                                tokens.addAll(tokenizeNumericValue(part))
                            }
                        } else {
                            tokens.addAll(subwordTokenize(part))
                        }
                    }
                }
                // Handle dates - keep as single token initially, then try subword if not found
                word.matches(Regex("\\d{2}-\\d{2}-\\d{4}")) -> {
                    if (vocab.containsKey(word)) {
                        tokens.add(word)
                    } else {
                        // Split date components
                        val dateParts = word.split("-")
                        for (part in dateParts) {
                            tokens.addAll(tokenizeNumericValue(part))
                        }
                        if (vocab.containsKey("-")) tokens.add("-") // Add separator if it exists in vocab
                    }
                }
                // Handle amounts with decimals
                word.matches(Regex("[\\d.,]+")) -> {
                    tokens.addAll(tokenizeNumericValue(word))
                }
                // Handle words ending with punctuation
                word.matches(Regex(""".*[.,;:!?]+$""")) -> {
                    val mainPart = word.dropLastWhile { it in ".,;:!?" }
                    val punctuation = word.drop(mainPart.length)
                    if (mainPart.isNotEmpty()) {
                        tokens.addAll(subwordTokenize(mainPart))
                    }
                    if (punctuation.isNotEmpty() && vocab.containsKey(punctuation)) {
                        tokens.add(punctuation)
                    }
                }
                else -> {
                    tokens.addAll(subwordTokenize(word))
                }
            }
        }

        // Removed verbose tokenization output for cleaner console
        // println("Tokenized into: $tokens")
        return tokens
    }

    private fun tokenizeNumericValue(value: String): List<String> {
        // Try the full value first
        if (vocab.containsKey(value)) {
            return listOf(value)
        }

        // Try to split on decimal points and commas
        val tokens = mutableListOf<String>()
        val parts = value.split(Regex("[.,]"))

        for (i in parts.indices) {
            val part = parts[i]
            if (vocab.containsKey(part)) {
                tokens.add(part)
            } else {
                // Try to break down further - digit by digit if necessary
                val subTokens = subwordTokenize(part)
                tokens.addAll(subTokens)
            }

            // Add separator if not last part
            if (i < parts.size - 1) {
                val separator = if (value.contains(".")) "." else ","
                if (vocab.containsKey(separator)) {
                    tokens.add(separator)
                }
            }
        }

        return tokens.ifEmpty { listOf(unkToken) }
    }

    private fun subwordTokenize(word: String): List<String> {
        val tokens = mutableListOf<String>()
        var remaining = if (doLowerCase) word.lowercase() else word
        var isFirst = true

        while (remaining.isNotEmpty()) {
            var matched = false
            for (i in remaining.length downTo 1) {
                val subword = remaining.take(i)
                val token = if (isFirst) subword else "##$subword"
                if (vocab.containsKey(token)) {
                    tokens.add(token)
                    remaining = remaining.drop(i)
                    matched = true
                    isFirst = false
                    break
                }
            }
            if (!matched) {
                tokens.add(unkToken)
                println("Warning: Could not tokenize '$word', using [UNK]")
                break
            }
        }
        return tokens
    }

    fun convertIdsToTokens(ids: IntArray): Array<String> {
        return ids.map { id -> reverseVocab[id] ?: unkToken }.toTypedArray()
    }
}