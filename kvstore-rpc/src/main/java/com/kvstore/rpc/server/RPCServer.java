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

public class RPCServer {

  private static final int NUM_WORKER_THREADS = 2;

  private final Executor workerPool;
  private final int port;
  private final KeyValueMap store;

  public RPCServer(final int port, final KeyValueMap store) {
    this.workerPool = Executors.newFixedThreadPool(NUM_WORKER_THREADS);
    this.port = port;
    this.store = store;
  }

  public void start() throws Exception {
    final EventLoopGroup acceptorGroup = new NioEventLoopGroup();
    final EventLoopGroup workerGroup = new NioEventLoopGroup();
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
      .group(acceptorGroup, workerGroup)
      .channel(NioServerSocketChannel.class)
      .childHandler(channelInitializer);

    try {
      final Channel channel = serverBootstrap.bind(port).sync().channel();
      System.out.println("RPC services are up. Server listening for requests on port: " + port);
      Runtime.getRuntime().addShutdownHook(new Thread() {
        @Override
        public void run() {
          acceptorGroup.shutdownGracefully();
          workerGroup.shutdownGracefully();
          System.out.println("JVM shutting down, stopping the RPC server");
        }
      });
      channel.closeFuture().sync();
    } finally {
      acceptorGroup.shutdownGracefully();
      workerGroup.shutdownGracefully();
    }
  }

  /**
   * The same instance of request handler is installed in the pipeline
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
   */
  private void handleRequest(
    final ChannelHandlerContext ctx,
    final KVStoreRPC.RPCRequest rpcRequest) {
    final CompletableFuture<KVStoreRPC.RPCResponse> responseFuture;
    if (rpcRequest.hasGetRequest()) {
      responseFuture = CompletableFuture.supplyAsync(() -> {
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
        }, workerPool);
    } else {
      responseFuture = CompletableFuture.supplyAsync(() -> {
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
      }, workerPool);
    }
    responseFuture.thenAccept(response -> ctx.writeAndFlush(response));
  }
}
