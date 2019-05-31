package com.kvstore.rpc.client;

import com.kvstore.common.Endpoint;
import com.kvstore.common.KVStoreClientRequest;
import com.kvstore.proto.KVStoreRPC;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;

public class RPCClientProxy {

  private final RPCClient rpcClient;
  private final Endpoint endpoint;
  private boolean isConnectionActive;
  private Channel channel;

  public RPCClientProxy (final RPCClient rpcClient, final Endpoint endpoint) {
    this.rpcClient = rpcClient;
    this.endpoint = endpoint;
    this.isConnectionActive = false;
    establishConnection();
  }

  private void establishConnection() {
    final ChannelFuture future = rpcClient.connectToPeer(endpoint);
    future.addListener(new ChannelFutureListener() {
      @Override
      public void operationComplete(ChannelFuture future) throws Exception {
        if (future.isSuccess()) {
          isConnectionActive = true;
          channel = future.channel();
        } else {
          System.out.println("Unable to establish connection with peer: " + endpoint.getAddress());
        }
      }
    });
  }

  public boolean isConnectionActive() {
    return isConnectionActive;
  }

  public void send(final KVStoreClientRequest request) {
    final long sequenceNum = rpcClient.getNextSequenceNum();
    KVStoreRPC.RPCRequest rpcRequest;
    if (request.getValue() == null) {
      KVStoreRPC.GetRequest getRequest = KVStoreRPC.GetRequest
        .newBuilder()
        .setKey(request.getKey())
        .build();
       rpcRequest = KVStoreRPC.RPCRequest
        .newBuilder()
        .setGetRequest(getRequest)
         .setSequenceNum(sequenceNum)
        .build();
    } else {
      KVStoreRPC.PutRequest putRequest = KVStoreRPC.PutRequest
        .newBuilder()
        .setKey(request.getKey())
        .setValue(request.getValue())
        .build();
      rpcRequest = KVStoreRPC.RPCRequest
        .newBuilder()
        .setPutRequest(putRequest)
        .setSequenceNum(sequenceNum)
        .build();
    }

    final ChannelFuture sendFuture = channel.writeAndFlush(rpcRequest);
    sendFuture.addListener(new ChannelFutureListener() {
      @Override
      public void operationComplete(ChannelFuture future) throws Exception {
        if (future.isSuccess()) {
          System.out.println("Request# " + sequenceNum + " sent successfully to " + future.channel().remoteAddress());
        } else {
          System.out.println("Unable to send request# " + sequenceNum);
        }
      }
    });
  }
}
