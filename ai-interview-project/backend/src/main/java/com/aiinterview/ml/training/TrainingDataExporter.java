package com.aiinterview.ml.training;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Exports training data to versioned JSONL format
 * Creates train/validation/test splits with metadata
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrainingDataExporter {
    
    private final TrainingDataCollector collector;
    private final ObjectMapper objectMapper;
    
    private static final String BASE_DIR = "eval/training_data/exports";
    private static final double TRAIN_RATIO = 0.8;
    private static final double VAL_RATIO = 0.1;
    private static final double TEST_RATIO = 0.1;
    
    /**
     * Export training data with automatic versioning
     * 
     * @param from Start date for data collection
     * @param to End date for data collection
     * @return Path to export directory
     */
    public Path exportTrainingData(LocalDate from, LocalDate to) throws IOException {
        log.info("Exporting training data from {} to {}", from, to);
        
        // Create version directory
        String version = generateVersion();
        Path exportDir = createExportDirectory(version);
        
        // Collect data
        List<TrainingExample> examples = collector.collectQualityPairs(from, to);
        
        if (examples.isEmpty()) {
            log.warn("No training examples found in date range");
            throw new IllegalStateException("No training data available for export");
        }
        
        // Shuffle for random split
        Collections.shuffle(examples);
        
        // Split data
        int trainSize = (int) (examples.size() * TRAIN_RATIO);
        int valSize = (int) (examples.size() * VAL_RATIO);
        
        List<TrainingExample> trainSet = examples.subList(0, trainSize);
        List<TrainingExample> valSet = examples.subList(trainSize, trainSize + valSize);
        List<TrainingExample> testSet = examples.subList(trainSize + valSize, examples.size());
        
        // Export JSONL files
        exportToJsonl(trainSet, exportDir.resolve("train.jsonl"));
        exportToJsonl(valSet, exportDir.resolve("validation.jsonl"));
        exportToJsonl(testSet, exportDir.resolve("test.jsonl"));
        
        // Export metadata
        exportMetadata(version, from, to, trainSet, valSet, testSet, exportDir);
        
        // Generate quality report
        generateQualityReport(trainSet, valSet, testSet, exportDir);
        
        log.info("Successfully exported training data to {}", exportDir);
        
        return exportDir;
    }
    
    /**
     * Export preference pairs for RLHF/DPO
     * 
     * @return Path to export directory
     */
    public Path exportPreferencePairs() throws IOException {
        log.info("Exporting preference pairs for RLHF/DPO");
        
        String version = generateVersion() + "_preferences";
        Path exportDir = createExportDirectory(version);
        
        List<PreferencePair> pairs = collector.collectPreferencePairs();
        
        if (pairs.isEmpty()) {
            log.warn("No preference pairs found");
            throw new IllegalStateException("No preference pairs available for export");
        }
        
        // Split data
        Collections.shuffle(pairs);
        int trainSize = (int) (pairs.size() * TRAIN_RATIO);
        int valSize = (int) (pairs.size() * VAL_RATIO);
        
        List<PreferencePair> trainPairs = pairs.subList(0, trainSize);
        List<PreferencePair> valPairs = pairs.subList(trainSize, trainSize + valSize);
        List<PreferencePair> testPairs = pairs.subList(trainSize + valSize, pairs.size());
        
        // Export to JSONL
        exportPreferencesToJsonl(trainPairs, exportDir.resolve("train_preferences.jsonl"));
        exportPreferencesToJsonl(valPairs, exportDir.resolve("validation_preferences.jsonl"));
        exportPreferencesToJsonl(testPairs, exportDir.resolve("test_preferences.jsonl"));
        
        log.info("Exported {} preference pairs to {}", pairs.size(), exportDir);
        
        return exportDir;
    }
    
    /**
     * Generate version identifier
     */
    private String generateVersion() {
        return "v1.0_" + LocalDate.now().format(DateTimeFormatter.ISO_DATE);
    }
    
    /**
     * Create export directory with version
     */
    private Path createExportDirectory(String version) throws IOException {
        Path dir = Paths.get(BASE_DIR, version);
        Files.createDirectories(dir);
        return dir;
    }
    
    /**
     * Export training examples to JSONL format
     */
    private void exportToJsonl(List<TrainingExample> examples, Path outputPath) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(outputPath)) {
            for (TrainingExample example : examples) {
                // Convert to OpenAI chat format
                Map<String, Object> record = new HashMap<>();
                record.put("messages", example.getMessages());
                
                // Add metadata
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("interaction_id", example.getInteractionId());
                metadata.put("role", example.getRole());
                metadata.put("difficulty", example.getDifficulty());
                metadata.put("quality_score", example.getQualityScore());
                metadata.put("category", example.getCategory());
                metadata.put("skill", example.getSkill());
                record.put("metadata", metadata);
                
                writer.write(objectMapper.writeValueAsString(record));
                writer.newLine();
            }
        }
        
        log.info("Exported {} examples to {}", examples.size(), outputPath.getFileName());
    }
    
    /**
     * Export preference pairs to JSONL format
     */
    private void exportPreferencesToJsonl(List<PreferencePair> pairs, Path outputPath) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(outputPath)) {
            for (PreferencePair pair : pairs) {
                Map<String, Object> record = new HashMap<>();
                record.put("prompt", pair.getPrompt());
                record.put("context", pair.getContext());
                record.put("chosen", pair.getChosen().getContent());
                record.put("rejected", pair.getRejected().getContent());
                record.put("margin", pair.getMargin());
                
                // Metadata
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("pair_id", pair.getPairId());
                metadata.put("role", pair.getRole());
                metadata.put("difficulty", pair.getDifficulty());
                metadata.put("chosen_score", pair.getChosen().getQualityScore());
                metadata.put("rejected_score", pair.getRejected().getQualityScore());
                record.put("metadata", metadata);
                
                writer.write(objectMapper.writeValueAsString(record));
                writer.newLine();
            }
        }
        
        log.info("Exported {} preference pairs to {}", pairs.size(), outputPath.getFileName());
    }
    
    /**
     * Export metadata about the dataset
     */
    private void exportMetadata(String version, LocalDate from, LocalDate to,
                               List<TrainingExample> train, 
                               List<TrainingExample> val,
                               List<TrainingExample> test,
                               Path exportDir) throws IOException {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("version", version);
        metadata.put("created_at", LocalDate.now().toString());
        metadata.put("date_range", Map.of("from", from.toString(), "to", to.toString()));
        
        Map<String, Integer> splits = new HashMap<>();
        splits.put("train", train.size());
        splits.put("validation", val.size());
        splits.put("test", test.size());
        splits.put("total", train.size() + val.size() + test.size());
        metadata.put("splits", splits);
        
        // Distribution stats
        metadata.put("role_distribution", getDistribution(train, TrainingExample::getRole));
        metadata.put("difficulty_distribution", getDistribution(train, TrainingExample::getDifficulty));
        metadata.put("category_distribution", getDistribution(train, TrainingExample::getCategory));
        
        // Quality stats
        double avgQuality = train.stream()
            .mapToDouble(e -> e.getQualityScore() != null ? e.getQualityScore() : 0.0)
            .average()
            .orElse(0.0);
        metadata.put("avg_quality_score", avgQuality);
        
        Path metadataPath = exportDir.resolve("metadata.json");
        objectMapper.writerWithDefaultPrettyPrinter()
            .writeValue(metadataPath.toFile(), metadata);
        
        log.info("Exported metadata to {}", metadataPath.getFileName());
    }
    
    /**
     * Generate quality report
     */
    private void generateQualityReport(List<TrainingExample> train,
                                      List<TrainingExample> val,
                                      List<TrainingExample> test,
                                      Path exportDir) throws IOException {
        StringBuilder report = new StringBuilder();
        report.append("# Training Data Quality Report\n\n");
        report.append("Generated: ").append(LocalDate.now()).append("\n\n");
        
        report.append("## Dataset Statistics\n\n");
        report.append("| Split | Size |\n");
        report.append("|-------|------|\n");
        report.append("| Train | ").append(train.size()).append(" |\n");
        report.append("| Validation | ").append(val.size()).append(" |\n");
        report.append("| Test | ").append(test.size()).append(" |\n");
        report.append("| **Total** | **").append(train.size() + val.size() + test.size()).append("** |\n\n");
        
        report.append("## Quality Metrics\n\n");
        double avgQuality = train.stream()
            .mapToDouble(e -> e.getQualityScore() != null ? e.getQualityScore() : 0.0)
            .average()
            .orElse(0.0);
        report.append("- Average Quality Score: ").append(String.format("%.2f", avgQuality)).append("\n");
        
        long validatedCount = train.stream()
            .filter(e -> e.getValidationPass() != null && e.getValidationPass())
            .count();
        double validationRate = (double) validatedCount / train.size() * 100;
        report.append("- Validation Pass Rate: ").append(String.format("%.1f%%", validationRate)).append("\n\n");
        
        report.append("## Distribution Analysis\n\n");
        report.append("### By Role\n");
        appendDistribution(report, getDistribution(train, TrainingExample::getRole));
        
        report.append("\n### By Difficulty\n");
        appendDistribution(report, getDistribution(train, TrainingExample::getDifficulty));
        
        report.append("\n### By Category\n");
        appendDistribution(report, getDistribution(train, TrainingExample::getCategory));
        
        Path reportPath = exportDir.resolve("quality_report.md");
        Files.writeString(reportPath, report.toString());
        
        log.info("Generated quality report at {}", reportPath.getFileName());
    }
    
    /**
     * Get distribution of a field
     */
    private <T> Map<String, Long> getDistribution(List<TrainingExample> examples, 
                                                   java.util.function.Function<TrainingExample, T> extractor) {
        return examples.stream()
            .map(extractor)
            .filter(Objects::nonNull)
            .collect(Collectors.groupingBy(
                Object::toString,
                Collectors.counting()
            ));
    }
    
    /**
     * Append distribution to report
     */
    private void appendDistribution(StringBuilder report, Map<String, Long> distribution) {
        distribution.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .forEach(entry -> {
                report.append("- ").append(entry.getKey())
                      .append(": ").append(entry.getValue()).append("\n");
            });
    }
}
