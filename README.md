# ollama-rag-chatbot

Spring Boot (JDK 21) endpoints using LangChain4j + open-source Ollama models.

## AnythingLLM-aligned configuration

The application reuses key environment variable names from AnythingLLM so existing configs can be applied directly:

- `LLM_PROVIDER` (default: `ollama`)
- `OLLAMA_BASE_PATH` (default: `http://localhost:11434`)
- `OLLAMA_MODEL_PREF` (default: `llama3.1`)
- `OLLAMA_MODEL_TOKEN_LIMIT` (default: `4096`)
- `EMBEDDING_ENGINE` (default: `ollama`)
- `EMBEDDING_BASE_PATH` (default: `http://localhost:11434`)
- `EMBEDDING_MODEL_PREF` (default: `nomic-embed-text:latest`)
- `VECTOR_DB` (default: `lancedb`)
- `SERVER_PORT` (default: `8080`)

## Run

```bash
mvn spring-boot:run
```

## Endpoints

- `GET /api/v1/health`
- `POST /api/v1/chat`

Chat request example:

```json
{
  "message": "Explain RAG in one sentence"
}
```
