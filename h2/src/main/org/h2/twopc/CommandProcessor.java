package org.h2.twopc;

import org.h2.mvstore.tx.Record;

import io.grpc.stub.StreamObserver;

public class CommandProcessor extends CommandProcessorGrpc.CommandProcessorImplBase {

  @Override
  public void processCommand(TwoPCRequest request, StreamObserver<TwoPCResponse> responseObserver) {
    // TODO Auto-generated method stub

    String command = request.getCommand();
    
    System.out.println("Command       : " + command);
    
    TwoPCResponse.Builder response = TwoPCResponse.newBuilder();
    System.out.println("TID           : " + request.getTid());
    System.out.println("Received data : " + request.getData());
    
    switch (command.toLowerCase()) {
    case "log":
      try {
        if (!request.getData().isEmpty()) {
//          String xml = new String(request.getData().toByteArray(), "UTF-8");
//          Record logRecord = TwoPCUtils.fromXML(xml, Record.class);
          Record<?,?> logRecord = (Record<?, ?>)TwoPCUtils.deserialize(request.getData().toByteArray());
          System.out.println("Record        : " + logRecord);
        }
      } catch (Exception e) {
        // TODO Auto-generated catch block
        System.err.println("Unable to de-serialize log record: " + e.getMessage());
        e.printStackTrace();
      }
      response.setReply("OK");      
      break;
    case "prepare":
      response.setReply("OK");
      break;
    default:
      response.setReply("ABORT");
      break;
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
