package com.kvstore.daemon;

import com.kvstore.common.Endpoint;
import com.kvstore.rpc.client.RPCClient;
import com.kvstore.rpc.client.demo.RPCDemo;
import com.kvstore.server.inmemstore.KeyValueMap;
import com.kvstore.rpc.server.RPCServer;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.net.InetAddress;

/**
 * The main KVStore daemon process that is started on each
 * node in the cluster
 */
public class KVStoreDaemon {

  private static RPCClient rpcClient;
  private static RPCServer rpcServer;
  private static RPCDemo rpcDemoApp;

  public static void main (String[] args) throws Exception {
    final Config userConfig = ConfigFactory.load();

    final int serverRPCPort = userConfig.getInt(KVStoreConfig.RPC_SERVER_PORT);
    final int serverAsyncPoolThreads = userConfig.getInt(KVStoreConfig.SERVER_ASYNCPOOL_THREADS);

    try {
      // start RPC client for this endpoint
      rpcClient = new RPCClient();
      rpcClient.start();

      final boolean rpcDemoEnabled = userConfig.getBoolean(KVStoreConfig.RPC_DEMO_ENABLED);
      final String demoServerEndpoint  = rpcDemoEnabled ?
        userConfig.getString(KVStoreConfig.RPC_DEMO_SERVER_ENDPOINT) : "";

      if (rpcDemoEnabled) {
        // start demo app on this endpoint
        rpcDemoApp = new RPCDemo(rpcClient, new Endpoint(demoServerEndpoint, serverRPCPort));
        rpcDemoApp.start();
        System.out.println("Demo app also started on this node");
        System.out.println("For demo this node will act as client and talk to peer: " + demoServerEndpoint);
      }

      // start RPC server for this endpoint
      KeyValueMap store = new KeyValueMap();
      rpcServer = new RPCServer(serverRPCPort, store, serverAsyncPoolThreads);
      rpcServer.start();
    } catch (Exception e) {
      System.out.println("Failure while starting KVStore.");
      // revisit this for proper cleanup
      throw e;
    }

    final String host = InetAddress.getLocalHost().getHostAddress();
    System.out.println("KVStore daemon started on node: " + host);

    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        try {
          rpcServer.close();
          if (rpcDemoApp != null) {
            rpcDemoApp.close();
          }
          rpcClient.close();
        } catch (Exception e) {
          System.out.println("Failure while shutting down KVStore");
          e.printStackTrace();
        }
      }
    });
  }
}
