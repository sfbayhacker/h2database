package org.h2.twopc;

import io.grpc.stub.StreamObserver;

public class CommandProcessor extends CommandProcessorGrpc.CommandProcessorImplBase {

  @Override
  public void processCommand(TwoPCRequest request, StreamObserver<TwoPCResponse> responseObserver) {
    // TODO Auto-generated method stub

    String command = request.getCommand();
    
    TwoPCResponse.Builder response = TwoPCResponse.newBuilder();
    
    if ("prepare".equalsIgnoreCase(command)) {
      response.setReply("OK");
    } else {
      response.setReply("ABORT");
    }
    
    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }
  
}

//import static io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall;
//
//public class CommandService implements CommandGrpc.CommandImplBase {
//  public void sendCommand(org.h2.twopc.TwoPCRequest request,
//      io.grpc.stub.StreamObserver<org.h2.twopc.TwoPCResponse> responseObserver) {
//    
//  }
//}
