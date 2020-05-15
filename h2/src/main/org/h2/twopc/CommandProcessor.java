package org.h2.twopc;

import org.h2.engine.Engine;
import org.h2.mvstore.tx.Record;
import org.h2.mvstore.tx.Transaction;
import org.h2.mvstore.tx.TransactionStore;
import org.h2.util.StringUtils;

import io.grpc.stub.StreamObserver;

public class CommandProcessor extends CommandProcessorGrpc.CommandProcessorImplBase {

  @Override
  public void processCommand(TwoPCRequest request, StreamObserver<TwoPCResponse> responseObserver) {
    // TODO Auto-generated method stub

    String command = request.getCommand();
    
    System.out.println("Command       : " + command);
    
    TwoPCResponse.Builder response = TwoPCResponse.newBuilder();
    
	String dbtx = request.getTid();
	String db = dbtx.substring(0, dbtx.indexOf('-'));
	String tid = dbtx.substring(dbtx.indexOf('-') + 1);
	System.out.println("DB            : " + db);
    System.out.println("TID           : " + tid);
    System.out.println("Received data : " + request.getData());
    
    switch (command.toLowerCase()) {
    case "log":
      try {
        if (!request.getData().isEmpty()) {
//          String xml = new String(request.getData().toByteArray(), "UTF-8");
//          Record logRecord = TwoPCUtils.fromXML(xml, Record.class);
          Record<?,?> logRecord = (Record<?, ?>)TwoPCUtils.deserialize(request.getData().toByteArray());
          System.out.println("Record        : " + logRecord);
          log(db, tid, logRecord);
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
 
  private void log(String dbName, String tid, Record<?,?> logRecord) {
  	TransactionStore ts = Engine.getInstance().getDatabase(dbName).getStore().getTransactionStore();
  
  	int txId = Integer.parseInt(tid);
  
  	Transaction t = ts.getTransaction(txId);
  	if (t == null) {
  		t = ts.begin(txId);
  	}
  
  	long l = t.log(logRecord);
  	System.out.println("result: " + l);
  	
  	t.commit();
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
