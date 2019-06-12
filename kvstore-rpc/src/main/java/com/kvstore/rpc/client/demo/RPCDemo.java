package com.kvstore.rpc.client.demo;

import com.kvstore.common.Endpoint;
import com.kvstore.rpc.client.RPCClient;
import com.kvstore.rpc.client.RPCClientConnectionProxy;

/**
 * A simple demo application to demonstrate the use of RPC
 * layer. The demo application talks to an endpoint
 * The demo application creates 2 threads
 * {@link RPCDemoTask} and each thread talks to the same
 * endpoint.
 *
 * First we create a connection to the endpoint
 * via {@link RPCClientConnectionProxy} and pass
 * the connection proxy to the tasks for executing
 * the request-response behavior.
 *
 * Since {@link RPCClientConnectionProxy} represents a
 * single physical connection/channel to an endpoint,
 * both demo tasks send data concurrently (or in arbitrary
 * unpredictable order) on the channel.
 */
public class RPCDemo implements AutoCloseable {
  static final int SLEEP_TIME = 5000;
  private final RPCDemoTask task1;
  private final RPCDemoTask task2;
  private final RPCClientConnectionProxy connectionProxy;
  static final int NUM_REQUESTS_EACH_TYPE = 50;

  public RPCDemo(final RPCClient rpcClient, final Endpoint peer) {
    connectionProxy = new RPCClientConnectionProxy(rpcClient, peer);
    task1 = new RPCDemoTask(connectionProxy);
    task2 = new RPCDemoTask(connectionProxy);
  }

  public void start() {
    new Thread(task1).start();
    new Thread(task2).start();
    System.out.println("Started Demo tasks");
  }

  @Override
  public void close() throws Exception {
    System.out.println("Shutting down demo tasks");
    task1.stop();
    task2.stop();
    System.out.println("Shutting down connection proxy");
    connectionProxy.close();
  }
}
