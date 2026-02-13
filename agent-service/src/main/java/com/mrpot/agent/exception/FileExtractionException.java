package com.mrpot.agent.exception;

public class FileExtractionException extends RuntimeException {

  private final String url;

  public FileExtractionException(String url, String message) {
    super(message);
    this.url = url;
  }

  public FileExtractionException(String url, String message, Throwable cause) {
    super(message, cause);
    this.url = url;
  }

  public String getUrl() {
    return url;
  }
}
