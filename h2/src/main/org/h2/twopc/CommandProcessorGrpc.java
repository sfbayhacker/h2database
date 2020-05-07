package org.h2.twopc;

import static io.grpc.MethodDescriptor.generateFullMethodName;
import static io.grpc.stub.ClientCalls.asyncBidiStreamingCall;
import static io.grpc.stub.ClientCalls.asyncClientStreamingCall;
import static io.grpc.stub.ClientCalls.asyncServerStreamingCall;
import static io.grpc.stub.ClientCalls.asyncUnaryCall;
import static io.grpc.stub.ClientCalls.blockingServerStreamingCall;
import static io.grpc.stub.ClientCalls.blockingUnaryCall;
import static io.grpc.stub.ClientCalls.futureUnaryCall;
import static io.grpc.stub.ServerCalls.asyncBidiStreamingCall;
import static io.grpc.stub.ServerCalls.asyncClientStreamingCall;
import static io.grpc.stub.ServerCalls.asyncServerStreamingCall;
import static io.grpc.stub.ServerCalls.asyncUnaryCall;
import static io.grpc.stub.ServerCalls.asyncUnimplementedStreamingCall;
import static io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall;

/**
 * <pre>
 * The 2pc command service definition.
 * </pre>
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.29.0)",
    comments = "Source: twopc.proto")
public final class CommandProcessorGrpc {

  private CommandProcessorGrpc() {}

  public static final String SERVICE_NAME = "twopc.CommandProcessor";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<org.h2.twopc.TwoPCRequest,
      org.h2.twopc.TwoPCResponse> getProcessCommandMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "processCommand",
      requestType = org.h2.twopc.TwoPCRequest.class,
      responseType = org.h2.twopc.TwoPCResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<org.h2.twopc.TwoPCRequest,
      org.h2.twopc.TwoPCResponse> getProcessCommandMethod() {
    io.grpc.MethodDescriptor<org.h2.twopc.TwoPCRequest, org.h2.twopc.TwoPCResponse> getProcessCommandMethod;
    if ((getProcessCommandMethod = CommandProcessorGrpc.getProcessCommandMethod) == null) {
      synchronized (CommandProcessorGrpc.class) {
        if ((getProcessCommandMethod = CommandProcessorGrpc.getProcessCommandMethod) == null) {
          CommandProcessorGrpc.getProcessCommandMethod = getProcessCommandMethod =
              io.grpc.MethodDescriptor.<org.h2.twopc.TwoPCRequest, org.h2.twopc.TwoPCResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "processCommand"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  org.h2.twopc.TwoPCRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  org.h2.twopc.TwoPCResponse.getDefaultInstance()))
              .setSchemaDescriptor(new CommandProcessorMethodDescriptorSupplier("processCommand"))
              .build();
        }
      }
    }
    return getProcessCommandMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static CommandProcessorStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<CommandProcessorStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<CommandProcessorStub>() {
        @java.lang.Override
        public CommandProcessorStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new CommandProcessorStub(channel, callOptions);
        }
      };
    return CommandProcessorStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static CommandProcessorBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<CommandProcessorBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<CommandProcessorBlockingStub>() {
        @java.lang.Override
        public CommandProcessorBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new CommandProcessorBlockingStub(channel, callOptions);
        }
      };
    return CommandProcessorBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static CommandProcessorFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<CommandProcessorFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<CommandProcessorFutureStub>() {
        @java.lang.Override
        public CommandProcessorFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new CommandProcessorFutureStub(channel, callOptions);
        }
      };
    return CommandProcessorFutureStub.newStub(factory, channel);
  }

  /**
   * <pre>
   * The 2pc command service definition.
   * </pre>
   */
  public static abstract class CommandProcessorImplBase implements io.grpc.BindableService {

    /**
     * <pre>
     * Sends a command
     * </pre>
     */
    public void processCommand(org.h2.twopc.TwoPCRequest request,
        io.grpc.stub.StreamObserver<org.h2.twopc.TwoPCResponse> responseObserver) {
      asyncUnimplementedUnaryCall(getProcessCommandMethod(), responseObserver);
    }

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
          .addMethod(
            getProcessCommandMethod(),
            asyncUnaryCall(
              new MethodHandlers<
                org.h2.twopc.TwoPCRequest,
                org.h2.twopc.TwoPCResponse>(
                  this, METHODID_PROCESS_COMMAND)))
          .build();
    }
  }

  /**
   * <pre>
   * The 2pc command service definition.
   * </pre>
   */
  public static final class CommandProcessorStub extends io.grpc.stub.AbstractAsyncStub<CommandProcessorStub> {
    private CommandProcessorStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected CommandProcessorStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new CommandProcessorStub(channel, callOptions);
    }

    /**
     * <pre>
     * Sends a command
     * </pre>
     */
    public void processCommand(org.h2.twopc.TwoPCRequest request,
        io.grpc.stub.StreamObserver<org.h2.twopc.TwoPCResponse> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(getProcessCommandMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * <pre>
   * The 2pc command service definition.
   * </pre>
   */
  public static final class CommandProcessorBlockingStub extends io.grpc.stub.AbstractBlockingStub<CommandProcessorBlockingStub> {
    private CommandProcessorBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected CommandProcessorBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new CommandProcessorBlockingStub(channel, callOptions);
    }

    /**
     * <pre>
     * Sends a command
     * </pre>
     */
    public org.h2.twopc.TwoPCResponse processCommand(org.h2.twopc.TwoPCRequest request) {
      return blockingUnaryCall(
          getChannel(), getProcessCommandMethod(), getCallOptions(), request);
    }
  }

  /**
   * <pre>
   * The 2pc command service definition.
   * </pre>
   */
  public static final class CommandProcessorFutureStub extends io.grpc.stub.AbstractFutureStub<CommandProcessorFutureStub> {
    private CommandProcessorFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected CommandProcessorFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new CommandProcessorFutureStub(channel, callOptions);
    }

    /**
     * <pre>
     * Sends a command
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<org.h2.twopc.TwoPCResponse> processCommand(
        org.h2.twopc.TwoPCRequest request) {
      return futureUnaryCall(
          getChannel().newCall(getProcessCommandMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_PROCESS_COMMAND = 0;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final CommandProcessorImplBase serviceImpl;
    private final int methodId;

    MethodHandlers(CommandProcessorImplBase serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_PROCESS_COMMAND:
          serviceImpl.processCommand((org.h2.twopc.TwoPCRequest) request,
              (io.grpc.stub.StreamObserver<org.h2.twopc.TwoPCResponse>) responseObserver);
          break;
        default:
          throw new AssertionError();
      }
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public io.grpc.stub.StreamObserver<Req> invoke(
        io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        default:
          throw new AssertionError();
      }
    }
  }

  private static abstract class CommandProcessorBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    CommandProcessorBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return org.h2.twopc.TwoPCProto.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("CommandProcessor");
    }
  }

  private static final class CommandProcessorFileDescriptorSupplier
      extends CommandProcessorBaseDescriptorSupplier {
    CommandProcessorFileDescriptorSupplier() {}
  }

  private static final class CommandProcessorMethodDescriptorSupplier
      extends CommandProcessorBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final String methodName;

    CommandProcessorMethodDescriptorSupplier(String methodName) {
      this.methodName = methodName;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.MethodDescriptor getMethodDescriptor() {
      return getServiceDescriptor().findMethodByName(methodName);
    }
  }

  private static volatile io.grpc.ServiceDescriptor serviceDescriptor;

  public static io.grpc.ServiceDescriptor getServiceDescriptor() {
    io.grpc.ServiceDescriptor result = serviceDescriptor;
    if (result == null) {
      synchronized (CommandProcessorGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new CommandProcessorFileDescriptorSupplier())
              .addMethod(getProcessCommandMethod())
              .build();
        }
      }
    }
    return result;
  }
}
