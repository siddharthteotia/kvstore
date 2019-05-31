package com.kvstore.rpc.client;

import com.kvstore.proto.KVStoreRPC;

public class RPCResponseListener {

  void done(KVStoreRPC.RPCResponse response) {
    if (response.hasGetResponse()) {
      KVStoreRPC.GetResponse getResponse = response.getGetResponse();
      System.out.println("RPC Response received for GET request: " + response.getSequenceNum());
      if (getResponse.getFound()) {
        System.out.println("Key: " + getResponse.getKey() + " Value: " + getResponse.getValue());
      } else {
        System.out.println("Key: " + getResponse.getKey() + " Value: DOES NOT EXIST");
      }
    } else {
      KVStoreRPC.PutResponse putResponse = response.getPutResponse();
      System.out.println("RPC Response received for PUT request: " + response.getSequenceNum());
      if (putResponse.getSuccess()) {
        System.out.println("Key: " + putResponse.getKey() + " Value: " + putResponse.getValue() + " stored in server");
      } else {
        System.out.println("Key: " + putResponse.getKey() + " Value: " + putResponse.getValue() + " failed to store in server");
      }
    }
  }
}
