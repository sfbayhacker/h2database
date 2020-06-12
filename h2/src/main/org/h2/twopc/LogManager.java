package org.h2.twopc;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.h2.store.fs.FileUtils;
import org.h2.twopc.DataManager.MapData;
import org.h2.util.StringUtils;

public class LogManager {
//  final String PREWRITE_LOG_FILE_PATH = Constants.SERVER_PROPERTIES_DIR + "/pw_log.json";
  final String WRITE_LOG_FILE_PATH = System.getProperty("user.home") + "/data.log";
  final String TWOPC_LOG_FILE_PATH = System.getProperty("user.home") + "/2pc.log";
  
  final ArrayList<LogEntry> logEntries = new ArrayList<>();
  
  private static class InstanceHolder {
    private static LogManager INSTANCE = new LogManager();
  }
  
  private LogManager() {
  }
  
  public static LogManager getInstance() {
    return InstanceHolder.INSTANCE;
  } 
  
  public void init() {
    try {
      readLog();
    } catch (IOException e) {
      //System.err.println("Error while loading log file: " + e.getMessage());
    }
  }
  
  boolean appendLogEntry(String txnId, String status) {
    if (StringUtils.isNullOrEmpty(txnId) || StringUtils.isNullOrEmpty(status)) return false;
    status = status.toLowerCase();
    logEntries.add(new LogEntry(txnId, status));
    try(PrintWriter output = new PrintWriter(new FileWriter(TWOPC_LOG_FILE_PATH, true))) 
    {
        output.printf("%s,%s%s", txnId, status, System.lineSeparator());
    } 
    catch (Exception e) {
      System.err.println(e.getMessage());
      return false;
    }
    
    return true;
  }
  
  void readLog() throws IOException {
    BufferedReader reader = new BufferedReader(new FileReader(TWOPC_LOG_FILE_PATH));
    logEntries.clear();
    try {
      String line = reader.readLine();
      while(line != null) {
        String[] values = line.split(",");
        logEntries.add(new LogEntry(values[0], values[1]));
        line = reader.readLine();
      }
    } finally {
      reader.close();
    }
  }
  
  List<HTimestamp> getPreparedTransactions() {
    if (logEntries.isEmpty()) Collections.emptyList();
    Map<String, String> finalStatusMap = new HashMap<>();
    logEntries.stream().forEach(e -> {
      finalStatusMap.put(e.txnId, e.status);
    });
    return finalStatusMap.keySet().stream().filter(k -> "prepare".equals(finalStatusMap.get(k)))
        .map(k -> {String[] values = k.split("#"); return new HTimestamp(Integer.parseInt(values[0]), Long.parseLong(values[1]));})
        .collect(Collectors.toList());
  }
  
  void flush(MapData data) throws IOException {
    System.out.println("flush()");
    System.out.println("realPath - " + FileUtils.toRealPath(WRITE_LOG_FILE_PATH));
//    ObjectMapper mapper = new ObjectMapper();
//    mapper.setVisibility(PropertyAccessor.FIELD, Visibility.ANY);
//    mapper.writeValue(new File(FileUtils.toRealPath(WRITE_LOG_FILE_PATH)), data);
    byte[] bytes = TwoPCUtils.serialize(data);
    OutputStream os = new FileOutputStream(new File(FileUtils.toRealPath(WRITE_LOG_FILE_PATH)));
    os.write(bytes); 
    os.close();
  }

  void flushState() throws IOException {
    System.out.println("flush()");
    System.out.println("realPath - " + FileUtils.toRealPath(WRITE_LOG_FILE_PATH));
    byte[] bytes = TwoPCUtils.serialize(DataManager.getInstance());
    OutputStream os = new FileOutputStream(new File(FileUtils.toRealPath(WRITE_LOG_FILE_PATH)));
    os.write(bytes); 
    os.close();
  }
  
  MapData restore() throws ClassNotFoundException, IOException {
    System.out.println("restore()");
//    ObjectMapper mapper = new ObjectMapper();
//    mapper.setVisibility(PropertyAccessor.FIELD, Visibility.ANY);
//    File f1 = new File(FileUtils.toRealPath(WRITE_LOG_FILE_PATH));
//    MapData data = mapper.readValue(f1, new TypeReference<MapData>() {});

    byte[] bytes = Files.readAllBytes(Paths.get(WRITE_LOG_FILE_PATH));
    MapData data = (MapData) TwoPCUtils.deserialize(bytes);
    System.out.println("pwMap :: " + data.pwMap);
    System.out.println("wMap :: " + data.wMap);
    return data;
  }

  DataManager restoreState() throws ClassNotFoundException, IOException {
    System.out.println("restore()");

    byte[] bytes = Files.readAllBytes(Paths.get(WRITE_LOG_FILE_PATH));
    DataManager dm = (DataManager) TwoPCUtils.deserialize(bytes);
    System.out.println("pwMap :: " + dm.pwMap);
    System.out.println("wMap :: " + dm.wMap);
    return dm;
  }
  
  static class LogEntry {
    String txnId;
    String status;
    
    public LogEntry(String txnId, String status) {
      this.txnId = txnId;
      this.status = status;
    }

    @Override
    public String toString() {
      return String.format("{%s, %s}", txnId, status);
    }
  }
}
