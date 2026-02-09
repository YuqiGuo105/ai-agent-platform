package com.mrpot.agent.common.tool;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * File item with URL, metadata, and extracted understanding.
 * 
 * @param url source URL
 * @param filename extracted filename
 * @param mime MIME type
 * @param text extracted text
 * @param keywords extracted keywords
 * @param queries suggested search queries
 * @param error error message if extraction failed
 */
@Schema(description = "File item with metadata and extracted understanding")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FileItem(
    @Schema(description = "Source URL", example = "https://example.com/doc.pdf")
    String url,
    
    @Schema(description = "Extracted filename", example = "doc.pdf")
    String filename,
    
    @Schema(description = "MIME type", example = "application/pdf")
    String mime,
    
    @Schema(description = "Extracted text content", example = "This is the document content...")
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
