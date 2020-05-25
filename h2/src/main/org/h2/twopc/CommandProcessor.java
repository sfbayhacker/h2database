package org.h2.twopc;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

  private Map<String, Session> sessionMap;
  
  private static class InstanceHolder {
    private static CommandProcessor INSTANCE = new CommandProcessor();
  }
  
  private CommandProcessor() {
    sessionMap = new ConcurrentHashMap<>();
  }
  
  public static CommandProcessor getInstance() {
    return InstanceHolder.INSTANCE;
  }
  
  @Override
  public void processCommand(TwoPCRequest request, StreamObserver<TwoPCResponse> responseObserver) {
    // TODO Auto-generated method stub

    TwoPCResponse.Builder response = TwoPCResponse.newBuilder();

    String command = request.getCommand();
    System.out.println(String.format("Command       : [%s]", command));
    String db = request.getDb();
    System.out.println(String.format("DB            : [%s]", db));
    String t = request.getTable();
    System.out.println(String.format("T             : [%s]", t));
    String sid = request.getSid();
    System.out.println(String.format("SID           : [%s]", sid));    
    long tid = request.getTid();
    System.out.println(String.format("TID           : [%d]", tid));
    int hid = request.getHid();
    System.out.println(String.format("HID           : [%d]", hid));
    
    System.out.println("Received data : " + request.getData());
    
    switch (command.toLowerCase()) {
      case "addrow":
      case "removerow": 
      case "updaterow": {
        boolean hasError = false;
        boolean result = false;
        try {
          List<Row> list = (List<Row>)TwoPCUtils.deserialize(request.getData().toByteArray());
//        Row row = list.get(0);
//        Row newRow = list.get(1);
          RowOp rowOp = new RowOp(list, command, sid);
          HTimestamp ts = new HTimestamp(hid, tid);
          result = DataManager.getInstance().prewrite(rowOp, ts);
//        rowOp(row, newRow, t, db, command, sid);
        } catch (ClassNotFoundException | IOException e) {
          System.err.println("Error while de-serializing row: " + e.getMessage());
          hasError = true;
        }
  
        if (hasError || !result) {
          response.setReply("ERROR");
        } else {
          response.setReply("OK");
        }
        
        break;
      }
      case "commit": {
//        commit(sid);
        DataManager.getInstance().commit(new HTimestamp(hid, tid));
        response.setReply("OK");
        break;
      }
      case "rollback": {
//        rollback(sid);
        DataManager.getInstance().rollback(new HTimestamp(hid, tid));
        response.setReply("OK");
        break;
      }
      case "prepare": {
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

  void rowOp(Row row, Row newRow, String t, String db, String op, String sid) {
    System.out.println(String.format("rowOp(%s, %s)", row.toString(), op));
    Session session = sessionMap.get(sid);
    if (session == null || session.isClosed()) {
      session = sessionMap.putIfAbsent(sid, createSession());
      if (session == null) {
        session = sessionMap.get(sid); 
      }
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
        ((MVTable) tables.get(0)).addRow(session, row, true, false);
    } else if (op.equalsIgnoreCase("removeRow")) {
        ((MVTable) tables.get(0)).removeRow(session, row, true, false);
    } else if (op.equalsIgnoreCase("updateRow")) {
        ((MVTable) tables.get(0)).updateRow(session, row, newRow, true, false);
    }
//    session.commit(false);
//    session.close();
  }

  void commit(String sid) {
    Session session = sessionMap.get(sid);
    if (session == null || session.isClosed()) {
      // Nothing to commit
      System.out.println("Nothing to commit!");
      return;
    }

    session.commit(false, true);
    session.close();
    sessionMap.remove(sid);
  }

  void rollback(String sid) {
    Session session = sessionMap.get(sid);
    if (session == null || session.isClosed()) {
      // Nothing to rollback
      System.out.println("Nothing to rollback!");
      return;
    }

    session.rollback(true);
    session.close();
    sessionMap.remove(sid);
  }

  private Session createSession() {
    // TODO: user to be part of message
    ConnectionInfo ci = new ConnectionInfo("~/test");
    ci.setUserName("SA");
    ci.setUserPasswordHash(SHA256.getKeyPasswordHash("SA", new char[0]));
    ci.setProperty("WRITE_DELAY", "0");
    return Engine.getInstance().createSession(ci);
  }

}
