package org.h2.twopc;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.h2.engine.Constants;
import org.h2.engine.Session;
import org.h2.result.Row;
import org.h2.util.SortedProperties;

import com.google.protobuf.ByteString;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

public class TwoPCCoordinator {

  private String[] cohorts;
  private int hostId;
  private Properties props;
  private String grpcPort;
  private boolean clustered;
  private boolean dummy;
  private boolean dieOnPrepare;

  private Map<String, ManagedChannel> channelMap = new HashMap<>();
  
  private static class InstanceHolder {
    private static TwoPCCoordinator INSTANCE = new TwoPCCoordinator();
  }

  private TwoPCCoordinator() {
    try {
      props = readProperties();
      hostId = props.get("hostId") == null ? 0 : Integer.parseInt(props.get("hostId").toString());
      grpcPort = props.get("grpcPort") == null ? "50051" : props.get("grpcPort").toString();
      Object peers = props.get("peerAddresses");
      if ((clustered = peers != null)) {
        if (peers.toString().isEmpty()) {
          dummy = true;
          System.out.println("Running in dummy coordinator mode!");
        } else {
          cohorts = peers.toString().split("\\|");
        }
      }
      Object dop = props.get("dieOnPrepare");
      dieOnPrepare = (dop != null && "y".equalsIgnoreCase(dop.toString()));
    } catch (Exception e) {
      System.err.println("Error loading properties!");
    }
//    this.cohorts = new String[] {"10.1.10.181:50051"};
//    this.hostId = "1";
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
      List<Row> list = Arrays.asList(new Row[]{row, newRow});

      String sid = String.valueOf(session.getId());
      result = DataManager.getInstance().prewrite(new RowOp(list, op, sid, session), 
          new HTimestamp(getHostId(), tid));
      
      if (!result) {
        System.out.println("Local DM rejected prewrite!");
        return false;
      }
      
      //TODO: temp check to be removed
      if (dummy) {
        result = true;
      }
      else {
        result = sendMessage(op, dbName, tableName, sid, tid, hostId, TwoPCUtils.serialize(list));
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
    boolean result = false;
    long start = System.currentTimeMillis();
    System.out.println("*** Start at:" + start); 

    try {
      System.out.println("*** 1 at:" + (System.currentTimeMillis() - start)); start = System.currentTimeMillis();
      String dbName = session.getDatabase().getName();
      System.out.println("*** 2 at:" + (System.currentTimeMillis() - start)); start = System.currentTimeMillis();
      String sid = String.valueOf(session.getId());
      System.out.println("*** 2 at:" + (System.currentTimeMillis() - start)); start = System.currentTimeMillis();
      long tid = session.getTransaction() == null ? 0L : session.getTransaction().getGlobalId();
      DataManager.getInstance().commit(sid, session, new HTimestamp(getHostId(), tid), false);
      System.out.println("*** 4 at:" + (System.currentTimeMillis() - start)); start = System.currentTimeMillis();
      
      if (dummy) {
        result = true;
      }
      else {
        result = TwoPCCoordinator.getInstance()
            .sendMessage("commit", dbName, "", String.valueOf(session.getId()), tid, hostId, new byte[0]);
      }
      System.out.println("*** 5 at:" + (System.currentTimeMillis() - start)); start = System.currentTimeMillis();
    } catch (InterruptedException | ExecutionException e) {
      // TODO Auto-generated catch block
      System.err.println("Failure sending log message: " + e.getMessage());
      e.printStackTrace();
      result = false;
    }
    
    if (!result) {
      System.err.println("commit call returned false");
    }
    
    return result;
  }
  
  public boolean rollback(Session session) {
    boolean result = false;
    try {
      String dbName = session.getDatabase().getName();
      String sid = String.valueOf(session.getId());
      long tid = session.getTransaction() == null ? 0L : session.getTransaction().getGlobalId();
      DataManager.getInstance().rollback(sid, session, new HTimestamp(getHostId(), tid), false);
      
      if (dummy) {
        result = true;
      }
      else {
        result = TwoPCCoordinator.getInstance()
            .sendMessage("rollback", dbName, "", String.valueOf(session.getId()), tid, hostId, new byte[0]);
      }
    } catch (InterruptedException | ExecutionException e) {
      // TODO Auto-generated catch block
      System.err.println("Failure sending log message: " + e.getMessage());
      e.printStackTrace();
      result = false;
    }
    
    if (!result) {
      System.err.println("commit call returned false");
    }
    
    return result;
  }
  
  public boolean sendMessage(final String command, final String db, final String table, final String sid, 
      final long tid, final int hid, final byte[] data)
      throws InterruptedException, ExecutionException {
      long start = System.currentTimeMillis();
    System.out.println(String.format("sendMessage: {command=%s, db=%s, table=%s, sid=%s, tid=%d, hid=%d, data=%s}", command, db, table, sid, tid, hid, data));
    if (command == null || cohorts == null) {
      System.err.println(String.format("Unable to send message: {%s, %s}", command, data.toString()));
      return false;
    }

      System.out.println("*** sendMessage at:" + (System.currentTimeMillis() - start)); start = System.currentTimeMillis();
    ExecutorService executors = Executors.newFixedThreadPool(cohorts.length);
    List<Future<String>> results = new ArrayList<>(cohorts.length);
    List<TwoPCClient> clients = new ArrayList<>(cohorts.length);

    try {
      // Access a service running on the local machine on port 50051
      for (String cohort : cohorts) {
      System.out.println("*** sendMessage at:" + (System.currentTimeMillis() - start)); start = System.currentTimeMillis();
        System.out.println("cohort: " + cohort);
        TwoPCClient client = buildClient(cohort);
        clients.add(client);
        ByteString b = ByteString.copyFrom(data);
        System.out.println("bytestring: " + b);
        Future<String> result = executors.submit(new CommandRunner(client, command, db, table, sid, tid, hid, b));
        results.add(result);
      System.out.println("*** sendMessage at:" + (System.currentTimeMillis() - start)); start = System.currentTimeMillis();
      }

      System.out.println("*** sendMessage executors.awaitTermination at:" + (System.currentTimeMillis() - start)); start = System.currentTimeMillis();
      executors.shutdown();
      executors.awaitTermination(200, TimeUnit.MILLISECONDS);
      System.out.println("*** sendMessage executors.awaitTermination at:" + (System.currentTimeMillis() - start)); start = System.currentTimeMillis();

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

//  public boolean terminate(String tid, String command) throws InterruptedException, ExecutionException {
//    if (cohorts == null) {
//      System.err.println();
//      return false;
//    }
//
//    ExecutorService executors = Executors.newFixedThreadPool(cohorts.length);
//    List<Future<String>> results = new ArrayList<>(cohorts.length);
//    // Access a service running on the local machine on port 50051
//    for (String cohort : cohorts) {
//      System.out.println("cohort: " + cohort);
//      TwoPCClient client = buildClient(cohort);
//      Future<String> result = executors.submit(new CommandRunner(client, command, tid, null));
//      results.add(result);
//    }
//
//    executors.awaitTermination(200, TimeUnit.MILLISECONDS);
//
//    for (Future<String> result : results) {
//      if (!result.isDone() || !"ACK".equalsIgnoreCase(result.get())) {
//        return false;
//      }
//    }
//
//    return true;
//  }

  private TwoPCClient buildClient(String cohort) throws InterruptedException {
    // Create a communication channel to the server, known as a Channel. Channels
    // are thread-safe
    // and reusable. It is common to create channels at the beginning of your
    // application and reuse
    // them until the application shuts down.
    ManagedChannel channel = null;
    if ((channel = channelMap.get(cohort)) == null) {
      channel = ManagedChannelBuilder.forTarget(cohort)
          // Channels are secure by default (via SSL/TLS). For the example we disable TLS
          // to avoid
          // needing certificates.
          .usePlaintext().build();
      channelMap.put(cohort, channel);
    }

    try {
      return new TwoPCClient(channel);
    } finally {
      // ManagedChannels use resources like threads and TCP connections. To prevent
      // leaking these
      // resources the channel should be shut down when it will no longer be used. If
      // it may be used
      // again leave it running.
      // CS244b: moved to shutdown method in TwoPCClient
//      channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }
  }

  private Properties readProperties() throws IOException {
    String serverPropertiesDir = Constants.SERVER_PROPERTIES_DIR;
    if ("null".equals(serverPropertiesDir)) {
      return null;
    }

    Properties props = SortedProperties.loadProperties(serverPropertiesDir + "/" + Constants.CLUSTER_PROPERTIES_NAME);

    return props;
  }

  public String[] getCohorts() {
    return cohorts;
  }

  public int getHostId() {
    return hostId;
  }

  public String getGrpcPort() {
    return grpcPort;
  }

  public boolean isClustered() {
    return clustered;
  }
  
  public boolean dieOnPrepare() {
    return dieOnPrepare;
  }
  
  public void setDieOnPrepare(boolean flag) {
    this.dieOnPrepare = flag;
  }
  
  private class CommandRunner implements Callable<String> {

    private TwoPCClient client;
    private String command;
    private String db;
    private String table;
    private String sid;
    private long tid;
    private int hid;
    private ByteString data;

    public CommandRunner(TwoPCClient client, String command, String db, String table, String sid,  
        long tid, int hid, ByteString data) {
      this.client = client;
      this.command = command;
      this.db = db;
      this.table = table;
      this.sid = sid;
      this.tid = tid;
      this.hid = hid;
      this.data = data;
    }

    @Override
    public String call() throws Exception {
      System.out.println("Calling..");
      return client.process(command, db, table, sid, tid, hid, data);
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
