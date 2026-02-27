package com.aiinterview.ml.experiment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Assignment of a request to an experiment variant
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExperimentAssignment {
    
    /** Experiment ID */
    private String experimentId;
    
    /** Request ID */
    private String requestId;
    
    /** Assigned variant: baseline or variant */
    private String variant;
    
    /** Configuration for this variant (JSON) */
    private String config;
}
