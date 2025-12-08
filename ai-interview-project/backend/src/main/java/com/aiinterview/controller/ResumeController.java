package com.aiinterview.controller;

import com.aiinterview.model.ResumeSummaryRequest;
import com.aiinterview.service.AiService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/resume")
public class ResumeController {

    private final AiService aiService;

    public ResumeController(AiService aiService) {
        this.aiService = aiService;
    }

    @PostMapping("/summary")
    public String summarize(@RequestBody ResumeSummaryRequest request) {
        return aiService.generateResumeSummary(
                request.getResumeText(),
                request.getJobDescription()
        );
    }
}
