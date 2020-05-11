package org.h2.twopc;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.h2.engine.Constants;
import org.h2.message.DbException;
import org.h2.util.SortedProperties;

import com.google.protobuf.ByteString;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

public class TwoPCCoordinator {
  
  private String[] cohorts;
  private String hostId;
  
  private static class InstanceHolder {
    private static TwoPCCoordinator INSTANCE = new TwoPCCoordinator();
  }
  
  private TwoPCCoordinator() {
//    readProperties();
    this.cohorts = new String[] {"10.1.10.181:50051"};
    this.hostId = "1";
  }
  
  public static TwoPCCoordinator getInstance() {
    return InstanceHolder.INSTANCE;
  }
  
  public boolean sendMessage(final String tid, final String command, final byte[] data) throws InterruptedException, ExecutionException {
    System.out.println(String.format("sendMessage: {%s, %s, %s}", tid, command, data));
    if (tid == null || command == null || cohorts == null) {
      System.err.println(String.format("Unable to send message: {%s, %s, %s}", tid, command, data.toString()));
      return false;
    }

    ExecutorService executors = Executors.newFixedThreadPool(cohorts.length);
    List<Future<String>> results = new ArrayList<>(cohorts.length);
    // Access a service running on the local machine on port 50051
    for(String cohort: cohorts) {
      System.out.println("cohort: " + cohort);
      TwoPCClient client = buildClient(cohort);
      ByteString b = ByteString.copyFrom(data);
      System.out.println("bytestring: " + b);
      Future<String> result = executors.submit(new CommandRunner(client, command, tid, b));
      results.add(result);
    }
    
    executors.awaitTermination(200, TimeUnit.MILLISECONDS);
    
    for(Future<String> result: results) {
      if (!result.isDone() || !"COMMIT-VOTE".equalsIgnoreCase(result.get())) {
        return false;
      }
    }
    
    return true;
  }
  
  public boolean terminate(String tid, String command) throws InterruptedException, ExecutionException {
    if (cohorts == null) {
      System.err.println();
      return false;
    }
    
    ExecutorService executors = Executors.newFixedThreadPool(cohorts.length);
    List<Future<String>> results = new ArrayList<>(cohorts.length);
    // Access a service running on the local machine on port 50051
    for(String cohort: cohorts) {
      System.out.println("cohort: " + cohort);
      TwoPCClient client = buildClient(cohort);
      Future<String> result = executors.submit(new CommandRunner(client, command, tid, null));
      results.add(result);
    }
    
    executors.awaitTermination(200, TimeUnit.MILLISECONDS);
    
    for(Future<String> result: results) {
      if (!result.isDone() || !"ACK".equalsIgnoreCase(result.get())) {
        return false;
      }
    }
    
    return true;
  }
  
  private TwoPCClient buildClient(String cohort) throws InterruptedException {
    // Create a communication channel to the server, known as a Channel. Channels are thread-safe
    // and reusable. It is common to create channels at the beginning of your application and reuse
    // them until the application shuts down.
    ManagedChannel channel = ManagedChannelBuilder.forTarget(cohort)
        // Channels are secure by default (via SSL/TLS). For the example we disable TLS to avoid
        // needing certificates.
        .usePlaintext()
        .build();
    try {
      return new TwoPCClient(channel);
    } finally {
      // ManagedChannels use resources like threads and TCP connections. To prevent leaking these
      // resources the channel should be shut down when it will no longer be used. If it may be used
      // again leave it running.
      //CS244b TODO: clean up code to be moved to appropriate place
//      channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }
  }
  
  private void readProperties() {
    try {
        String serverPropertiesDir = Constants.SERVER_PROPERTIES_DIR;
        if ("null".equals(serverPropertiesDir)) {
            return;
        }
        
        Properties props = SortedProperties.loadProperties(
                serverPropertiesDir + "/" + Constants.SERVER_PROPERTIES_NAME);
        
        hostId = props.get("hostId").toString();
        
        Object peers = props.get("peerAddresses");
        if (peers != null) {
          cohorts = peers.toString().split("\\|");
        }
    } catch (Exception e) {
        DbException.traceThrowable(e);
    }
  }
  
  public String[] getCohorts() {
    return cohorts;
  }
  
  public String getHostId() {
    return hostId;
  }
  
  private class CommandRunner implements Callable<String> {

    private TwoPCClient client;
    private String command;
    private String tid;
    private ByteString data;
    
    public CommandRunner(TwoPCClient client, String command, String tid, ByteString data) {
      this.client = client;
      this.command = command;
      this.tid = tid;
      this.data = data;
    }
    
    @Override
    public String call() throws Exception {
      System.out.println("Calling..");
      return client.process(command, tid, data);
    }
    
  }
  
  /**
   * Greet server. If provided, the first element of {@code args} is the name to use in the
   * greeting. The second argument is the target server.
   */
  public static void main(String[] args) throws Exception {
    TwoPCCoordinator.getInstance().sendMessage("001", "PREPARE", TwoPCUtils.serialize("key=value"));
  }
}
