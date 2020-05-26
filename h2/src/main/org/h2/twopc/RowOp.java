package org.h2.twopc;

import java.util.List;

import org.h2.engine.Session;
import org.h2.result.Row;

public class RowOp {
  final List<Row> rows;
  final String command;
  final String remoteSid;
  final Session localSession;
  
  public RowOp(List<Row> rows, String command, String sid, Session session) {
    this.rows = rows;
    this.command = command;
    this.remoteSid = sid;
    this.localSession = session;
  }
}
