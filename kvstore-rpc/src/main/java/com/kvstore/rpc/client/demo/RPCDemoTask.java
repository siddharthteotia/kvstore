package com.kvstore.rpc.client.demo;

import com.kvstore.common.KVStoreClientRequest;
import com.kvstore.rpc.client.RPCClientConnectionProxy;
import org.apache.commons.lang3.RandomStringUtils;

/**
 * A task for the demo application. Sends a fixed
 * number of requests to the endpoint.
 */
public class RPCDemoTask implements Runnable {

  private volatile boolean cancel;
  private final RPCClientConnectionProxy proxy;

  RPCDemoTask(final RPCClientConnectionProxy proxy) {
    this.cancel = false;
    this.proxy = proxy;
  }

  @Override
  public void run() {
    int count = 0;
    while (!cancel && count < RPCDemo.NUM_REQUESTS_EACH_TYPE) {
      try {
        if (!proxy.isConnectionEstablished()) {
          System.out.println("Demo task: " + Thread.currentThread().getName() + " Waiting for active connection");
          Thread.sleep(RPCDemo.SLEEP_TIME);
        }
      } catch (InterruptedException ie) {
        System.out.println("Demo task thread got interrupted");
        ie.printStackTrace();
      }

      // put and get
      final String key = RandomStringUtils.randomAlphabetic(5);
      final String value = RandomStringUtils.randomAlphabetic(10);
      proxy.send(new KVStoreClientRequest(key, value));
      proxy.send(new KVStoreClientRequest(key, null));
      ++count;
    }
    final StringBuilder sb = new StringBuilder();
    sb.append("Demo task thread: ")
      .append(Thread.currentThread().getId())
      .append(" exiting after issuing ")
      .append(count)
      .append(" get and put requests");
    System.out.println(sb.toString());
  }

  void stop() {
    cancel = true;
  }
}
