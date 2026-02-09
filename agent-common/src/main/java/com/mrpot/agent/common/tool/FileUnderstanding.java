package com.mrpot.agent.common.tool;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Result from file understanding via Qwen VL.
 * 
 * @param text extracted/summarized text from the file
 * @param keywords extracted keywords
 * @param queries suggested search queries
 * @param error error message if extraction failed
 */
@Schema(description = "File understanding result from Qwen VL")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FileUnderstanding(
    @Schema(description = "Extracted/summarized text from file", example = "This document contains...")
    String text,
    
    @Schema(description = "Extracted keywords", example = "[\"keyword1\", \"keyword2\"]")
    List<String> keywords,
    
    @Schema(description = "Suggested search queries", example = "[\"query 1\", \"query 2\"]")
    List<String> queries,
    
    @Schema(description = "Error message if extraction failed", example = "timeout", nullable = true)
    String error
) {
  
  /**
   * Check if extraction was successful.
   */
  public boolean isSuccess() {
    return error == null || error.isBlank();
  }
}
