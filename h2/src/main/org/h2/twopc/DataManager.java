package org.h2.twopc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.h2.result.Row;

public class DataManager {
  //largest timestamp of read operation on key
  Map<String, HTimestamp> rtmMap = new ConcurrentHashMap<>();
  //largest timestamp of write operation on key
  Map<String, HTimestamp> wtmMap = new ConcurrentHashMap<>();
  //reads for key
  Map<String, PriorityQueue<HTimestamp>> rMap = new ConcurrentHashMap<>();
  //prewrites for key
  Map<String, PriorityQueue<Prewrite>> pwMap = new ConcurrentHashMap<>();
  //writes for key
  Map<String, PriorityQueue<Prewrite>> wMap = new ConcurrentHashMap<>();
  //prewrites for txn
  Map<HTimestamp, Set<Prewrite>> txMap = new ConcurrentHashMap<>();
  //committed txns with buffer entries
  PriorityQueue<HTimestamp> pending = new PriorityQueue<>();
  
  private static class InstanceHolder {
    private static DataManager INSTANCE = new DataManager();
  }
  
  private DataManager() {
  }
  
  public static DataManager getInstance() {
    return InstanceHolder.INSTANCE;
  }
  
  public boolean prewrite(RowOp data, HTimestamp ts) {
    Prewrite pw = new Prewrite(data, ts);
    HTimestamp wtm = wtmMap.getOrDefault(pw.key, new HTimestamp(ts.hid, Long.MIN_VALUE));
    if (ts.compareTo(wtm) < 0) return false;
    
    pwMap.computeIfAbsent(pw.key, k -> new PriorityQueue<>(new PrewriteComparator()));
    pwMap.get(pw.key).removeIf(e -> e.equals(pw));
    pwMap.get(pw.key).add(pw);
    txMap.computeIfAbsent(ts, k -> new HashSet<>());
    txMap.get(ts).add(pw);
    return true;
  }
  
  public void commit(HTimestamp ts) {
    System.out.println(String.format("commit(%s)", ts.toString()));
    Set<Prewrite> txPrewrites = txMap.get(ts);
    if (txPrewrites == null || txPrewrites.size() == 0) {
      return;
    }

    List<Prewrite> toRemove = new ArrayList<>();
    for(Prewrite pw: txPrewrites) {
      long minTS = pwMap.get(pw.key).peek().timestamp.timestamp;
      checkAndWrite(pw, minTS, toRemove);
    }
    
    for(Prewrite pw: toRemove) {
      txMap.get(pw.timestamp).remove(pw);
      if (txMap.get(pw.timestamp).isEmpty()) {
        txMap.remove(pw.timestamp);
      }
    }
  }
  
  private void checkAndWrite(Prewrite pw, long minTS, List<Prewrite> toRemove) {
    System.out.println(String.format("checkAndWrite(%s, %s, %s)", pw.toString(), String.valueOf(minTS), toRemove));
    boolean result = write(pw);
    
    if (result) {
      pwMap.get(pw.key).removeIf(e -> e.equals(pw));
      if (wMap.get(pw.key) != null) {
        wMap.get(pw.key).removeIf(e -> e.equals(pw));
      }
      
      Prewrite minP = pwMap.get(pw.key).peek();
      if (minP != null) {
        long newMinTS = minP.timestamp.timestamp;
        if (newMinTS > minTS && !wMap.isEmpty()) {
          checkAndWrite(wMap.get(pw.key).peek(), newMinTS, toRemove);
        }
      }
      
      //remove pw from txMap after
      toRemove.add(pw);
    } else {
      //buffer write
      wMap.computeIfAbsent(pw.key, k -> new PriorityQueue<>(new PrewriteComparator()));
      wMap.get(pw.key).removeIf(e -> e.equals(pw));
      wMap.get(pw.key).add(pw);
    }
  }
  
  private boolean write(Prewrite pw) {
    rMap.computeIfAbsent(pw.key, k -> new PriorityQueue<>());
    HTimestamp rMinTS = rMap.get(pw.key).peek();
    if (rMinTS == null || rMinTS.compareTo(pw.timestamp) >= 0) {
      Row row = pw.data.rows.get(0);
      Row newRow = pw.data.rows.get(1);
      CommandProcessor.getInstance().rowOp(row, newRow, "", "", pw.data.command, pw.data.sid);
      wtmMap.put(pw.key, pw.timestamp);
      return true;
    }
    
    return false;
  }
  
  public void rollback(HTimestamp ts) {
    //TODO
    System.out.println("Rollback not implemented yet!");
  }
  
  private class PrewriteComparator implements Comparator<Prewrite> {
    @Override
    public int compare(Prewrite o1, Prewrite o2) {
      return o1.timestamp.compareTo(o2.timestamp);
    }
  }
}
