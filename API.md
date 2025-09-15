# API Documentation

## BankMessageTokinizer API Reference

### Core Classes

#### FinancialNERLibrary

Main entry point for the library providing entity extraction capabilities.

##### Methods

###### `initialize()`
```kotlin
fun initialize(
    modelPath: Path,
    vocabPath: String,
    specialTokensPath: String,
    tokenizerConfigPath: String,
    configPath: String
): FinancialNERLibrary
```

**Parameters:**
- `modelPath` - Path to the ONNX model file
- `vocabPath` - Path to vocabulary file
- `specialTokensPath` - Path to special tokens configuration
- `tokenizerConfigPath` - Path to tokenizer configuration
- `configPath` - Path to model configuration

**Returns:** Initialized FinancialNERLibrary instance

**Throws:** RuntimeException if model files are missing or invalid

###### `extractEntities()`
```kotlin
fun extractEntities(text: String): List<FinancialEntity>
```

**Parameters:**
- `text` - Banking message text to analyze

**Returns:** List of detected financial entities

**Example:**
```kotlin
val entities = library.extractEntities("HSBC card ****9273 debited SAR 100.00")
```

###### `extractEntitiesAsync()`
```kotlin
suspend fun extractEntitiesAsync(text: String): List<FinancialEntity>
```

**Parameters:**
- `text` - Banking message text to analyze

**Returns:** List of detected financial entities (async)

###### `extractEntitiesBatch()`
```kotlin
suspend fun extractEntitiesBatch(texts: List<String>): List<List<FinancialEntity>>
```

**Parameters:**
- `texts` - List of banking messages to process

**Returns:** List of entity lists for each input message

#### FinancialEntity

Represents a detected financial entity with metadata.

##### Properties

```kotlin
data class FinancialEntity(
    val text: String,           // Entity text content
    val label: EntityType,      // Entity classification
    val startPosition: Int,     // Start position in original text
    val endPosition: Int,       // End position in original text
    val confidence: Float       // Confidence score (0.0-1.0)
)
```

#### EntityType

Enumeration of supported entity types.

```kotlin
enum class EntityType {
    ACCOUNT,           // Account numbers
    AMOUNT,           // Transaction amounts
    BANK,             // Bank names
    CARD,             // Card numbers (usually masked)
    DATE,             // Transaction dates
    MERCHANT,         // Merchant names
    REF,              // Reference numbers/URLs
    TRANSACTION_TYPE, // Transaction types (debit, credit, etc.)
    OTHER             // Other entities
}
```

### Configuration Classes

#### BertTokenizer

Handles text tokenization with Arabic/English support.

```kotlin
class BertTokenizer(
    vocabPath: String,
    specialTokensPath: String,
    tokenizerConfigPath: String,
    modelConfig: Map<String, Any>
)
```

### Error Handling

The library throws specific exceptions for different error conditions:

#### RuntimeException
- Missing model files
- Invalid configuration files
- Model loading failures

#### IllegalArgumentException  
- Invalid input parameters
- Unsupported text formats

### Performance Considerations

#### Memory Usage
- Initial model loading: ~500MB
- Per-message processing: ~50MB
- Batch processing: scales linearly

#### Processing Speed
- Single message: ~20ms
- Batch processing: ~10ms per message
- Async processing: up to 50 messages/second

### Thread Safety

- ✅ `extractEntities()` - Thread-safe
- ✅ `extractEntitiesAsync()` - Thread-safe  
- ✅ `extractEntitiesBatch()` - Thread-safe
- ❌ Model initialization - Not thread-safe (initialize once)

### Best Practices

#### Resource Management
```kotlin
library.use { ner ->
    // Process messages
    val entities = ner.extractEntities(message)
} // Automatically closed
```

#### Batch Processing
```kotlin
// Preferred for multiple messages
val messages = listOf("msg1", "msg2", "msg3")
val results = library.extractEntitiesBatch(messages)

// Instead of multiple single calls
messages.forEach { msg ->
    library.extractEntities(msg) // Less efficient
}
```

#### Error Handling
```kotlin
try {
    val entities = library.extractEntities(message)
} catch (e: RuntimeException) {
    logger.error("Entity extraction failed", e)
    // Handle gracefully
}
```
