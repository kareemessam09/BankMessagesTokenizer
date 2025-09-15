# Model Files

The following model files are required but not included in the repository due to size constraints:

## Required Files:
- `model.onnx` - BERT NER model (trained model file)
- `vocab.txt` - Tokenizer vocabulary 
- `tokenizer_config.json` - Tokenizer configuration
- `special_tokens_map.json` - Special tokens mapping
- `config.json` - Model configuration

## Setup Instructions:

1. **Download/Place Model Files**:
   ```bash
   # Place your trained model files in this directory:
   src/main/resources/
   ├── model.onnx
   ├── vocab.txt
   ├── tokenizer_config.json
   ├── special_tokens_map.json
   └── config.json
   ```

2. **File Requirements**:
   - `model.onnx`: BERT-based NER model trained for financial entity extraction
   - `vocab.txt`: Vocabulary file compatible with the tokenizer
   - All JSON files: Configuration files matching your model architecture

3. **Alternative Setup**:
   If you don't have these files, you can train your own model or use a pre-trained BERT model for NER tasks.

## Security Note:
These files are excluded from version control to:
- Keep repository size manageable
- Protect proprietary model weights
- Allow users to use their own trained models

## Getting Started:
Once you have the model files in place, run:
```bash
./gradlew run
```

For production deployment, ensure all model files are properly configured in your deployment environment.
