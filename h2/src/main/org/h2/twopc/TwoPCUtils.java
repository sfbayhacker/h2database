package org.h2.twopc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class TwoPCUtils {

  public static byte[] serialize(Object obj) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ObjectOutputStream os = new ObjectOutputStream(out);
    os.writeObject(obj);
    return out.toByteArray();
  }

  public static Object deserialize(byte[] data) throws IOException, ClassNotFoundException {
    ByteArrayInputStream in = new ByteArrayInputStream(data);
    ObjectInputStream is = new ObjectInputStream(in);
    Object o = null;

    try {
      while ((o = is.readObject()) != null) {
      }
      is.close();
      return o;
    } catch(EOFException e) {
      System.err.println("End deserialize!");
      return o;
    } finally {
      is.close();
      in.close();
    }
  }

}
