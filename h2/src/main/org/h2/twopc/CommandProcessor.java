package org.h2.twopc;

import java.io.IOException;
import java.util.List;

import org.h2.engine.ConnectionInfo;
import org.h2.engine.Database;
import org.h2.engine.Engine;
import org.h2.engine.Session;
import org.h2.mvstore.db.MVTable;
import org.h2.mvstore.tx.Record;
import org.h2.mvstore.tx.Transaction;
import org.h2.mvstore.tx.TransactionStore;
import org.h2.result.Row;
import org.h2.security.SHA256;
import org.h2.table.Table;

import io.grpc.stub.StreamObserver;

public class CommandProcessor extends CommandProcessorGrpc.CommandProcessorImplBase {

  private Session session;

  private static class InstanceHolder {
    private static CommandProcessor INSTANCE = new CommandProcessor();
  }
  
  private CommandProcessor() {}
  
  public static CommandProcessor getInstance() {
    return InstanceHolder.INSTANCE;
  }
  
  @Override
  public void processCommand(TwoPCRequest request, StreamObserver<TwoPCResponse> responseObserver) {
    // TODO Auto-generated method stub
    String command = request.getCommand();
    System.out.println("Command       : " + command);
    TwoPCResponse.Builder response = TwoPCResponse.newBuilder();

    switch (command.toLowerCase()) {
      case "addrow":
      case "removerow": 
      case "updaterow": {
        // sending dbname-tablename for now as tid
        String dbtx = request.getTid();
  
        String db = dbtx == null ? "" : dbtx.substring(0, dbtx.indexOf('-'));
        String t = dbtx == null ? "" : dbtx.substring(dbtx.indexOf('-') + 1);
        System.out.println("DB            : " + db);
        System.out.println("T             : " + t);
        System.out.println("Received data : " + request.getData());
  
        boolean hasError = false;
  
        try {
	  List<Row> list = (List<Row>)TwoPCUtils.deserialize(request.getData().toByteArray());
          Row row = list.get(0);
          Row newRow = list.get(1);
          rowOp(row, newRow, t, db, command);
        } catch (ClassNotFoundException | IOException e) {
          System.err.println("Error while de-serializing row: " + e.getMessage());
          hasError = true;
        }
  
        if (hasError) {
          response.setReply("ERROR");
        } else {
          response.setReply("OK");
        }
        break;
      }
      case "commit": {
        commit();
        response.setReply("OK");
        break;
      }
      case "rollback": {
        rollback();
        response.setReply("OK");
        break;
      }
      case "prepare": {
        response.setReply("OK");
        break;
      }
      case "log": {
        try {
          if (!request.getData().isEmpty()) {
  //          String xml = new String(request.getData().toByteArray(), "UTF-8");
  //          Record logRecord = TwoPCUtils.fromXML(xml, Record.class);
            String dbtx = request.getTid();
            String db = dbtx == null ? "" : dbtx.substring(0, dbtx.indexOf('-'));
            String tid = dbtx == null ? "" : dbtx.substring(dbtx.indexOf('-') + 1);
            System.out.println("DB            : " + db);
            System.out.println("TID           : " + tid);
            System.out.println("Received data : " + request.getData());
  
            Record<?, ?> logRecord = (Record<?, ?>) TwoPCUtils.deserialize(request.getData().toByteArray());
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
      }
      default: {
        response.setReply("ABORT");
        break;
      }
    }

    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }

  private void rowOp(Row row, Row newRow,  String t, String db, String op) {
    if (session == null || session.isClosed()) {
      session = createSession();
    }

    Database d = session.getDatabase();
    if (d == null) {
      System.err.println("Database " + db + " not found. Aborting " + op + " operation!");
      return;
    }

    List<Table> tables = d.getTableOrViewByName("MAP");
    System.out.println("tables: " + tables);
    if (tables == null || tables.isEmpty()) {
      System.err.println("Table " + t + " not found. Aborting " + op + " operation!");
    }

    if (op.equalsIgnoreCase("addRow")) {
        ((MVTable) tables.get(0)).addRow(session, row, true);
    } else if (op.equalsIgnoreCase("removeRow")) {
        ((MVTable) tables.get(0)).removeRow(session, row, true);
    } else if (op.equalsIgnoreCase("updateRow")) {
        ((MVTable) tables.get(0)).updateRow(session, row, newRow, true);
    }
//    session.commit(false);
//    session.close();
  }

  private void commit() {
    if (session == null || session.isClosed()) {
      // Nothing to commit
      System.out.println("Nothing to commit!");
      return;
    }

    session.commit(false, true);
    session.close();
  }

  private void rollback() {
    if (session == null || session.isClosed()) {
      // Nothing to rollback
      System.out.println("Nothing to rollback!");
      return;
    }

    session.rollback(true);
    session.close();
  }

  private static Session createSession() {
    // TODO: user to be part of message
    ConnectionInfo ci = new ConnectionInfo("~/test");
    ci.setUserName("SA");
    ci.setUserPasswordHash(SHA256.getKeyPasswordHash("SA", new char[0]));
    ci.setProperty("WRITE_DELAY", "0");
    return Engine.getInstance().createSession(ci);
  }

  private void log(String dbName, String tid, Record<?, ?> logRecord) {
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
