package org.h2.twopc;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.google.protobuf.ByteString;

public class TwoPCFollower {
  
  private static class InstanceHolder {
    private static TwoPCFollower INSTANCE = new TwoPCFollower();
  }

  private TwoPCFollower() {

  }

  public void init() {
    System.out.println("TwoPCFollower()");
    if (ClusterInfo.getInstance().isFollower()) {
      try {
        doInitChecks();
      } catch (InterruptedException | ExecutionException e) {
        System.err.println("Error while performing init checks: " + e.getMessage());
      }      
    }
  }
  
  public static TwoPCFollower getInstance() {
    return InstanceHolder.INSTANCE;
  }
  
  private void doInitChecks() throws InterruptedException, ExecutionException {
    System.out.println("Performing init checks..");
    List<HTimestamp> prepared = LogManager.getInstance().getPreparedTransactions();
    //if (true) return;
    if (prepared == null || prepared.isEmpty()) return;
    System.err.println("prepared txns: " + prepared);
   
    outer: 
    for(HTimestamp ts: prepared) {
      inner:
      while (true) {
        System.err.println("Checking status of " + ts.timestamp + " with coordinator");
        Optional<String> txnStatus = checkTxnStatus(ts.timestamp);
        if (txnStatus.isPresent()) {
          System.err.println("Txn status is " + txnStatus.get());
          if ("commit".equalsIgnoreCase(txnStatus.get())) {
            List<Prewrite> pwList = DataManager.getInstance().txMap.get(ts);
            if (pwList != null && !pwList.isEmpty()) {
              System.err.println("Prewrites found. Commiting prewrites");
              DataManager.getInstance().commit(pwList.get(0).data.remoteSid, null, ts, true);
            }
            System.err.println("Writing commit log record");
            boolean result = LogManager.getInstance().appendLogEntry("" + ts.hid + "#" + ts.timestamp, "commit");
	    if (!result) continue outer;
          }
          break inner;
        } else {
          System.out.println("Txn status not available!!");
        }
      }
    }
  }
  
  private Optional<String> checkTxnStatus(final long tid)
      throws InterruptedException, ExecutionException {
    long start = System.currentTimeMillis();
    String command = "txnstatus";
    int hid = 1;
    System.out.println(String.format("sendMessage: {command=%s, tid=%d, hid=%d}", command, tid, hid));
    String coordinator = ClusterInfo.getInstance().getCoordinator();
    if (command == null || coordinator == null) {
      System.err.println(String.format("Unable to send message: {%s}", command));
      return Optional.empty();
    }

    System.out.println("*** sendMessageToCoordinator at 1:" + (System.currentTimeMillis() - start));
    start = System.currentTimeMillis();

    ExecutorService executors = Executors.newFixedThreadPool(1);
    final TwoPCClient client = GrpcManager.getInstance().buildClient(coordinator);
    
    try {
      // Access a service running on the local machine on port 50051
      System.out.println("*** sendMessageToCoordinator at 2:" + (System.currentTimeMillis() - start));
      start = System.currentTimeMillis();
      System.out.println("coordinator: " + ClusterInfo.getInstance().getCoordinator());
      
      ByteString b = ByteString.copyFrom(new byte[0]);
      
//      new Thread( () -> {
      Future<String> result = executors
        .submit(new GrpcManager.CommandRunner(client, command, "", "", "", tid, hid, b));
//      }).start();
      
      System.out.println("*** sendMessageToCoordinator at 3:" + (System.currentTimeMillis() - start));
      
      executors.shutdown();
      executors.awaitTermination(200, TimeUnit.MILLISECONDS);
      
      if (!result.isDone()) {
        return Optional.empty();
      }
      
      return Optional.of(result.get());
    } finally {
      if (client != null) client.shutdown();
    }
  }
}
