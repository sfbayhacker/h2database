package org.h2.twopc;

import java.util.List;

import org.h2.result.Row;

public class RowOp {
  final List<Row> rows;
  final String command;
  final String sid;
  
  public RowOp(List<Row> rows, String command, String sid) {
    this.rows = rows;
    this.command = command;
    this.sid = sid;
  }
}
