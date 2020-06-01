package org.h2.twopc;

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

public class DataManager {
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
  
  private static class InstanceHolder {
    private static DataManager INSTANCE = new DataManager();
  }
  
  private DataManager() {
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
      return null;
    }
    
    List<Prewrite> l = txMap.get(ts);
    
    if (l == null || l.isEmpty()) {
      return null;
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
          minTS = pwMap.get(data.key).peek().timestamp;
          if (ts.lessThanOrEqualTo(minTS)) {
            return true;
          }
          
          try {
            Thread.sleep(10);
          } catch (InterruptedException e) {
            System.err.println(e.getMessage());
          }
          if (++count == 200) {
            throw DbException.get(99999, new RuntimeException("Waited 2s for prewrites to complete!"));
          }
        }
      }
    }
    
    return true;
  }
  
  private void setRTM(RowOp data, HTimestamp ts) {
    HTimestamp rtm = rtmMap.getOrDefault(data.key, new HTimestamp(ts.hid, Long.MIN_VALUE));
    if (ts.greaterThan(rtm)) {
      rtmMap.put(data.key, new HTimestamp(ts.hid, ts.timestamp));
    }    
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
  
  public void commit(String remoteSid, Session localSession, HTimestamp ts, boolean grpc) {
    System.out.println(String.format("DataManager::commit(%s)", ts.toString()));
    List<Prewrite> txPrewrites = txMap.get(ts);
    if (txPrewrites == null || txPrewrites.size() == 0) {
      return;
    }

    List<Prewrite> toRemove = new ArrayList<>();

    for(Prewrite pw: txPrewrites) {
      HTimestamp minTS = pwMap.get(pw.key).peek().timestamp;
      checkAndWrite(pw, minTS, toRemove, grpc);
    }
    
    for(Prewrite pw: toRemove) {
      removeFromTxMap(pw);
      removeFromPwMap(pw);
    }
    
    CommandProcessor.getInstance().commit(remoteSid, localSession);
  }
  
  private void checkAndWrite(Prewrite pw, HTimestamp minTS, List<Prewrite> toRemove, boolean grpc) {
    System.out.println(String.format("DataManager::checkAndWrite(%s, %s, %s)", pw.toString(), String.valueOf(minTS), toRemove));
    boolean result = write(pw, grpc);
    
    if (result) {
      if (wMap.get(pw.key) != null) {
        wMap.get(pw.key).remove(pw);
      }
      
      Prewrite minP = pwMap.get(pw.key).peek();
      if (minP != null) {
        HTimestamp newMinTS = minP.timestamp;
        if (newMinTS.greaterThan(minTS) && !wMap.isEmpty()) {
          checkAndWrite(wMap.get(pw.key).peek(), newMinTS, toRemove, grpc);
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
  
  private boolean write(Prewrite pw, boolean grpc) {
    System.out.println(String.format("DataManager::write(%s)", pw.toString()));
//    rMap.computeIfAbsent(pw.key, k -> new PriorityQueue<>());
//    HTimestamp rMinTS = rMap.get(pw.key).peek();
//    if (rMinTS == null || rMinTS.greaterThanOrEqualTo(pw.timestamp)) {
      Row row = pw.data.rows.get(0);
      Row newRow = pw.data.rows.get(1);
      CommandProcessor.getInstance().rowOp(row, newRow, "", "", 
          pw.data.command, pw.data.remoteSid, pw.data.localSession);
      wtmMap.put(pw.key, pw.timestamp);
      return true;
//    }
    
//    return false;
  }
  
  public void rollback(String remoteSid, Session localSession, HTimestamp ts, boolean grpc) {
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
  
  private class PrewriteComparator implements Comparator<Prewrite> {
    @Override
    public int compare(Prewrite o1, Prewrite o2) {
      int result = o1.timestamp.compareTo(o2.timestamp);
      if (result == 0) {
        result = o1.seq.compareTo(o2.seq);
      }
      return result;
    }
  }
}
