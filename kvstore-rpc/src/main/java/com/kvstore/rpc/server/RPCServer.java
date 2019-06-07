package com.kvstore.rpc.server;

import com.kvstore.proto.KVStoreRPC;
import com.kvstore.server.inmemstore.KeyValueMap;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * The server side of RPC layer on an endpoint in the
 * KVStore cluster.
 */
public class RPCServer {

  private final Executor asyncWorkerPool;
  private final int port;
  private final KeyValueMap store;

  public RPCServer(final int port, final KeyValueMap store, final int numAsyncPoolThreads) {
    this.asyncWorkerPool = Executors.newFixedThreadPool(numAsyncPoolThreads);
    this.port = port;
    this.store = store;
  }

  public void start() throws Exception {

    /*
     * Two event loop groups are used:
     * (1) First group is responsible for accepting connections.
     *     For each such accepted connection, it creates a
     *     SocketChannel and assigns that channel to a particular
     *     event loop (single threaded) in another event loop group.
     * (2) This event loop in the second event loop group
     *     will be responsible for handling all the activity
     *     on this accepted connection (channel). A channel will be
     *     assigned to exactly one event loop but multiple channels
     *     can be handled by a single event loop
     */
    final EventLoopGroup connectionAcceptorGroup = new NioEventLoopGroup();
    final EventLoopGroup connectionChannelGroup = new NioEventLoopGroup();
    final ChannelInitializer<SocketChannel> channelInitializer =
      new ChannelInitializer<SocketChannel>() {
        @Override
        protected void initChannel(SocketChannel ch) throws Exception {
          // the bytes sent by the client over the connection to server
          // will be decoded into protobuf with appropriate handlers
          // in the channel pipeline
          final ChannelPipeline pipeline = ch.pipeline();
          // inbound handler to split the received ByteBuf and get the protobuf data
          pipeline.addLast(new ProtobufVarint32FrameDecoder());
          // inbound handler to decode data from ByteBuf into RPCRequest protobuf
          pipeline.addLast(new ProtobufDecoder(KVStoreRPC.RPCRequest.getDefaultInstance()));
          // outbound handler to prepend the length field to protobuf response from server
          pipeline.addLast(new ProtobufVarint32LengthFieldPrepender());
          // outbound handler to encode the protobuf response from server into bytes for client
          pipeline.addLast(new ProtobufEncoder());
          // inbound handler to parse the data in RPCRequest protobuf, handle the
          // request, create and send RPCResponse
          pipeline.addLast(new RPCRequestHandler());
        }
      };

    final ServerBootstrap serverBootstrap = new ServerBootstrap()
      .group(connectionAcceptorGroup, connectionChannelGroup)
      .channel(NioServerSocketChannel.class)
      .childHandler(channelInitializer);

    try {
      final Channel channel = serverBootstrap.bind(port).sync().channel();
      System.out.println("RPC services are up. Server listening for requests on port: " + port);
      Runtime.getRuntime().addShutdownHook(new Thread() {
        @Override
        public void run() {
          connectionAcceptorGroup.shutdownGracefully();
          connectionChannelGroup.shutdownGracefully();
          System.out.println("JVM shutting down, stopping the RPC server");
        }
      });
      channel.closeFuture().sync();
    } finally {
      connectionAcceptorGroup.shutdownGracefully();
      connectionChannelGroup.shutdownGracefully();
    }
  }

  /**
   * Inbound handler for handling the requests/data arriving
   * from RPC client endpoints in the cluster. The same instance of
   * channel handler is installed in the pipeline
   * of all {@link SocketChannel} opened for connections. Since
   * the handler internally works with a thread safe store API,
   * the handler is sharable.
   */
  @ChannelHandler.Sharable
  class RPCRequestHandler extends SimpleChannelInboundHandler<KVStoreRPC.RPCRequest> {
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, KVStoreRPC.RPCRequest rpcRequest) {
      handleRequest(ctx, rpcRequest);
    }
  }

  /**
   * Handles {@link com.kvstore.proto.KVStoreRPC.RPCRequest} by parsing
   * it and understanding the type of request (get/put). Talks to the
   * underlying inmem store to execute the request and forms an appropriate
   * {@link com.kvstore.proto.KVStoreRPC.RPCResponse}
   * @param rpcRequest request as protobuf from client
   *
   * Note: Since this method is invoked by the inbound handler,
   *       it implies that code here is executed by the event loop
   *       thread (aka netty I/O thread). For efficiency and throughput
   *       reasons, this code should not block and return asap to
   *       let the event loop thread handle the network I/O and the next
   *       set of event activity on the connection. So the actual handling
   *       of request is done in async manner by creating a
   *       {@link CompletableFuture} with the required async computation
   *       to process the incoming RPC request and a callback code to be
   *       invoked on the completion of CompletableFuture. The supplied
   *       async computation and callback is executed by a thread from
   *       async pool.
   *
   *       In the current implementation, request handling will require
   *       a lock on backend store to get/put the data and that is
   *       a strong reason why the request is not processed in
   *       event loop thread.
   */
  private void handleRequest(
    final ChannelHandlerContext ctx,
    final KVStoreRPC.RPCRequest rpcRequest) {
    final CompletableFuture<KVStoreRPC.RPCResponse> responseFuture;
    if (rpcRequest.hasGetRequest()) {
      System.out.println("Server received GET request. Request seq num: " + rpcRequest.getSequenceNum());
      responseFuture = CompletableFuture.supplyAsync(() -> {
        // async GET computation to be run in the worker pool thread
        final KVStoreRPC.GetRequest getRequest = rpcRequest.getGetRequest();
        final long sequenceNum = rpcRequest.getSequenceNum();
        final KeyValueMap.OpResult result = store.get(getRequest.getKey());
        final KVStoreRPC.GetResponse response =
          KVStoreRPC.GetResponse.newBuilder()
            .setFound(result.returnCode == KeyValueMap.ReturnCode.SUCCESS)
            .setKey(getRequest.getKey())
            .setValue(result.data)
            .build();
        return KVStoreRPC.RPCResponse
          .newBuilder()
          .setGetResponse(response)
          .setSequenceNum(sequenceNum)
          .build();
        }, asyncWorkerPool);
    } else {
      System.out.println("Server received PUT request. Request seq num: " + rpcRequest.getSequenceNum());
      responseFuture = CompletableFuture.supplyAsync(() -> {
        // async PUT computation to be run in the worker pool thread
        final KVStoreRPC.PutRequest putRequest = rpcRequest.getPutRequest();
        final long sequenceNum = rpcRequest.getSequenceNum();
        final KeyValueMap.OpResult result = store.put(putRequest.getKey(), putRequest.getValue());
        final KVStoreRPC.PutResponse response =
          KVStoreRPC.PutResponse.newBuilder()
            .setSuccess(result.returnCode == KeyValueMap.ReturnCode.SUCCESS)
            .setKey(putRequest.getKey())
            .setValue(putRequest.getValue())
            .build();
        return KVStoreRPC.RPCResponse
          .newBuilder()
          .setPutResponse(response)
          .setSequenceNum(sequenceNum)
          .build();
      }, asyncWorkerPool);
    }
    // callback code sends the RPCResponse back to client on
    // the completion of future
    responseFuture.thenAccept(response -> ctx.writeAndFlush(response));
  }
}
