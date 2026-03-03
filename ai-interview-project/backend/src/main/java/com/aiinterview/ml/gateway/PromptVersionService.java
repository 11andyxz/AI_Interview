package com.aiinterview.ml.gateway;

import com.aiinterview.ml.gateway.model.PromptVersion;
import com.aiinterview.ml.gateway.repository.PromptVersionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class PromptVersionService {

    private static final Logger logger = LoggerFactory.getLogger(PromptVersionService.class);

    private final PromptVersionRepository promptVersionRepository;

    public PromptVersionService(PromptVersionRepository promptVersionRepository) {
        this.promptVersionRepository = promptVersionRepository;
    }

    public Optional<PromptVersion> getActivePrompt(String promptKey) {
        return promptVersionRepository.findByPromptKeyAndIsActiveTrue(promptKey);
    }

    public Optional<PromptVersion> getPromptByVersion(String promptKey, String version) {
        return promptVersionRepository.findByPromptKeyAndVersion(promptKey, version);
    }

    /**
     * Resolve prompt for an experiment variant.
     * If the experiment config specifies a prompt version, use it; otherwise fall back to active prompt.
     */
    public Optional<PromptVersion> getPromptForExperiment(String promptKey,
                                                           String promptVersion) {
        if (promptVersion != null) {
            return getPromptByVersion(promptKey, promptVersion);
        }
        return getActivePrompt(promptKey);
    }

    @Transactional
    public PromptVersion createVersion(String promptKey, String version,
                                        String content, String description) {
        PromptVersion pv = new PromptVersion();
        pv.setPromptKey(promptKey);
        pv.setVersion(version);
        pv.setContent(content);
        pv.setDescription(description);
        pv.setActive(false);
        return promptVersionRepository.save(pv);
    }

    @Transactional
    public PromptVersion activateVersion(String promptKey, String version) {
        promptVersionRepository.findByPromptKeyAndIsActiveTrue(promptKey)
                .ifPresent(current -> {
                    current.setActive(false);
                    promptVersionRepository.save(current);
                });

        PromptVersion target = promptVersionRepository.findByPromptKeyAndVersion(promptKey, version)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Prompt version not found: " + promptKey + " " + version));
        target.setActive(true);
        return promptVersionRepository.save(target);
    }

    public List<PromptVersion> getVersionHistory(String promptKey) {
        return promptVersionRepository.findByPromptKeyOrderByCreatedAtDesc(promptKey);
    }
}
