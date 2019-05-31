package com.kvstore.common;

public class Endpoint {

  private final String address;
  private final int port;

  public Endpoint(final String address, final int port) {
    this.address = address;
    this.port = port;
  }

  public String getAddress() {
    return address;
  }

  public int getPort() {
    return port;
  }
}
