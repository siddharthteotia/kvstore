package com.kvstore.rpc.client;

import com.kvstore.common.Endpoint;
import com.kvstore.proto.KVStoreRPC;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The client side of RPC layer on an endpoint in
 * the KVStore cluster.
 */
public class RPCClient {

  private final AtomicLong sequenceNum;
  private Bootstrap bootstrap;
  private final Map<Long, RPCResponseListener> responseListeners;

  public RPCClient() {
    this.sequenceNum = new AtomicLong(0);
    this.bootstrap = new Bootstrap();
    this.responseListeners = new ConcurrentHashMap<>();
  }

  public void start() {
    final EventLoopGroup eventLoopGroup = new NioEventLoopGroup();
    final ChannelInitializer<SocketChannel> channelInitializer =
      new ChannelInitializer<SocketChannel>() {
        @Override
        protected void initChannel(SocketChannel ch) throws Exception {
          final ChannelPipeline pipeline = ch.pipeline();
          // inbound handler to split the received ByteBuf and get the protobuf data
          pipeline.addLast(new ProtobufVarint32FrameDecoder());
          // inbound handler to decode data from ByteBuf into RPCResponse protobuf
          pipeline.addLast(new ProtobufDecoder(KVStoreRPC.RPCResponse.getDefaultInstance()));
          // outbound handler to prepend the length field to protobuf message from client
          pipeline.addLast(new ProtobufVarint32LengthFieldPrepender());
          // outbound handler to encode the protobuf message from client into bytes for server
          pipeline.addLast(new ProtobufEncoder());
          // inbound handler to parse the data in RPCResponse protobuf, handle the
          // request, create and send RPCResponse
          pipeline.addLast(new RPCResponseHandler());
        }
      };

    bootstrap
      .group(eventLoopGroup)
      .channel(NioSocketChannel.class)
      .handler(channelInitializer);
  }

  /**
   * Inbound handler for responses/data arriving from RPC server
   * endpoints in the cluster.
   */
  class RPCResponseHandler extends SimpleChannelInboundHandler<KVStoreRPC.RPCResponse> {
    @Override
    public void channelRead0(ChannelHandlerContext context, KVStoreRPC.RPCResponse rpcResponse) {
      final long sequenceNum = rpcResponse.getSequenceNum();
      final RPCResponseListener listener = responseListeners.remove(sequenceNum);
      // right now just complete it in the network thread -- event loop thread
      listener.done(rpcResponse);
    }
  }

  /**
   * Establish connection with an endpoint
   * @param endpoint {@link Endpoint} representing a peer with host address/port
   * @return {@link ChannelFuture}
   */
  ChannelFuture connectToPeer(Endpoint endpoint) {
    return bootstrap.connect(endpoint.getAddress(), endpoint.getPort());
  }

  /**
   * For each request sent on the connection, we track the
   * sequence number along with corresponding response listener
   * which gets notified when the response for a particular
   * request arrives.
   * @return sequence number to be used for next request
   */
  long getNextSequenceNum() {
    final long seq = sequenceNum.incrementAndGet();
    responseListeners.put(seq, new RPCResponseListener());
    return seq;
  }
}
