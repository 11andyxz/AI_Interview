package com.aiinterview.ml.indexing;

import com.aiinterview.ml.embedding.EmbeddingService;
import com.aiinterview.ml.embedding.VectorStore;
import com.aiinterview.model.KnowledgeBase;
import com.aiinterview.repository.KnowledgeBaseRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Service for indexing knowledge base into vector store
 * 
 * Indexes:
 * - System knowledge base (questions, concepts)
 * - User knowledge (resumes, profiles)
 * - Golden dataset examples
 */
@Slf4j
@Service
public class KnowledgeIndexer {
    
    @Autowired
    private VectorStore vectorStore;
    
    @Autowired
    private EmbeddingService embeddingService;
    
    @Autowired
    private KnowledgeBaseRepository knowledgeBaseRepository;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Value("${ml.indexing.enabled:true}")
    private boolean indexingEnabled;
    
    @Value("${ml.indexing.golden-dataset-path:eval/golden_dataset}")
    private String goldenDatasetPath;
    
    /**
     * Index all knowledge on application startup
     */
    @PostConstruct
    public void indexAllOnStartup() {
        if (!indexingEnabled) {
            log.info("Knowledge indexing is disabled");
            return;
        }
        
        log.info("Starting knowledge indexing on startup...");
        
        try {
            // Index system knowledge base
            indexSystemKnowledgeBase();
            
            // Index golden dataset
            indexGoldenDataset();
            
            long count = vectorStore.count();
            log.info("Knowledge indexing completed. Total vectors: {}", count);
            
        } catch (Exception e) {
            log.error("Failed to index knowledge on startup", e);
        }
    }
    
    /**
     * Index system knowledge base (questions, concepts)
     */
    public void indexSystemKnowledgeBase() {
        log.info("Indexing system knowledge base...");
        
        try {
            List<KnowledgeBase> systemKB = knowledgeBaseRepository
                .findByTypeAndIsActiveTrueOrderByCreatedAtDesc("system");
            
            int indexed = 0;
            for (KnowledgeBase kb : systemKB) {
                indexEntry(kb);
                indexed++;
            }
            
            log.info("Indexed {} system knowledge base entries", indexed);
            
        } catch (Exception e) {
            log.error("Failed to index system knowledge base", e);
        }
    }
    
    /**
     * Index a single knowledge base entry
     * 
     * @param entry Knowledge base entry
     */
    public void indexEntry(KnowledgeBase entry) {
        if (entry == null || !entry.getIsActive()) {
            return;
        }
        
        try {
            // Build indexable content
            StringBuilder content = new StringBuilder();
            
            if (entry.getTitle() != null) {
                content.append(entry.getTitle()).append(" ");
            }
            if (entry.getName() != null) {
                content.append(entry.getName()).append(" ");
            }
            if (entry.getDescription() != null) {
                content.append(entry.getDescription());
            }
            
            String textContent = content.toString().trim();
            if (textContent.isEmpty()) {
                log.warn("Skipping empty knowledge base entry: {}", entry.getId());
                return;
            }
            
            // Generate embedding
            float[] embedding = embeddingService.generateEmbedding(textContent);
            
            // Build metadata
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("content", textContent);
            metadata.put("category", entry.getCategory() != null ? entry.getCategory() : "unknown");
            metadata.put("type", entry.getType());
            metadata.put("source", "knowledge_base");
            
            if (entry.getTags() != null) {
                metadata.put("tags", entry.getTags());
            }
            
            // Upsert to vector store
            String id = "kb_" + entry.getId();
            vectorStore.upsert(id, embedding, metadata);
            
            log.debug("Indexed knowledge base entry: {}", id);
            
        } catch (Exception e) {
            log.error("Failed to index knowledge base entry: {}", entry.getId(), e);
        }
    }
    
    /**
     * Index resume analysis result
     * 
     * @param resumeId Resume ID
     * @param analysisResult Resume analysis result (JSON or text)
     */
    public void indexResume(Long resumeId, String analysisResult) {
        if (resumeId == null || analysisResult == null || analysisResult.isEmpty()) {
            return;
        }
        
        try {
            // Generate embedding
            float[] embedding = embeddingService.generateEmbedding(analysisResult);
            
            // Build metadata
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("content", analysisResult);
            metadata.put("category", "resume");
            metadata.put("source", "resume_analysis");
            metadata.put("resume_id", resumeId.toString());
            
            // Upsert to vector store
            String id = "resume_" + resumeId;
            vectorStore.upsert(id, embedding, metadata);
            
            log.debug("Indexed resume: {}", id);
            
        } catch (Exception e) {
            log.error("Failed to index resume: {}", resumeId, e);
        }
    }
    
    /**
     * Index golden dataset examples
     */
    public void indexGoldenDataset() {
        log.info("Indexing golden dataset...");
        
        try {
            Path datasetPath = Paths.get(goldenDatasetPath);
            if (!Files.exists(datasetPath)) {
                log.warn("Golden dataset path not found: {}", goldenDatasetPath);
                return;
            }
            
            // Find all JSON files in golden_dataset directory
            List<Path> jsonFiles;
            try (Stream<Path> paths = Files.walk(datasetPath)) {
                jsonFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".json"))
                    .collect(Collectors.toList());
            }
            
            log.info("Found {} JSON files in golden dataset", jsonFiles.size());
            
            int indexed = 0;
            for (Path jsonFile : jsonFiles) {
                try {
                    String content = Files.readString(jsonFile);
                    Map<String, Object> example = objectMapper.readValue(content, Map.class);
                    
                    indexGoldenExample(jsonFile.getFileName().toString(), example);
                    indexed++;
                    
                } catch (Exception e) {
                    log.error("Failed to index golden example: {}", jsonFile, e);
                }
            }
            
            log.info("Indexed {} golden dataset examples", indexed);
            
        } catch (Exception e) {
            log.error("Failed to index golden dataset", e);
        }
    }
    
    /**
     * Index a single golden dataset example
     */
    private void indexGoldenExample(String filename, Map<String, Object> example) {
        try {
            // Extract key fields from golden example
            StringBuilder content = new StringBuilder();
            
            if (example.containsKey("question")) {
                content.append("Question: ").append(example.get("question")).append(" ");
            }
            if (example.containsKey("expected_answer")) {
                content.append("Answer: ").append(example.get("expected_answer")).append(" ");
            }
            if (example.containsKey("context")) {
                content.append("Context: ").append(example.get("context"));
            }
            
            String textContent = content.toString().trim();
            if (textContent.isEmpty()) {
                return;
            }
            
            // Generate embedding
            float[] embedding = embeddingService.generateEmbedding(textContent);
            
            // Build metadata
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("content", textContent);
            metadata.put("category", "golden_example");
            metadata.put("source", "golden_dataset");
            
            if (example.containsKey("role")) {
                metadata.put("role", example.get("role").toString());
            }
            if (example.containsKey("difficulty")) {
                metadata.put("difficulty", example.get("difficulty").toString());
            }
            if (example.containsKey("skill")) {
                metadata.put("skill", example.get("skill").toString());
            }
            
            // Upsert to vector store
            String id = "golden_" + filename.replace(".json", "");
            vectorStore.upsert(id, embedding, metadata);
            
        } catch (Exception e) {
            log.error("Failed to index golden example: {}", filename, e);
        }
    }
    
    /**
     * Re-index all knowledge (useful for maintenance)
     */
    public void reindexAll() {
        log.info("Starting full re-index...");
        
        try {
            // Clear existing vectors (optional, depending on strategy)
            // For now, we'll just overwrite with new data
            
            indexSystemKnowledgeBase();
            indexGoldenDataset();
            
            long count = vectorStore.count();
            log.info("Re-indexing completed. Total vectors: {}", count);
            
        } catch (Exception e) {
            log.error("Failed to re-index knowledge", e);
        }
    }
    
    /**
     * Get indexing statistics
     */
    public Map<String, Object> getIndexingStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("total_vectors", vectorStore.count());
        stats.put("indexing_enabled", indexingEnabled);
        stats.put("vector_store_healthy", vectorStore.isHealthy());
        return stats;
    }
}
