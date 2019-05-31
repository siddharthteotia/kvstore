package com.kvstore.common;

public class KVStoreClientRequest {

  private final String key;
  private final String value;

  public KVStoreClientRequest(final String key, final String value) {
    // right now we naively don't have null values for keys
    // so if value is null, we just assume it's a get request
    if (key == null || key.length() == 0) {
      throw new IllegalArgumentException("Invalid arguments for request");
    }
    this.key = key;
    this.value = value;
  }

  public String getKey() {
    return key;
  }

  public String getValue() {
    return value;
  }
}
