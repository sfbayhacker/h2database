package org.h2.twopc;

import java.io.Serializable;

public class HTimestamp implements Comparable<HTimestamp>, Serializable {
  int hid;
  long timestamp;
  
  public HTimestamp(int hid, long timestamp) {
    this.hid = hid;
    this.timestamp = timestamp;
  }

  public boolean lessThan(HTimestamp other) {
    return compareTo(other) < 0;
  }
  
  public boolean lessThanOrEqualTo(HTimestamp other) {
    return compareTo(other) <= 0;
  }
  
  public boolean greaterThan(HTimestamp other) {
    return compareTo(other) > 0;
  }
  
  public boolean greaterThanOrEqualTo(HTimestamp other) {
    return compareTo(other) >= 0;
  }
  
  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + hid;
    result = prime * result + (int) (timestamp ^ (timestamp >>> 32));
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
    HTimestamp other = (HTimestamp) obj;
    if (hid != other.hid)
      return false;
    if (timestamp != other.timestamp)
      return false;
    return true;
  }

  @Override
  public int compareTo(HTimestamp o) {
    int result = Long.valueOf(timestamp).compareTo(o.timestamp);
    
    if (result == 0) {
      result = -1 * Integer.valueOf(hid).compareTo(o.hid);
    }
    
    return result;
  }
  
  @Override
  public String toString() {
    return "{hid: "+hid+ "; timestamp: "+timestamp+"}";
  }
}
