package org.h2.twopc;

public class Prewrite {
  final String key;
  final String value;
  final RowOp data;
  final HTimestamp timestamp;
//  public Prewrite(String key, String value, HTimestamp timestamp) {
//    this.key = key;
//    this.value = value;
//    this.timestamp = timestamp;
//    this.data = null;
//  }

  public Prewrite(RowOp data, HTimestamp timestamp) {
    this.data = data;
    this.key = data.rows.get(0).getValueList()[0].toString();
    if (data.rows.get(1) != null) {
      System.out.println("value - " + data.rows.get(1).getValue(1));
      this.value = data.rows.get(1).getValue(1).toString();
    } else {
      System.out.println("value - " + data.rows.get(0).getValue(1));
      this.value = data.rows.get(0).getValue(1).toString();
    }
    
    this.timestamp = timestamp;
  }
  
  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((key == null) ? 0 : key.hashCode());
    result = prime * result + ((timestamp == null) ? 0 : timestamp.hashCode());
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
    return true;
  }
  
  @Override
  public String toString() {
    return "{key: "+key+", value: "+value+", data: "+data+", ts: "+timestamp+"}";
  }
}
