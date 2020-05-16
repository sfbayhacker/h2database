package org.h2.twopc;

import java.io.IOException;
import java.util.List;

import org.h2.engine.Database;
import org.h2.engine.Engine;
import org.h2.mvstore.db.MVTable;
import org.h2.mvstore.tx.Record;
import org.h2.mvstore.tx.Transaction;
import org.h2.mvstore.tx.TransactionStore;
import org.h2.result.Row;
import org.h2.table.Table;

import io.grpc.stub.StreamObserver;

public class CommandProcessor extends CommandProcessorGrpc.CommandProcessorImplBase {

  @Override
  public void processCommand(TwoPCRequest request, StreamObserver<TwoPCResponse> responseObserver) {
    // TODO Auto-generated method stub

    String command = request.getCommand();
    
    System.out.println("Command       : " + command);
    
    TwoPCResponse.Builder response = TwoPCResponse.newBuilder();
    
    switch (command.toLowerCase()) {
    case "log":
      try {
        if (!request.getData().isEmpty()) {
//          String xml = new String(request.getData().toByteArray(), "UTF-8");
//          Record logRecord = TwoPCUtils.fromXML(xml, Record.class);
          String dbtx = request.getTid();
          String db = dbtx==null?"":dbtx.substring(0, dbtx.indexOf('-'));
          String tid = dbtx==null?"":dbtx.substring(dbtx.indexOf('-') + 1);
          System.out.println("DB            : " + db);
          System.out.println("TID           : " + tid);
          System.out.println("Received data : " + request.getData());
          
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
    case "addrow":
      //sending dbname-tablename for now as tid
      String dbtx = request.getTid();
      
      String db = dbtx==null?"":dbtx.substring(0, dbtx.indexOf('-'));
      String t = dbtx==null?"":dbtx.substring(dbtx.indexOf('-') + 1);
      System.out.println("DB            : " + db);
      System.out.println("T             : " + t);
      System.out.println("Received data : " + request.getData());
      
      try {
        Row row = (Row)TwoPCUtils.deserialize(request.getData().toByteArray());
        addRow(row, t, db);
      } catch (ClassNotFoundException | IOException e) {
        System.err.println("Error while de-serializing row: " + e.getMessage());
      }
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
 
  private void addRow(Row row, String t, String db) {
    Database d = Engine.getInstance().getDatabase(db);
    
    if (d == null) {
      System.err.println("Database " + db + " not found. Aborting addRow operation!");
      return;
    }
    
    List<Table> table = d.getTableOrViewByName(t);
    
    if (table == null || t.isEmpty()) {
      System.err.println("Table " + t + " not found. Aborting addRow operation!");
    }
    
    ((MVTable)table).addRow(d.getSystemSession(), row);
  }
  
  private void log(String dbName, String tid, Record<?,?> logRecord) {
	  System.out.println("engine: " + Engine.getInstance());
	  System.out.println("db: " + Engine.getInstance().getDatabase(dbName));
	  System.out.println("store: " + Engine.getInstance().getDatabase(dbName).getStore());
	  System.out.println("ts: " + Engine.getInstance().getDatabase(dbName).getStore().getTransactionStore());
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
