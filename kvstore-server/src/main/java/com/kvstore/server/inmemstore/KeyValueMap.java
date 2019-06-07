package com.kvstore.server.inmemstore;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A simple wrapper over hash table to mimic
 * an inmemory backend store
 */
public class KeyValueMap {

  private final Map<String, String> map;

  public KeyValueMap () {
    this.map = new ConcurrentHashMap<>();
  }

  public OpResult get(String key) {
    ReturnCode code;
    final String value = map.get(key);
    if (value == null) {
      code = ReturnCode.KEY_DOES_NOT_EXIST;
    } else {
      code = ReturnCode.SUCCESS;
    }

    return new OpResult(code, value);
  }

  public OpResult put(String key, String value) {
    map.put(key, value);
    return new OpResult(ReturnCode.SUCCESS, "");
  }

  public static class OpResult {
    public final ReturnCode returnCode;
    public final String data;

    OpResult(final ReturnCode returnCode, final String data) {
      this.returnCode = returnCode;
      this.data = data;
    }
  }

  public enum ReturnCode {
    SUCCESS,
    KEY_DOES_NOT_EXIST
  }
}
