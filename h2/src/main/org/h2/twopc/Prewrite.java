package org.h2.twopc;

import java.io.Serializable;

public class Prewrite implements Serializable {
  final String key;
  final RowOp data;
  final HTimestamp timestamp;
  final Integer seq;
  final long rowKey;
//  public Prewrite(String key, String value, HTimestamp timestamp) {
//    this.key = key;
//    this.value = value;
//    this.timestamp = timestamp;
//    this.data = null;
//  }

  public Prewrite(RowOp data, HTimestamp timestamp, int seq) {
    this.data = data;
    this.key = data.key;
    this.rowKey = data.rows.get(0).getKey();
    this.timestamp = timestamp;
    this.seq = seq;
  }
  
  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((key == null) ? 0 : key.hashCode());
    result = prime * result + ((timestamp == null) ? 0 : timestamp.hashCode());
    result = prime * result + ((seq == null) ? 0 : seq.hashCode());
    return result;
  }
  
  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    Prewrite other = (Prewrite) obj;
    if (key == null) {
      if (other.key != null)
        return false;
    } else if (!key.equals(other.key))
      return false;
    if (timestamp == null) {
      if (other.timestamp != null)
        return false;
    } else if (!timestamp.equals(other.timestamp))
      return false;
    if (seq == null) {
      if (other.seq != null)
        return false;
    } else if (!seq.equals(other.seq))
      return false;
    return true;
  }
  
  @Override
  public String toString() {
    return "{key: "+key+", data: "+data+", ts: "+timestamp+", seq: "+seq+"}";
  }
}
