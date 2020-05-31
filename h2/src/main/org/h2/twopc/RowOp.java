package org.h2.twopc;

import java.util.Arrays;
import java.util.List;

import org.h2.engine.Session;
import org.h2.result.Row;

public class RowOp {
  final List<Row> rows;
  final Row resultRow;
  final String command;
  final String remoteSid;
  final Session localSession;
  
  final String key;
  final String value;
  
  public RowOp(final Row row) {//used for local read operations
    rows = Arrays.asList(row);
    this.command = "read";
    this.remoteSid = null;
    this.localSession = null;
    
    this.key = row.getValueList()[0].toString();
    System.out.println("value - " + row.getValue(1));
    this.value = row.getValue(1).toString();
    this.resultRow = row;
  }
  
  public RowOp(final List<Row> rows, final String command, final String sid, final Session session) {
    this.rows = rows;
    this.command = command;
    this.remoteSid = sid;
    this.localSession = session;
    
    this.key = rows.get(0).getValueList()[0].toString();
    if (rows.get(1) != null) {
      System.out.println("value - " + rows.get(1).getValue(1));
      this.value = rows.get(1).getValue(1).toString();
      this.resultRow = rows.get(1);
    } else {
      System.out.println("value - " + rows.get(0).getValue(1));
      this.value = rows.get(0).getValue(1).toString();
      this.resultRow = rows.get(0);
    }
  }
  
  @Override
  public String toString() {
    return "{key: "+key+", value: "+value+", rows: "+rows+", command: "+command+", remoteSid: "+remoteSid+", localSession: "+localSession+"}";
  }
}
