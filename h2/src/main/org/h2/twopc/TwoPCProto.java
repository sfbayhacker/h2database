// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: twopc.proto

package org.h2.twopc;

public final class TwoPCProto {
  private TwoPCProto() {}
  public static void registerAllExtensions(
      com.google.protobuf.ExtensionRegistryLite registry) {
  }

  public static void registerAllExtensions(
      com.google.protobuf.ExtensionRegistry registry) {
    registerAllExtensions(
        (com.google.protobuf.ExtensionRegistryLite) registry);
  }
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_twopc_TwoPCRequest_descriptor;
  static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_twopc_TwoPCRequest_fieldAccessorTable;
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_twopc_TwoPCResponse_descriptor;
  static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_twopc_TwoPCResponse_fieldAccessorTable;

  public static com.google.protobuf.Descriptors.FileDescriptor
      getDescriptor() {
    return descriptor;
  }
  private static  com.google.protobuf.Descriptors.FileDescriptor
      descriptor;
  static {
    java.lang.String[] descriptorData = {
      "\n\013twopc.proto\022\005twopc\"b\n\014TwoPCRequest\022\017\n\007" +
      "command\030\001 \001(\t\022\n\n\002db\030\002 \001(\t\022\r\n\005table\030\003 \001(\t" +
      "\022\013\n\003sid\030\004 \001(\t\022\013\n\003tid\030\005 \001(\t\022\014\n\004data\030\006 \001(\014" +
      "\"\036\n\rTwoPCResponse\022\r\n\005reply\030\001 \001(\t2Q\n\020Comm" +
      "andProcessor\022=\n\016processCommand\022\023.twopc.T" +
      "woPCRequest\032\024.twopc.TwoPCResponse\"\000B$\n\014o" +
      "rg.h2.twopcB\nTwoPCProtoP\001\242\002\005TWOPCb\006proto" +
      "3"
    };
    descriptor = com.google.protobuf.Descriptors.FileDescriptor
      .internalBuildGeneratedFileFrom(descriptorData,
        new com.google.protobuf.Descriptors.FileDescriptor[] {
        });
    internal_static_twopc_TwoPCRequest_descriptor =
      getDescriptor().getMessageTypes().get(0);
    internal_static_twopc_TwoPCRequest_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_twopc_TwoPCRequest_descriptor,
        new java.lang.String[] { "Command", "Db", "Table", "Sid", "Tid", "Data", });
    internal_static_twopc_TwoPCResponse_descriptor =
      getDescriptor().getMessageTypes().get(1);
    internal_static_twopc_TwoPCResponse_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_twopc_TwoPCResponse_descriptor,
        new java.lang.String[] { "Reply", });
  }

  // @@protoc_insertion_point(outer_class_scope)
}
