package org.h2.twopc;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.h2.engine.ConnectionInfo;
import org.h2.engine.Database;
import org.h2.engine.Engine;
import org.h2.engine.Session;
import org.h2.mvstore.db.MVTable;
import org.h2.result.Row;
import org.h2.security.SHA256;
import org.h2.table.Table;
import org.h2.twopc.LogManager.LogEntry;

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
        List<Row> list = (List<Row>) TwoPCUtils.deserialize(request.getData().toByteArray());
//        Row row = list.get(0);
//        Row newRow = list.get(1);
        RowOp rowOp = new RowOp(list, command, sid, null);
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
    case "prepare": {
    //    boolean result = LogManager.getInstance().appendLogEntry(""+0+"#"+System.currentTimeMillis(), "prepare");
      try {
        LogManager.getInstance().flushState();
      } catch (IOException e) {
        System.err.println("Error while flushing state: " + e.getMessage());
        response.setReply("ABORT");
        break;
      }
      boolean result = LogManager.getInstance().appendLogEntry("" + hid + "#" + tid, "prepare");
      response.setReply(result ? "OK" : "ABORT");
      break;
    }
    case "commit": {
//        commit(sid);
      boolean result = LogManager.getInstance().appendLogEntry("" + hid + "#" + tid, "commit");
      if (!result) {
        response.setReply("ABORT");
        break;
      }
      long start = System.currentTimeMillis();
      System.out.println("*** DataManager.getInstance().commit 1 at:" + (System.currentTimeMillis() - start));
      start = System.currentTimeMillis();
      DataManager.getInstance().commit(sid, null, new HTimestamp(hid, tid), false);
      response.setReply("OK");
      System.out.println("*** DataManager.getInstance().commit 2 at:" + (System.currentTimeMillis() - start));
      start = System.currentTimeMillis();
      break;
    }
    case "rollback": {
//        rollback(sid);
      DataManager.getInstance().rollback(sid, null, new HTimestamp(hid, tid));
      response.setReply("OK");
      break;
    }
    case "txnstatus": {
      if (LogManager.getInstance().logEntries.isEmpty()) {
        response.setReply("ABORT");
        break;
      }
      
      String txnId = ""+hid+"#"+tid;
      Optional<LogEntry> entry = LogManager.getInstance().logEntries.stream().filter(e -> txnId.equals(e.txnId)).findFirst();
      response.setReply(entry.isPresent() ? "COMMIT" : "ABORT");
      break;
    }
    case "prepare2die": {
      ClusterInfo.getInstance().setDieOnPrepare(true);
      response.setReply("OK");
      break;
    }
    case "recover": {
      try {
        LogManager.getInstance().readLog();
      } catch (IOException e) {
        System.err.println("Error while recovering log: " + e.getMessage());
        response.setReply("ABORT");
        break;
      }
      System.out.println(LogManager.getInstance().getPreparedTransactions());
      response.setReply("OK");
      break;
    }
    case "flush-writes": {
      try {
        LogManager.getInstance().flushState();
      } catch (IOException e) {
        System.err.println("Error while flushing state: " + e.getMessage());
        response.setReply("ABORT");
        break;
      }
      response.setReply("OK");
      break;
    }
    case "restore-writes": {
      try {
        LogManager.getInstance().restoreState();
      } catch (ClassNotFoundException | IOException e) {
        System.err.println("Error while restoring state: " + e.getMessage());
        response.setReply("ABORT");
        break;
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
    
    if ("prepare".equals(command.toLowerCase()) && ClusterInfo.getInstance().dieOnPrepare()) {
      System.exit(1);
    }
  }

  void rowOp(Row row, Row newRow, String t, String db, String op, String sid, Session localSession) {
    System.out.println(String.format("rowOp(%s, %s)", row.toString(), op));
    Session session = null;
    long start = System.currentTimeMillis();
    System.out.println("*** rowOp 1 at:" + (System.currentTimeMillis() - start));
    start = System.currentTimeMillis();
    if (localSession != null) {
      session = localSession;
    } else {
      session = sessionMap.get(sid);
      if (session == null || session.isClosed()) {
        session = sessionMap.putIfAbsent(sid, createSession());
        if (session == null) {
          session = sessionMap.get(sid);
        }
      }
    }

    System.out.println("*** rowOp 2 at:" + (System.currentTimeMillis() - start));
    start = System.currentTimeMillis();
    Database d = session.getDatabase();
    if (d == null) {
      System.err.println("Database " + db + " not found. Aborting " + op + " operation!");
      return;
    }

    System.out.println("*** rowOp 3 at:" + (System.currentTimeMillis() - start));
    start = System.currentTimeMillis();
    List<Table> tables = d.getTableOrViewByName("MAP");
    System.out.println("tables: " + tables);
    if (tables == null || tables.isEmpty()) {
      System.err.println("Table " + t + " not found. Aborting " + op + " operation!");
    }

    System.out.println("*** rowOp 4 at:" + (System.currentTimeMillis() - start));
    start = System.currentTimeMillis();
    if (op.equalsIgnoreCase("addRow")) {
      ((MVTable) tables.get(0)).addRow(session, row, true, false);
    } else if (op.equalsIgnoreCase("removeRow")) {
      ((MVTable) tables.get(0)).removeRow(session, row, true, false);
    } else if (op.equalsIgnoreCase("updateRow")) {
      ((MVTable) tables.get(0)).updateRow(session, row, newRow, true, false);
    }
    System.out.println("*** rowOp 5 at:" + (System.currentTimeMillis() - start));
    start = System.currentTimeMillis();
//    session.commit(false);
//    session.close();
//    sessionMap.remove(sid);
  }

  void commit(String sid, Session localSession, boolean recovery) {
    Session session = null;
    long start = System.currentTimeMillis();
    System.out.println("*** commit 1 at:" + (System.currentTimeMillis() - start));
    start = System.currentTimeMillis();

    if (localSession != null) {
      session = localSession;
      session.commit(false, true);
      System.out.println("*** commit 2 at:" + (System.currentTimeMillis() - start));
      start = System.currentTimeMillis();
    } else {
      session = sessionMap.get(sid);
      if (session == null && recovery) {
        session = createSession();
      } else if (session == null || session.isClosed()) {
        // Nothing to commit
        System.out.println("Nothing to commit!");
        return;
      }
      System.out.println("*** commit 3 at:" + (System.currentTimeMillis() - start));
      start = System.currentTimeMillis();
      session.commit(false, true);
      // for sessions created by followers, clean up session objects
      System.out.println("*** commit 5 at:" + (System.currentTimeMillis() - start));
      start = System.currentTimeMillis();
      /*
       * sessionMap.remove(sid); final Session s = session; new Thread( () -> {
       * s.close(); }).start();
       */
      System.out.println("*** commit 7 at:" + (System.currentTimeMillis() - start));
      start = System.currentTimeMillis();
    }
  }

  void rollback(String sid, Session localSession) {
    Session session = null;
    long start = System.currentTimeMillis();

    System.out.println("*** rollback 1 at:" + (System.currentTimeMillis() - start));
    start = System.currentTimeMillis();

    if (localSession != null) {
      session = localSession;
      session.rollback(true);
    } else {
      session = sessionMap.get(sid);
      if (session == null || session.isClosed()) {
        // Nothing to rollback
        System.out.println("Nothing to rollback!");
        System.out.println("*** rollback 2 at:" + (System.currentTimeMillis() - start));
        start = System.currentTimeMillis();
        return;
      }

      session.rollback(true);
      // for sessions created by followers, clean up session objects
      /*
       * session.close(); sessionMap.remove(sid);
       */
    }
    System.out.println("*** rollback 3 at:" + (System.currentTimeMillis() - start));
    start = System.currentTimeMillis();
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
