package com.kvstore.daemon;

import com.kvstore.common.Endpoint;
import com.kvstore.rpc.client.RPCClient;
import com.kvstore.rpc.client.demo.RPCDemo;
import com.kvstore.server.inmemstore.KeyValueMap;
import com.kvstore.rpc.server.RPCServer;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.net.InetAddress;
import java.net.URL;

/**
 * The main KVStore daemon process that is started on each
 * node in the cluster
 */
public class KVStoreDaemon {

  public static void main (String[] args) throws Exception {
    Config userConfig = ConfigFactory.load();

    final int serverRPCPort = userConfig.getInt(KVStoreConfig.RPC_SERVER_PORT);
    final int serverAsyncPoolThreads = userConfig.getInt(KVStoreConfig.SERVER_ASYNCPOOL_THREADS);

    String host = InetAddress.getLocalHost().getHostAddress();
    System.out.println("KVStore daemon started on node: " + host);

    // start RPC client for this endpoint
    RPCClient rpcClient = new RPCClient();
    rpcClient.start();

    final boolean rpcDemoEnabled = userConfig.getBoolean(KVStoreConfig.RPC_DEMO_ENABLED);
    final String demoServerEndpoint  = rpcDemoEnabled ?
      userConfig.getString(KVStoreConfig.RPC_DEMO_SERVER_ENDPOINT) : "";

    if (rpcDemoEnabled) {
      RPCDemo rpcDemoApp = new RPCDemo(rpcClient, new Endpoint(demoServerEndpoint, serverRPCPort));
      rpcDemoApp.start();
      System.out.println("Demo app also started on this node");
      System.out.println("For demo this node will act as client and talk to peer: " + demoServerEndpoint);
    }

    // start RPC server for this endpoint
    KeyValueMap store = new KeyValueMap();
    RPCServer rpcServer = new RPCServer(serverRPCPort, store, serverAsyncPoolThreads);
    rpcServer.start();
  }
}
