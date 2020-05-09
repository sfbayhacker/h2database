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
  
  private static class InstanceHolder {
    private static TwoPCCoordinator INSTANCE = new TwoPCCoordinator();
  }
  
  private TwoPCCoordinator() {
    this.cohorts = getPeerAddresses();
  }
  
  public static TwoPCCoordinator getInstance() {
    return InstanceHolder.INSTANCE;
  }
  
  public boolean prepare(String tid, ByteString data) throws InterruptedException, ExecutionException {
    if (cohorts == null) {
      System.err.println();
      return false;
    }
    
    String command = "PREPARE";

    ExecutorService executors = Executors.newFixedThreadPool(cohorts.length);
    List<Future<String>> results = new ArrayList<>(cohorts.length);
    // Access a service running on the local machine on port 50051
    for(String cohort: cohorts) {
      System.out.println("cohort: " + cohort);
      TwoPCClient client = buildClient(cohort);
      Future<String> result = executors.submit(new CommandRunner(client, command, tid, data));
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
      channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }
  }
  
  //TODO: make return array; change logic in main to use executor service to send messages and wait for all calls
  //to succeed and return true; if any one fails or times out, the method should false
  private String[] getPeerAddresses() {
    try {
        String serverPropertiesDir = Constants.SERVER_PROPERTIES_DIR;
        if ("null".equals(serverPropertiesDir)) {
            return null;
        }
        Properties props = SortedProperties.loadProperties(
                serverPropertiesDir + "/" + Constants.SERVER_PROPERTIES_NAME);
        
        Object peers = props.get("peerAddresses");
        
        if (peers == null) return null;
        
        return peers.toString().split("|");
    } catch (Exception e) {
        DbException.traceThrowable(e);
        return null;
    }
  }
  
  public String[] getCohorts() {
    return cohorts;
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
      return client.process(command, tid, data);
    }
    
  }
  
  /**
   * Greet server. If provided, the first element of {@code args} is the name to use in the
   * greeting. The second argument is the target server.
   */
  public static void main(String[] args) throws Exception {
    TwoPCCoordinator.getInstance().prepare("001", ByteString.copyFromUtf8("key=value"));
  }
}
