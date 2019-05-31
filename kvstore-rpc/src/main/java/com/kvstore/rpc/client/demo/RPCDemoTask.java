package com.kvstore.rpc.client.demo;

import com.kvstore.common.KVStoreClientRequest;
import com.kvstore.rpc.client.RPCClientProxy;
import org.apache.commons.lang3.RandomStringUtils;

public class RPCDemoTask implements Runnable {

  private final RPCClientProxy proxy;

  RPCDemoTask(final RPCClientProxy proxy) {
    this.proxy = proxy;
  }

  @Override
  public void run() {
    int count = 50;
    while (count >= 0) {
      try {
        if (!proxy.isConnectionActive()) {
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
      --count;
    }
  }
}
