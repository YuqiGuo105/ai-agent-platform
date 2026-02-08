package com.mrpot.agent.common.util;

import java.util.UUID;

public final class Ids {
  private Ids() {}

  public static String traceId() {
    return UUID.randomUUID().toString().replace("-", "");
  }
}
