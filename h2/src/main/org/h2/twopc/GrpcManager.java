package org.h2.twopc;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import com.google.protobuf.ByteString;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

public class GrpcManager {
  
  private Map<String, ManagedChannel> channelMap = new HashMap<>();
  
  private static class InstanceHolder {
    private static GrpcManager INSTANCE = new GrpcManager();
  }

  private GrpcManager() {
  }

  public static GrpcManager getInstance() {
    return InstanceHolder.INSTANCE;
  }
  
  TwoPCClient buildClient(String cohort) throws InterruptedException {
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
  
  static class CommandRunner implements Callable<String> {

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
}
