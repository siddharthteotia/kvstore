package com.kvstore.rpc.client;

import com.kvstore.common.Endpoint;
import com.kvstore.common.KVStoreClientRequest;
import com.kvstore.proto.KVStoreRPC;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;

/**
 * Creates and holds the RPC connection to an endpoint
 * (remote or local).
 */
public class RPCClientConnectionProxy implements AutoCloseable {

  private final RPCClient rpcClient;
  private final Endpoint endpoint;
  private Channel channel;

  public RPCClientConnectionProxy (final RPCClient rpcClient, final Endpoint endpoint) {
    this.rpcClient = rpcClient;
    this.endpoint = endpoint;
    establishConnection();
  }

  /**
   * Connect to an endpoint (remote or local)
   */
  private void establishConnection() {
    final ChannelFuture future = rpcClient.connectToPeer(endpoint);
    future.addListener(new ChannelFutureListener() {
      @Override
      public void operationComplete(ChannelFuture future) throws Exception {
        channel = future.channel();
        if (future.isSuccess()) {
          System.out.println("Connected to peer: " + channel.remoteAddress());
        } else {
          System.out.println("Unable to establish connection with peer: " + endpoint.getAddress());
          channel.close().sync();
        }
      }
    });
  }

  /**
   * Checks if the connection has been established or not
   * @return true if connection has been established, false otherwise
   */
  public boolean isConnectionEstablished() {
    return channel != null && channel.isActive();
  }

  /**
   * Used to send RPC request. Builds the {@link com.kvstore.proto.KVStoreRPC.RPCRequest}
   * and sends it over the wire
   * @param request get/put request info encapsulated in {@link KVStoreClientRequest}
   */
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

  @Override
  public void close() throws Exception {
    try {
      System.out.println("ConnectionProxy: Closing channel to peer " + channel.remoteAddress());
      channel.closeFuture().sync();
    } catch (Exception e) {
      System.out.println("Failure while shutting down connection proxy");
      throw e;
    }
  }
}
