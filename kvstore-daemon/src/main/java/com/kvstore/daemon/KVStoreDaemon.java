package com.kvstore.daemon;

import com.kvstore.common.Endpoint;
import com.kvstore.rpc.client.RPCClient;
import com.kvstore.rpc.client.demo.RPCDemo;
import com.kvstore.server.inmemstore.KeyValueMap;
import com.kvstore.rpc.server.RPCServer;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.net.InetAddress;

public class KVStoreDaemon {

  public static void main (String[] args) throws Exception {

    Config config = ConfigFactory.load();

    final boolean rpcDemoEnabled = config.getBoolean(KVStoreConfig.RPC_DEMO_ENABLED);
    final String demoServerEndpoint  = rpcDemoEnabled ? config.getString(KVStoreConfig.RPC_DEMO_SERVER_ENDPOINT) : "";
    final int serverRPCPort = config.getInt(KVStoreConfig.RPC_SERVER_PORT);

    // start RPC client for this endpoint
    RPCClient rpcClient = new RPCClient();
    rpcClient.start();

    // start RPC server for this endpoint
    KeyValueMap store = new KeyValueMap();
    RPCServer rpcServer = new RPCServer(serverRPCPort, store);
    rpcServer.start();

    if (rpcDemoEnabled) {
      RPCDemo rpcDemoApp = new RPCDemo(rpcClient, new Endpoint(demoServerEndpoint, serverRPCPort));
      rpcDemoApp.start();
      String host = InetAddress.getLocalHost().getHostAddress();
      System.out.println("KVStore daemon started as demo RPC server on node: " + host + " at port: " + serverRPCPort);
    }
  }
}
