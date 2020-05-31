package org.h2.twopc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.h2.engine.Session;
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
  
  public Row get(long rowKey, HTimestamp ts) {
    String key = null;
    
    if ( (key = keyMap.get(rowKey)) == null) {
      return null;
    }
    
    PriorityQueue<Prewrite> q = pwMap.get(key);
    
    if (q == null || q.isEmpty()) {
      return null;
    }
    
    Iterator<Prewrite> it = q.iterator();
    while(it.hasNext()) {
      Prewrite pw = it.next();
      if (key.equals(pw.key)) {
        return pw.data.resultRow;
      }
    }
    
    return null;
  }
  
  public boolean read(RowOp data, HTimestamp ts) {
    HTimestamp wtm = wtmMap.getOrDefault(data.key, new HTimestamp(ts.hid, Long.MIN_VALUE));
    if (ts.compareTo(wtm) < 0) {
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
      setRTM(data, ts);
    } else {
      HTimestamp minTS = pwMap.get(data.key).peek().timestamp;
      if (ts.compareTo(minTS) <= 0) {
        setRTM(data, ts);
      } else {
        while(true) {
          minTS = pwMap.get(data.key).peek().timestamp;
          if (ts.compareTo(minTS) <= 0) {
            return true;
          }
          
          try {
            Thread.sleep(10);
          } catch (InterruptedException e) {
            System.err.println(e.getMessage());
          }
        }
      }
    }
    
    return true;
  }
  
  private void setRTM(RowOp data, HTimestamp ts) {
    HTimestamp rtm = rtmMap.getOrDefault(data.key, new HTimestamp(ts.hid, Long.MIN_VALUE));
    if (ts.compareTo(rtm) > 0) {
      rtmMap.put(data.key, new HTimestamp(ts.hid, ts.timestamp));
    }    
  }
  
  public boolean prewrite(RowOp data, HTimestamp ts) {
    Prewrite pw = new Prewrite(data, ts);
    HTimestamp wtm = wtmMap.getOrDefault(pw.key, new HTimestamp(ts.hid, Long.MIN_VALUE));
    if (ts.compareTo(wtm) < 0) return false;
    
    pwMap.computeIfAbsent(pw.key, k -> new PriorityQueue<>(new PrewriteComparator()));
    pwMap.get(pw.key).removeIf(e -> e.equals(pw));
    pwMap.get(pw.key).add(pw);
    keyMap.put(pw.rowKey, pw.key);
    txMap.computeIfAbsent(ts, k -> new HashSet<>());
    txMap.get(ts).add(pw);
    return true;
  }
  
  public void commit(String remoteSid, Session localSession, HTimestamp ts, boolean grpc) {
    System.out.println(String.format("commit(%s)", ts.toString()));
    Set<Prewrite> txPrewrites = txMap.get(ts);
    if (txPrewrites == null || txPrewrites.size() == 0) {
      return;
    }

    List<Prewrite> toRemove = new ArrayList<>();

    for(Prewrite pw: txPrewrites) {
      long minTS = pwMap.get(pw.key).peek().timestamp.timestamp;
      checkAndWrite(pw, minTS, toRemove, grpc);
    }
    
    for(Prewrite pw: toRemove) {
      txMap.get(pw.timestamp).remove(pw);
      if (txMap.get(pw.timestamp).isEmpty()) {
        txMap.remove(pw.timestamp);
      }
    }
    
    CommandProcessor.getInstance().commit(remoteSid, localSession);
  }
  
  private void checkAndWrite(Prewrite pw, long minTS, List<Prewrite> toRemove, boolean grpc) {
    System.out.println(String.format("checkAndWrite(%s, %s, %s)", pw.toString(), String.valueOf(minTS), toRemove));
    boolean result = write(pw, grpc);
    
    if (result) {
      pwMap.get(pw.key).removeIf(e -> e.equals(pw));
      if (pwMap.get(pw.key) == null || pwMap.get(pw.key).isEmpty()) {
        keyMap.remove(pw.rowKey);
      }
      if (wMap.get(pw.key) != null) {
        wMap.get(pw.key).removeIf(e -> e.equals(pw));
      }
      
      Prewrite minP = pwMap.get(pw.key).peek();
      if (minP != null) {
        long newMinTS = minP.timestamp.timestamp;
        if (newMinTS > minTS && !wMap.isEmpty()) {
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
    System.out.println(String.format("write(%s)", pw.toString()));
    rMap.computeIfAbsent(pw.key, k -> new PriorityQueue<>());
    HTimestamp rMinTS = rMap.get(pw.key).peek();
    if (rMinTS == null || rMinTS.compareTo(pw.timestamp) >= 0) {
      Row row = pw.data.rows.get(0);
      Row newRow = pw.data.rows.get(1);
      CommandProcessor.getInstance().rowOp(row, newRow, "", "", 
          pw.data.command, pw.data.remoteSid, pw.data.localSession);
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
