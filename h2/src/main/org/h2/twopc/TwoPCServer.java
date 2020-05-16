package org.h2.twopc;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import io.grpc.Server;
import io.grpc.ServerBuilder;

/**
 * Server that manages startup/shutdown of a {@code Greeter} server.
 */
public class TwoPCServer {
  private Server server;

  public void start() throws IOException {
    /* The port on which the server should run */
    int port = Integer.parseInt(TwoPCCoordinator.getInstance().getGrpcPort());
    server = ServerBuilder.forPort(port)
        .addService(new CommandProcessor())
        .build()
        .start();
    System.out.println("Server started, listening on " + port);
    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        // Use stderr here since the logger may have been reset by its JVM shutdown hook.
        System.err.println("*** shutting down gRPC server since JVM is shutting down");
        try {
          TwoPCServer.this.stop();
        } catch (InterruptedException e) {
          e.printStackTrace(System.err);
        }
        System.err.println("*** server shut down");
      }
    });
  }

  public void stop() throws InterruptedException {
    if (server != null) {
      server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
    }
  }

  /**
   * Await termination on the main thread since the grpc library uses daemon threads.
   */
  public void blockUntilShutdown() throws InterruptedException {
    if (server != null) {
      server.awaitTermination();
    }
  }

  /**
   * Main launches the server from the command line.
   */
  public static void main(String[] args) throws IOException, InterruptedException {
    final TwoPCServer server = new TwoPCServer();
    server.start();
    server.blockUntilShutdown();
  }
}
