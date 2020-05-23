/*
 * Copyright 2015 The gRPC Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.h2.twopc;

import java.util.concurrent.TimeUnit;

import com.google.protobuf.ByteString;

import io.grpc.Channel;
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;

/**
 * A simple client that requests a greeting from the {@link HelloWorldServer}.
 */
public class TwoPCClient {
  private final CommandProcessorGrpc.CommandProcessorBlockingStub blockingStub;

  /** Construct client for accessing HelloWorld server using the existing channel. */
  public TwoPCClient(Channel channel) {
    // 'channel' here is a Channel, not a ManagedChannel, so it is not this code's responsibility to
    // shut it down.

    // Passing Channels to code makes code easier to test and makes it easier to reuse Channels.
    blockingStub = CommandProcessorGrpc.newBlockingStub(channel);
  }
  
  /** send command to server. */
  public String process(String command, String db, String table, String sid, 
      String tid, ByteString data) {
    System.out.println("Sending command - " + command);
    TwoPCRequest.Builder requestBuilder = TwoPCRequest.newBuilder().setCommand(command)
        .setDb(db).setTable(table).setSid(sid).setTid(tid);
    
    requestBuilder = requestBuilder.setData(data);
    
    TwoPCRequest request = requestBuilder.build();
    
    TwoPCResponse response;
    try {
      response = blockingStub.processCommand(request);
    } catch (StatusRuntimeException e) {
      System.err.println("RPC failed: " + e.getStatus());
      return null;
    }
    System.out.println("Reply: " + response.getReply());
    return response.getReply();
  }
  
  public void shutdown() {
    try {
      ((ManagedChannel)blockingStub.getChannel()).shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      System.err.println("Error shutting down managed channel!");
    }
  }
}
