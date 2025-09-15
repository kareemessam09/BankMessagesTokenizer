# Contributing to BankMessageTokinizer

Thank you for your interest in contributing to BankMessageTokinizer! This guide will help you get started.

## 🚀 Quick Start

1. **Fork the repository**
2. **Clone your fork**:
   ```bash
   git clone https://github.com/your-username/BankMessageTokinizer.git
   cd BankMessageTokinizer
   ```
3. **Set up the development environment**
4. **Create a feature branch**: `git checkout -b feature/your-feature-name`
5. **Make your changes**
6. **Submit a pull request**

## 🛠️ Development Setup

### Prerequisites
- Java 11 or higher
- Gradle 7.0+
- Git

### Environment Setup
```bash
# Install dependencies
./gradlew build

# Run tests
./gradlew test

# Run the example
./gradlew run
```

### Model Files
The project requires model files that are not included in the repository:
- Place your model files in `src/main/resources/`
- See `src/main/resources/README.md` for details

## 📝 Contribution Guidelines

### Code Style
- Follow Kotlin coding conventions
- Use meaningful variable and function names
- Add KDoc comments for public APIs
- Maintain consistent formatting

### Commit Messages
Follow conventional commit format:
```
type(scope): description

Examples:
feat(tokenizer): add support for new currency format
fix(model): resolve entity overlap issue
docs(api): update usage examples
```

### Pull Request Process
1. Update documentation if needed
2. Add tests for new functionality
3. Ensure all tests pass
4. Update CHANGELOG.md
5. Request review from maintainers

## 🧪 Testing

### Running Tests
```bash
# Run all tests
./gradlew test

# Run with coverage
./gradlew test jacocoTestReport
```

### Test Categories
- **Unit Tests**: Individual component testing
- **Integration Tests**: End-to-end functionality
- **Performance Tests**: Accuracy and speed benchmarks

## 📚 Documentation

### API Documentation
- Update KDoc comments for new APIs
- Generate docs: `./gradlew dokkaHtml`
- Update API.md for major changes

### Examples
- Add usage examples for new features
- Update README.md if needed
- Test examples before submitting

## 🐛 Bug Reports

When reporting bugs, include:
- **Description**: Clear description of the issue
- **Reproduction**: Steps to reproduce the bug
- **Environment**: OS, Java version, model details
- **Expected vs Actual**: What should happen vs what happens
- **Logs**: Relevant error messages or logs

## 💡 Feature Requests

For new features:
- **Use Case**: Describe the problem you're solving
- **Proposed Solution**: How you think it should work
- **Alternatives**: Other approaches you've considered
- **Impact**: Who would benefit from this feature

## 🔧 Architecture Overview

```
BankMessageTokinizer
├── Core Library (FinancialNERLibrary)
├── BERT Tokenizer (Arabic/English support)
├── ONNX Model Integration
├── Rule-Based Enhancement
└── Entity Post-Processing
```

### Key Components
- **FinancialNERLibrary**: Main API interface
- **BertTokenizer**: Text tokenization with multilingual support
- **FinancialNERModel**: ONNX model wrapper with predictions
- **EntityType**: Financial entity classification system

## 📦 Release Process

1. Update version in `build.gradle.kts`
2. Update CHANGELOG.md
3. Create release branch
4. Tag release: `git tag v1.x.x`
5. Publish artifacts: `./gradlew publish`

## 🤝 Community

- **Discussions**: Use GitHub Discussions for questions
- **Issues**: Report bugs and request features
- **Security**: Email security issues privately

## 📄 License

By contributing, you agree that your contributions will be licensed under the MIT License.

## ✨ Recognition

Contributors will be acknowledged in:
- README.md contributors section
- Release notes
- GitHub contributors page

Thank you for helping make BankMessageTokinizer better! 🙏
