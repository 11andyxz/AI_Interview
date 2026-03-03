package com.aiinterview.ml.guardrails;

import com.aiinterview.model.openai.OpenAiMessage;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates repair prompts for the self-refinement pattern.
 * When LLM output fails schema validation, a repair prompt is constructed
 * that includes the original prompt, the malformed output, the specific
 * validation errors, and the expected schema — then sent back to the LLM
 * for correction.
 */
@Service
public class RepairPromptGenerator {

    public List<OpenAiMessage> generateRepairPrompt(List<OpenAiMessage> originalMessages,
                                                     String malformedOutput,
                                                     List<String> validationErrors,
                                                     String expectedSchema) {
        List<OpenAiMessage> repairMessages = new ArrayList<>(originalMessages);

        repairMessages.add(new OpenAiMessage("assistant", malformedOutput));

        StringBuilder repairInstruction = new StringBuilder();
        repairInstruction.append("Your previous response did not conform to the required JSON schema. ");
        repairInstruction.append("Please fix the following validation errors and return ONLY valid JSON.\n\n");

        repairInstruction.append("Validation errors:\n");
        for (String error : validationErrors) {
            repairInstruction.append("- ").append(error).append("\n");
        }

        if (expectedSchema != null && !expectedSchema.isEmpty()) {
            repairInstruction.append("\nExpected schema:\n").append(expectedSchema).append("\n");
        }

        repairInstruction.append("\nPlease return ONLY the corrected JSON object, with no additional text.");

        repairMessages.add(new OpenAiMessage("user", repairInstruction.toString()));

        return repairMessages;
    }
}
