package com.ollama.ragchatbot.service;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * Handles ingestion of documents (files or raw text) into ChromaDB.
 *
 * <p>Pipeline for each document:
 * <ol>
 *   <li>Parse bytes/text into a {@link Document}.</li>
 *   <li>Split the document into overlapping chunks with {@link DocumentSplitters#recursive}.</li>
 *   <li>Embed every chunk via the Ollama embedding model.</li>
 *   <li>Persist chunks + embeddings in ChromaDB.</li>
 * </ol>
 *
 * <p>Chunk size and overlap can be tuned via
 * {@code RAG_CHUNK_SIZE} / {@code RAG_CHUNK_OVERLAP} environment variables.
 */
@Slf4j
@Service
public class DocumentIngestionService {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final DocumentSplitter splitter;

    public DocumentIngestionService(
            EmbeddingModel embeddingModel,
            EmbeddingStore<TextSegment> embeddingStore,
            @Value("${RAG_CHUNK_SIZE:500}") int chunkSize,
            @Value("${RAG_CHUNK_OVERLAP:50}") int chunkOverlap) {

        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.splitter = DocumentSplitters.recursive(chunkSize, chunkOverlap);
    }

    /**
     * Parses an uploaded file with Apache Tika (supports PDF, DOCX, TXT, HTML, …),
     * splits it into chunks, embeds them, and stores the result in ChromaDB.
     *
     * @param file the multipart file to ingest
     * @return number of chunks stored
     * @throws IOException if the file cannot be read
     */
    public int ingestFile(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown";
        log.info("Ingesting file '{}' ({} bytes)", filename, file.getSize());

        ApacheTikaDocumentParser parser = new ApacheTikaDocumentParser();
        Document parsed = parser.parse(file.getInputStream());

        // Attach the source filename as metadata so it can be surfaced to callers.
        Document document = Document.from(parsed.text(), Metadata.metadata("source", filename));
        int count = ingest(document);
        log.info("Stored {} chunks from '{}'", count, filename);
        return count;
    }

    /**
     * Wraps a plain-text string in a {@link Document}, splits and embeds it.
     *
     * @param text   the raw text to ingest
     * @param source a human-readable label (e.g. URL or dataset name)
     * @return number of chunks stored
     */
    public int ingestText(String text, String source) {
        log.info("Ingesting text snippet from source '{}' ({} chars)", source, text.length());
        Document document = Document.from(text, Metadata.metadata("source", source));
        int count = ingest(document);
        log.info("Stored {} chunks from source '{}'", count, source);
        return count;
    }

    // ── private helpers ───────────────────────────────────────────────────────

    /**
     * Core ingestion: split → embed → store.
     *
     * @return number of segments stored
     */
    private int ingest(Document document) {
        List<TextSegment> segments = splitter.split(document);
        if (segments.isEmpty()) {
            log.warn("Document produced no segments – skipping.");
            return 0;
        }
        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
        embeddingStore.addAll(embeddings, segments);
        return segments.size();
    }
}
