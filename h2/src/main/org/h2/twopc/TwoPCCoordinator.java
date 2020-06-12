package org.h2.twopc;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.h2.engine.Session;
import org.h2.result.Row;

import com.google.protobuf.ByteString;

public class TwoPCCoordinator {

  private static class InstanceHolder {
    private static TwoPCCoordinator INSTANCE = new TwoPCCoordinator();
  }

  private TwoPCCoordinator() {
  }

  public static TwoPCCoordinator getInstance() {
    return InstanceHolder.INSTANCE;
  }

  public boolean rowOp(Session session, String tableName, Row row, Row newRow, String op) {
    boolean result = false;
    try {

      String dbName = session.getDatabase().getName();
      long tid = session.getTransaction() == null ? 0L : session.getTransaction().getGlobalId();
      System.out.println("rowOp " + op + "; Row : " + row + "; Class is " + row.getClass() + "; newRow: " + newRow);
      List<Row> list = Arrays.asList(new Row[] { row, newRow });

      String sid = String.valueOf(session.getId());
      result = DataManager.getInstance().prewrite(new RowOp(list, op, sid, session),
          new HTimestamp(ClusterInfo.getInstance().getHostId(), tid));

      if (!result) {
        System.out.println("Local DM rejected prewrite!");
        return false;
      }

      // TODO: temp check to be removed
      if (ClusterInfo.getInstance().isDummy()) {
        result = true;
      } else {
        result = sendMessage(op, dbName, tableName, sid, tid, ClusterInfo.getInstance().getHostId(),
            TwoPCUtils.serialize(list));
      }
    } catch (InterruptedException | ExecutionException | IOException e) {
      // TODO Auto-generated catch block
      System.err.println("Failure sending log message: " + e.getMessage());
      e.printStackTrace();
      result = false;
    }

    if (!result) {
      System.err.println(op + " call returned false");
    }

    return result;
  }

  public boolean commit(Session session) {
    System.out.println("TwoPCCoordinator::commit()");
    boolean result = doPrepare(session);
    System.out.println("result after prepare: " + result);
    result = result && doCommit(session, "commit");
    System.out.println("result after commit: " + result);
    if (!result) {
      System.out.println("rolling back..");
      rollback(session);
    }

    System.out.println("final result: " + result);

    return result;
  }

  private boolean doPrepare(Session session) {
    boolean result = false;
    long start = System.currentTimeMillis();
    System.out.println("*** Start at:" + start);

    try {
      System.out.println("*** 1 at:" + (System.currentTimeMillis() - start));
      start = System.currentTimeMillis();
      String dbName = session.getDatabase().getName();
      long tid = session.getTransaction() == null ? 0L : session.getTransaction().getGlobalId();

      if (ClusterInfo.getInstance().isDummy()) {
        result = true;
      } else {
        result = TwoPCCoordinator.getInstance().sendMessage("prepare", dbName, "map", String.valueOf(session.getId()), tid,
            ClusterInfo.getInstance().getHostId(), new byte[0]);
      }
      System.out.println("*** 2 at:" + (System.currentTimeMillis() - start));
      start = System.currentTimeMillis();
    } catch (InterruptedException | ExecutionException e) {
      // TODO Auto-generated catch block
      System.err.println("Failure sending log message: " + e.getMessage());
      e.printStackTrace();
      result = false;
    }

    if (!result) {
      System.err.println("prepare call returned false");
    }

    System.out.println("PREPARE DONE!!");
    
    return result;
  }

  private boolean doCommit(final Session session, final String command) {
    final String dbName = session.getDatabase().getName();
    final long tid = session.getTransaction() == null ? 0L : session.getTransaction().getGlobalId();
    boolean result = LogManager.getInstance().appendLogEntry("" + ClusterInfo.getInstance().getHostId() + "#" + tid,
        "commit");
    if (result) {
      if (!ClusterInfo.getInstance().isDummy()) {
        new Thread(() -> {
          try {
            TwoPCCoordinator.getInstance().sendMessage(command, dbName, "", String.valueOf(session.getId()), tid,
                ClusterInfo.getInstance().getHostId(), new byte[0]);
          } catch (InterruptedException | ExecutionException e) {
            System.err.println("Error while sending " + command + " to followers: " + e.getMessage());
          }
        }).start();        
      }

      String sid = String.valueOf(session.getId());
      DataManager.getInstance().commit(sid, session, new HTimestamp(ClusterInfo.getInstance().getHostId(), tid), false);
      
      System.out.println("COMMIT DONE!!");
    }
    
    return result;
  }

  public boolean rollback(Session session) {
    boolean result = false;
    try {
      String dbName = session.getDatabase().getName();
      String sid = String.valueOf(session.getId());
      long tid = session.getTransaction() == null ? 0L : session.getTransaction().getGlobalId();
      
      result = LogManager.getInstance().appendLogEntry("" + ClusterInfo.getInstance().getHostId() + "#" + tid, "abort");
      
      if (result) {
        if (ClusterInfo.getInstance().isDummy()) {
          result = true;
        } else {
          result = TwoPCCoordinator.getInstance().sendMessage("rollback", dbName, "", String.valueOf(session.getId()),
              tid, ClusterInfo.getInstance().getHostId(), new byte[0]);
        }

        if (!result) {
          System.err.println("abort call returned false");
        }
        
        DataManager.getInstance().rollback(sid, session, new HTimestamp(ClusterInfo.getInstance().getHostId(), tid));        
      } else {
        System.err.println("writing abort record failed at coordinator!!");
      }

    } catch (InterruptedException | ExecutionException e) {
      // TODO Auto-generated catch block
      System.err.println("Failure sending log message: " + e.getMessage());
      e.printStackTrace();
      result = false;
    }

    return result;
  }

  public boolean sendMessage(final String command, final String db, final String table, final String sid,
      final long tid, final int hid, final byte[] data) throws InterruptedException, ExecutionException {
    System.err.println("Sending " + command);
    long start = System.currentTimeMillis();
    String[] cohorts = ClusterInfo.getInstance().getCohorts();
    System.out.println(String.format("sendMessage: {command=%s, db=%s, table=%s, sid=%s, tid=%d, hid=%d, data=%s}",
        command, db, table, sid, tid, hid, data));
    if (command == null || cohorts == null) {
      System.err.println(String.format("Unable to send message: {%s, %s}", command, data.toString()));
      return false;
    }

    System.out.println("*** sendMessage at:" + (System.currentTimeMillis() - start));
    start = System.currentTimeMillis();
    ExecutorService executors = Executors.newFixedThreadPool(cohorts.length);
    List<Future<String>> results = new ArrayList<>(cohorts.length);
    List<TwoPCClient> clients = new ArrayList<>(cohorts.length);

    try {
      // Access a service running on the local machine on port 50051
      for (String cohort : cohorts) {
        System.out.println("*** sendMessage at:" + (System.currentTimeMillis() - start));
        start = System.currentTimeMillis();
        System.out.println("cohort: " + cohort);
        TwoPCClient client = GrpcManager.getInstance().buildClient(cohort);
        clients.add(client);
        ByteString b = ByteString.copyFrom(data);
        System.out.println("bytestring: " + b);
        Future<String> result = executors
            .submit(new GrpcManager.CommandRunner(client, command, db, table, sid, tid, hid, b));
        results.add(result);
        System.out.println("*** sendMessage at:" + (System.currentTimeMillis() - start));
        start = System.currentTimeMillis();
      }

      System.out.println("*** sendMessage executors.awaitTermination at:" + (System.currentTimeMillis() - start));
      start = System.currentTimeMillis();
      executors.shutdown();
      executors.awaitTermination(200, TimeUnit.MILLISECONDS);
      System.out.println("*** sendMessage executors.awaitTermination at:" + (System.currentTimeMillis() - start));
      start = System.currentTimeMillis();

      for (Future<String> result : results) {
        if (!result.isDone() || !"OK".equalsIgnoreCase(result.get())) {
          return false;
        }
      }

      return true;
    } finally {
      for (TwoPCClient client : clients) {
        client.shutdown();
      }
    }
  }

  /**
   * Greet server. If provided, the first element of {@code args} is the name to
   * use in the greeting. The second argument is the target server.
   */
  public static void main(String[] args) throws Exception {
    TwoPCCoordinator.getInstance().sendMessage("PREPARE", "test", "MAP", "0", 0L, 0, TwoPCUtils.serialize("key=value"));
  }
}
