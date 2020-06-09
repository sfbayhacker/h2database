package org.h2.twopc;

import java.io.IOException;
import java.util.Properties;

import org.h2.engine.Constants;
import org.h2.util.SortedProperties;

public class ClusterInfo {
  private String[] cohorts;
  private int hostId;
  private Properties props;
  private String grpcPort;
  private boolean clustered;
  private boolean dummy;
  private boolean dieOnPrepare;
  private String coordinator;
  private boolean follower;
  
  private static class InstanceHolder {
    private static ClusterInfo INSTANCE = new ClusterInfo();
  }

  public static ClusterInfo getInstance() {
    return InstanceHolder.INSTANCE;
  }
  
  private ClusterInfo() {
    try {
      props = readProperties();
      hostId = props.get("hostId") == null ? 0 : Integer.parseInt(props.get("hostId").toString());
      grpcPort = props.get("grpcPort") == null ? "50051" : props.get("grpcPort").toString();
      Object peers = props.get("peerAddresses");
      if ((clustered = peers != null)) {
        if (peers.toString().isEmpty()) {
          dummy = true;
          System.out.println("Running in dummy coordinator mode!");
        } else {
          cohorts = peers.toString().split("\\|");
        }
      }
      Object dop = props.get("dieOnPrepare");
      dieOnPrepare = (dop != null && "y".equalsIgnoreCase(dop.toString()));
      coordinator = props.get("coordinator") == null ? null : props.get("coordinator").toString();
      follower = coordinator != null;
    } catch (Exception e) {
      System.err.println("Error loading properties!");
    }
//    this.cohorts = new String[] {"10.1.10.181:50051"};
//    this.hostId = "1";
  }
  
  private Properties readProperties() throws IOException {
    String serverPropertiesDir = Constants.SERVER_PROPERTIES_DIR;
    if ("null".equals(serverPropertiesDir)) {
      return null;
    }

    Properties props = SortedProperties.loadProperties(serverPropertiesDir + "/" + Constants.CLUSTER_PROPERTIES_NAME);

    return props;
  }

  public String[] getCohorts() {
    return cohorts;
  }

  public int getHostId() {
    return hostId;
  }

  public String getGrpcPort() {
    return grpcPort;
  }

  public boolean isClustered() {
    return clustered;
  }
  
  public boolean isDummy() {
    return dummy;
  }
  
  public boolean dieOnPrepare() {
    return dieOnPrepare;
  }
  
  public void setDieOnPrepare(boolean flag) {
    this.dieOnPrepare = flag;
  }
  
  public String getCoordinator() {
    return coordinator;
  }
  
  public boolean isFollower() {
    return follower;
  }
}
