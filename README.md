# Ollama RAG Chatbot

A production-ready **Retrieval-Augmented Generation (RAG)** chatbot built with:

| Layer | Technology |
|---|---|
| LLM + Embeddings | [Ollama](https://ollama.com) (local, GPU-optional) |
| RAG framework | [LangChain4j](https://github.com/langchain4j/langchain4j) 0.35 |
| Vector store | [ChromaDB](https://www.trychroma.com) 0.5.x |
| API server | Spring Boot 3.3 (JDK 21, virtual threads) |
| Streaming | Server-Sent Events (SSE) |

---

## Architecture

```
 User / Browser
      │  GET /api/chat/stream?message=…  (SSE)
      │  POST /api/documents/upload      (file)
      │  POST /api/documents/text        (text)
      ▼
┌─────────────────────────────────────────┐
│          Spring Boot API (8080)         │
│                                         │
│  ChatController                         │
│       │                                 │
│       ├─► RagChatService                │
│       │       │ embed query             │
│       │       ▼                         │
│       │   ChromaDB ◄──── embeddings     │
│       │       │ top-k chunks            │
│       │       ▼                         │
│       │   OllamaStreamingChatModel      │
│       │       │ token stream            │
│       │       ▼                         │
│       │   SseEmitter → client           │
│       │                                 │
│       └─► DocumentIngestionService      │
│               │ parse (Tika)            │
│               │ split → embed           │
│               ▼                         │
│           ChromaDB                      │
└─────────────────────────────────────────┘
         │                    │
    Ollama :11434        ChromaDB :8000
  (llama3.1, nomic-embed-text)
```

---

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| JDK | 21+ | Virtual-thread support |
| Maven | 3.9+ | Or use `./mvnw` |
| Ollama | latest | [Install guide](https://ollama.com) |
| Docker + Compose | v2+ | For ChromaDB |

### Pull the required Ollama models

```bash
ollama pull llama3.1           # chat model
ollama pull nomic-embed-text   # embedding model
```

---

## Quick Start

### 1. Start ChromaDB

```bash
docker compose up chromadb -d
```

### 2. Run the Spring Boot app

```bash
mvn spring-boot:run
```

Or with Docker Compose (builds the image automatically):

```bash
docker compose up --build
```

### 3. Verify

```bash
curl http://localhost:8080/api/v1/health
# → {"status":"ok","provider":"ollama","model":"llama3.1"}
```

---

## Configuration

All settings can be overridden via environment variables:

| Variable | Default | Description |
|---|---|---|
| `LLM_PROVIDER` | `ollama` | LLM backend |
| `OLLAMA_BASE_PATH` | `http://localhost:11434` | Ollama server URL |
| `OLLAMA_MODEL_PREF` | `llama3.1` | Chat model name |
| `OLLAMA_MODEL_TOKEN_LIMIT` | `4096` | Max tokens to predict |
| `EMBEDDING_ENGINE` | `ollama` | Embedding backend |
| `EMBEDDING_BASE_PATH` | `http://localhost:11434` | Ollama embedding URL |
| `EMBEDDING_MODEL_PREF` | `nomic-embed-text:latest` | Embedding model |
| `CHROMA_BASE_URL` | `http://localhost:8000` | ChromaDB HTTP URL |
| `CHROMA_COLLECTION` | `rag-documents` | Chroma collection name |
| `RAG_MAX_RESULTS` | `5` | Top-k chunks returned |
| `RAG_MIN_SCORE` | `0.7` | Minimum similarity score (0–1) |
| `RAG_CHUNK_SIZE` | `500` | Characters per document chunk |
| `RAG_CHUNK_OVERLAP` | `50` | Overlap characters between chunks |
| `SERVER_PORT` | `8080` | HTTP port |

---

## API Reference

### Legacy endpoints (v1)

#### `GET /api/v1/health`

```bash
curl http://localhost:8080/api/v1/health
```

```json
{"status": "ok", "provider": "ollama", "model": "llama3.1"}
```

#### `POST /api/v1/chat` _(deprecated – no RAG, no streaming)_

```bash
curl -X POST http://localhost:8080/api/v1/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "What is RAG?"}'
```

---

### RAG Chat

#### `GET /api/chat/stream` — **Token-streaming RAG chat (SSE)**

```bash
curl -N "http://localhost:8080/api/chat/stream?message=What+is+RAG&sessionId=my-session"
```

Each `data:` event delivers one token. A final `event: done` with `data: [DONE]` signals completion.

**JavaScript example:**

```js
const es = new EventSource(
  '/api/chat/stream?message=Explain+RAG&sessionId=abc123'
);
es.onmessage = (e) => process.stdout.write(e.data);
es.addEventListener('done', () => es.close());
```

#### `DELETE /api/chat/session/{sessionId}` — Clear a session

```bash
curl -X DELETE http://localhost:8080/api/chat/session/my-session
```

```json
{"sessionId": "my-session", "cleared": true}
```

---

### Document Ingestion

#### `POST /api/documents/upload` — Upload a file

Accepts PDF, DOCX, PPTX, TXT, HTML, Markdown, and any format Apache Tika supports.

```bash
curl -X POST http://localhost:8080/api/documents/upload \
  -F "file=@/path/to/document.pdf"
```

```json
{"filename": "document.pdf", "chunks": 42, "status": "ingested"}
```

#### `POST /api/documents/text` — Ingest plain text

```bash
curl -X POST http://localhost:8080/api/documents/text \
  -H "Content-Type: application/json" \
  -d '{"text": "ChromaDB is an open-source vector database.", "source": "docs"}'
```

```json
{"source": "docs", "chunks": 1, "status": "ingested"}
```

---

## Tested Flows

### End-to-end RAG test

```bash
# 1. Ingest a document
curl -X POST http://localhost:8080/api/documents/text \
  -H "Content-Type: application/json" \
  -d '{"text": "LangChain4j is a Java library for building LLM-powered apps.", "source": "langchain4j-intro"}'

# 2. Ask about it (streaming)
curl -N "http://localhost:8080/api/chat/stream?message=What+is+LangChain4j&sessionId=test"

# 3. Follow-up (session memory)
curl -N "http://localhost:8080/api/chat/stream?message=Tell+me+more&sessionId=test"

# 4. Clear session
curl -X DELETE http://localhost:8080/api/chat/session/test
```

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| `Connection refused` on port 11434 | Ollama not running | `ollama serve` |
| `Connection refused` on port 8000 | ChromaDB not running | `docker compose up chromadb -d` |
| Empty context in responses | Model not embedded | Ingest documents first |
| Slow first response | Cold model load | Warm up: `ollama run llama3.1 ""` |
| `IllegalStateException: llm-provider must be 'ollama'` | Wrong config | Set `LLM_PROVIDER=ollama` |
| Out-of-memory in Docker | Model too large | Increase Docker RAM or use a smaller model |

---

## Performance Tips

- **Virtual threads** (Java 21) are enabled by default – each SSE stream runs on its own lightweight thread.
- Tune `RAG_CHUNK_SIZE` and `RAG_CHUNK_OVERLAP` for your domain: smaller chunks = more precise retrieval; larger = more context per chunk.
- Lower `RAG_MIN_SCORE` (e.g. `0.5`) to broaden retrieval; raise it (e.g. `0.85`) to reduce noise.
- Use `nomic-embed-text` for general English; swap to a domain-specific embedding model for specialised corpora.
- Run Ollama with a GPU for 5–10× faster inference.

---

## License

See [LICENSE](LICENSE).

