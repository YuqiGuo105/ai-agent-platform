package com.mrpot.agent.common.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public final class Json {
  private Json() {}

  public static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
}
