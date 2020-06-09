package org.h2.twopc;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;

import org.h2.engine.Session;
import org.h2.message.DbException;
import org.h2.result.Row;

public class DataManager implements Serializable {
  //largest timestamp of read operation on key
  Map<String, HTimestamp> rtmMap = new ConcurrentHashMap<>();
  //largest timestamp of write operation on key
  Map<String, HTimestamp> wtmMap = new ConcurrentHashMap<>();
  //reads for key
//  Map<String, PriorityQueue<HTimestamp>> rMap = new ConcurrentHashMap<>();
  //prewrites for key
  Map<String, PriorityQueue<Prewrite>> pwMap = new ConcurrentHashMap<>();
  //writes for key
  Map<String, PriorityQueue<Prewrite>> wMap = new ConcurrentHashMap<>();
  //prewrites for txn
  Map<HTimestamp, List<Prewrite>> txMap = new ConcurrentHashMap<>();
  //key map -- row key <> primary key
  Map<Long, String> keyMap = new ConcurrentHashMap<>();
  //writes cache map -- TODO: remove entries based on time (10s)
  Map<String, Row> writesCache = new ConcurrentHashMap<>();
  
  private static class InstanceHolder {
    private static DataManager INSTANCE = new DataManager();
  }
  
  private DataManager() {
  }
  
  public void init() {
    try {
      DataManager restored = LogManager.getInstance().restoreState();
      if (restored != null) {
        restoreFrom(restored);
      }
    } catch (ClassNotFoundException | IOException e) {
      System.err.println("Error while restoring state: " + e.getMessage());
    }
  }
  
  private void restoreFrom(DataManager restored) {
    this.rtmMap = restored.rtmMap;
    this.wtmMap = restored.wtmMap;
    this.pwMap = restored.pwMap;
    this.wMap = restored.wMap;
    this.txMap = restored.txMap;
    this.keyMap = restored.keyMap;
    this.writesCache = restored.writesCache;
  }
  
  public static DataManager getInstance() {
    return InstanceHolder.INSTANCE;
  }
  
  public Row read(RowOp data, HTimestamp ts) {
    System.out.println(String.format("DataManager::read(%s)", ts.toString()));
    if (!checkReadOp(data, ts)) {
      throw DbException.get(99999, new RuntimeException("Read before write on same key!"));
    }
    
    String key = null;
    
    if ( (key = keyMap.get(data.rowKey)) == null) {
      return getFromWritesCacheOrNull(data.key);
    }
    
    List<Prewrite> l = txMap.get(ts);
    
    if (l == null || l.isEmpty()) {
      return getFromWritesCacheOrNull(key);
    }
    
    Iterator<Prewrite> it = l.iterator();
    Row resultRow = null;
    //TODO: check how to get last matching element
    while(it.hasNext()) {
      Prewrite pw = it.next();
      if (key.equals(pw.key)) {
        resultRow = pw.data.resultRow;
        System.out.println("matching row - " + resultRow);
      }
    }
    
    if (resultRow == null) {
      return getFromWritesCacheOrNull(key);
    }
    
    return resultRow;
  }
  
  private boolean checkReadOp(RowOp data, HTimestamp ts) {
    HTimestamp wtm = wtmMap.getOrDefault(data.key, new HTimestamp(ts.hid, Long.MIN_VALUE));
    System.out.println("Comparing ts " + ts + " with wtm " + wtm);
    if (ts.lessThan(wtm)) {
      return false;
    }
    
    //TODO
    /*
    If (no PWi in the buffer)
    execute Ri and RTM(x)= max(RTM(x), TS);
    else
    If TS <= TS(PW-min) then
    execute Ri and RTM(x) = max(RTM(x), TS);
    else // there is one (or more) PW with TS(PW) < TS
    Ri is buffered until all transactions which
    has TS(PW) < TS commit;
    */
    
    PriorityQueue<Prewrite> q = pwMap.get(data.key);
    if (q == null || q.isEmpty()) {
      System.out.println("q is empty");
      setRTM(data, ts);
    } else {
      HTimestamp minTS = pwMap.get(data.key).peek().timestamp;
      System.out.println("Comparing ts " + ts + " with minTS " + minTS);
      if (ts.lessThanOrEqualTo(minTS)) {
        setRTM(data, ts);
      } else {
        int count = 0;
        while(true) {
          Prewrite minP = pwMap.get(data.key) == null ? null : pwMap.get(data.key).peek();
//          minTS = pwMap.get(data.key).peek().timestamp;
          if (minP == null || ts.lessThanOrEqualTo(minP.timestamp)) {
            setRTM(data, ts);
            return true;
          }
          
          try {
            Thread.sleep(10);
          } catch (InterruptedException e) {
            System.err.println(e.getMessage());
          }
          if (++count == 1000) {
            throw DbException.get(99999, new RuntimeException("Waited 2s for prewrites to complete!"));
          }
        }
      }
    }
    
    return true;
  }
  
  private void setRTM(RowOp data, HTimestamp ts) {
    System.out.println(String.format("DataManager::setRTM(%s)", ts.toString()));
    HTimestamp rtm = rtmMap.getOrDefault(data.key, new HTimestamp(ts.hid, Long.MIN_VALUE));
    if (ts.greaterThan(rtm)) {
      rtmMap.put(data.key, new HTimestamp(ts.hid, ts.timestamp));
    }
    
    System.out.println(String.format("rtmMap - %s", rtmMap));
  }
  
  public boolean prewrite(RowOp data, HTimestamp ts) {
    System.out.println(String.format("DataManager::prewrite(%s)", ts.toString()));
    int seq = txMap.get(ts) == null ? 0 : txMap.get(ts).size() + 1;
    Prewrite pw = new Prewrite(data, ts, seq);
    HTimestamp wtm = wtmMap.getOrDefault(pw.key, new HTimestamp(ts.hid, Long.MIN_VALUE));
    HTimestamp rtm = rtmMap.getOrDefault(pw.key, new HTimestamp(ts.hid, Long.MIN_VALUE));
    if (ts.lessThan(rtm) || ts.lessThan(wtm)) return false;
    
    pwMap.computeIfAbsent(pw.key, k -> new PriorityQueue<>(new PrewriteComparator()));
    pwMap.get(pw.key).removeIf(e -> e.equals(pw));
    pwMap.get(pw.key).add(pw);
    
    System.out.println("pwMap - " + pwMap);
    
    keyMap.put(pw.rowKey, pw.key);
    txMap.computeIfAbsent(ts, k -> new ArrayList<>());
    txMap.get(ts).add(pw);
    
    System.out.println("txMap - " + txMap);
    
    return true;
  }
  
  public void commit(String remoteSid, Session localSession, HTimestamp ts, boolean recovery) {
    System.out.println(String.format("DataManager::commit(%s)", ts.toString()));
   long start = System.currentTimeMillis();
    System.out.println("*** DM commit 1 at:" + (System.currentTimeMillis() - start)); start = System.currentTimeMillis();

    List<Prewrite> txPrewrites = txMap.get(ts);
    if (txPrewrites == null || txPrewrites.size() == 0) {
      return;
    }

    System.out.println("*** DM commit 2 at:" + (System.currentTimeMillis() - start)); start = System.currentTimeMillis();
    List<Prewrite> toRemove = new ArrayList<>();

    for(Prewrite pw: txPrewrites) {
      HTimestamp minTS = pwMap.get(pw.key).peek().timestamp;
      checkAndWrite(pw, minTS, toRemove);
    }
    
    System.out.println("*** DM commit 3 at:" + (System.currentTimeMillis() - start)); start = System.currentTimeMillis();
    for(Prewrite pw: toRemove) {
      removeFromTxMap(pw);
      removeFromPwMap(pw);
    }
    
    System.out.println("*** DM commit 4 at:" + (System.currentTimeMillis() - start)); start = System.currentTimeMillis();
    CommandProcessor.getInstance().commit(remoteSid, localSession, recovery);
    System.out.println("*** DM commit 5 at:" + (System.currentTimeMillis() - start)); start = System.currentTimeMillis();
  }
  
  private void checkAndWrite(Prewrite pw, HTimestamp minTS, List<Prewrite> toRemove) {
    System.out.println(String.format("DataManager::checkAndWrite(%s, %s, %s)", pw.toString(), String.valueOf(minTS), toRemove));
    long start = System.currentTimeMillis();
    boolean result = write(pw);
    System.out.println("*** checkAndWrite 1 at:" + (System.currentTimeMillis() - start)); start = System.currentTimeMillis(); 
    
    if (result) {
      if (wMap.get(pw.key) != null) {
        wMap.get(pw.key).remove(pw);
      }
      
    System.out.println("*** checkAndWrite 2 at:" + (System.currentTimeMillis() - start)); start = System.currentTimeMillis(); 
      Prewrite minP = pwMap.get(pw.key).peek();
      if (minP != null) {
        HTimestamp newMinTS = minP.timestamp;
        if (newMinTS.greaterThan(minTS) && !wMap.isEmpty()) {
          checkAndWrite(wMap.get(pw.key).peek(), newMinTS, toRemove);
        }
      }
      
    System.out.println("*** checkAndWrite 3 at:" + (System.currentTimeMillis() - start)); start = System.currentTimeMillis(); 
      //remove pw from txMap after
      toRemove.add(pw);
    System.out.println("*** checkAndWrite 4 at:" + (System.currentTimeMillis() - start)); start = System.currentTimeMillis(); 
    } else {
      //buffer write
    System.out.println("*** checkAndWrite 5 at:" + (System.currentTimeMillis() - start)); start = System.currentTimeMillis(); 
      wMap.computeIfAbsent(pw.key, k -> new PriorityQueue<>(new PrewriteComparator()));
      wMap.get(pw.key).removeIf(e -> e.equals(pw));
      wMap.get(pw.key).add(pw);
    System.out.println("*** checkAndWrite 6 at:" + (System.currentTimeMillis() - start)); start = System.currentTimeMillis(); 
    }
  }
  
  private boolean write(Prewrite pw) {
    System.out.println(String.format("DataManager::write(%s)", pw.toString()));
    long start = System.currentTimeMillis();
    System.out.println("*** write 1 at:" + (System.currentTimeMillis() - start)); start = System.currentTimeMillis(); 
//    rMap.computeIfAbsent(pw.key, k -> new PriorityQueue<>());
//    HTimestamp rMinTS = rMap.get(pw.key).peek();
//    if (rMinTS == null || rMinTS.greaterThanOrEqualTo(pw.timestamp)) {
      Row row = pw.data.rows.get(0);
      Row newRow = pw.data.rows.get(1);
      CommandProcessor.getInstance().rowOp(row, newRow, "", "", 
          pw.data.command, pw.data.remoteSid, pw.data.localSession);
      wtmMap.put(pw.key, pw.timestamp);
      writesCache.put(pw.key, newRow == null ? row : newRow);
    System.out.println("*** write 2 at:" + (System.currentTimeMillis() - start)); start = System.currentTimeMillis(); 
      return true;
//    }
    
//    return false;
  }
  
  public void rollback(String remoteSid, Session localSession, HTimestamp ts) {
    System.out.println(String.format("DataManager::rollback(%s)", ts.toString()));
    List<Prewrite> txPrewrites = txMap.get(ts);
    if (txPrewrites == null || txPrewrites.isEmpty()) {
      CommandProcessor.getInstance().rollback(remoteSid, localSession);
      return;
    }

    //remove prewrites
    for(Prewrite pw: txPrewrites) {
      removeFromPwMap(pw);
    }
    
    //clear tx map
    txMap.remove(ts);
    
    //rollback
    CommandProcessor.getInstance().rollback(remoteSid, localSession);
  }
  
  private void removeFromTxMap(Prewrite pw) {
    txMap.get(pw.timestamp).remove(pw);
    if (txMap.get(pw.timestamp).isEmpty()) {
      txMap.remove(pw.timestamp);
    }
  }

  private void removeFromPwMap(Prewrite pw) {
    pwMap.get(pw.key).remove(pw);
    if (pwMap.get(pw.key).isEmpty()) {
      pwMap.remove(pw.key);
      keyMap.remove(pw.rowKey);
    }
  }
  
  private Row getFromWritesCacheOrNull(String key) {
    return writesCache.get(key);
  }
  
  /*private void flushWrites() {
    try {
      LogUtil.flush(wMap);
    } catch (Exception e) {
      throw DbException.get(99999, new RuntimeException("Log flush failed!"));
    }
  }*/
  
//  void flushState() {
//    MapData data = new MapData(pwMap, wMap);
//    try {
//      LogUtil.flushDM();
//    } catch (IOException e) {
//      System.err.println("Error during flush: " + e.getMessage());
//      e.printStackTrace();
//    }
//  }
//  
//  void restoreState() {
//    try {
//      LogUtil.restoreDM();
//    } catch (IOException | ClassNotFoundException e) {
//      System.err.println("Error during restore: " + e.getMessage());
//      e.printStackTrace();
//    }
//  }
  
  private class PrewriteComparator implements Comparator<Prewrite>, Serializable {
    @Override
    public int compare(Prewrite o1, Prewrite o2) {
      int result = o1.timestamp.compareTo(o2.timestamp);
      if (result == 0) {
        result = o1.seq.compareTo(o2.seq);
      }
      return result;
    }
  }
  
  class MapData implements Serializable {
    Map<String, PriorityQueue<Prewrite>> pwMap;
    Map<String, PriorityQueue<Prewrite>> wMap;
    
    MapData(Map<String, PriorityQueue<Prewrite>> pwMap, Map<String, PriorityQueue<Prewrite>> wMap) {
      this.pwMap = pwMap;
      this.wMap = wMap;
    }
  }
}
