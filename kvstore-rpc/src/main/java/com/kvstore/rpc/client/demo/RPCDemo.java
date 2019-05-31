package com.kvstore.rpc.client.demo;

import com.kvstore.common.Endpoint;
import com.kvstore.rpc.client.RPCClient;
import com.kvstore.rpc.client.RPCClientProxy;

public class RPCDemo {

  static final int SLEEP_TIME = 5000;
  private final RPCClient rpcClient;
  private final Endpoint peer;

  public RPCDemo(final RPCClient rpcClient, final Endpoint peer) {
    this.rpcClient = rpcClient;
    this.peer = peer;
  }

  public void start() {
    final RPCClientProxy proxy = new RPCClientProxy(rpcClient, peer);
    Thread task1 = new Thread(new RPCDemoTask(proxy));
    Thread task2 = new Thread(new RPCDemoTask(proxy));
    task1.start();
    task2.start();
  }
}
