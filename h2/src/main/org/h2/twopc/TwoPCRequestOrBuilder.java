// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: twopc.proto

package org.h2.twopc;

public interface TwoPCRequestOrBuilder extends
    // @@protoc_insertion_point(interface_extends:twopc.TwoPCRequest)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <code>string command = 1;</code>
   * @return The command.
   */
  java.lang.String getCommand();
  /**
   * <code>string command = 1;</code>
   * @return The bytes for command.
   */
  com.google.protobuf.ByteString
      getCommandBytes();

  /**
   * <code>string tid = 2;</code>
   * @return The tid.
   */
  java.lang.String getTid();
  /**
   * <code>string tid = 2;</code>
   * @return The bytes for tid.
   */
  com.google.protobuf.ByteString
      getTidBytes();

  /**
   * <code>bytes data = 3;</code>
   * @return The data.
   */
  com.google.protobuf.ByteString getData();
}