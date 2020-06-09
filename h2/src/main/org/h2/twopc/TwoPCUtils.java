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
    System.out.println("Serializing object " + obj);
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
      //System.err.println("End deserialize!");
      return o;
    } finally {
      is.close();
      in.close();
    }
  }

//  public static <T> String toXML(Object o, Class<T> clazz) throws JAXBException {
//    JAXBContext jaxbContext = JAXBContext.newInstance(clazz);
//    Marshaller jaxbMarshaller = jaxbContext.createMarshaller();
//    jaxbMarshaller.setProperty(Marshaller.JAXB_ENCODING, "UTF-8");
//    jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE); // To format XML
//    StringWriter out = new StringWriter();
//    jaxbMarshaller.marshal(o, out);
//    return out.toString();
//  }
//  
//  public static <T> T fromXML(String xml, Class<T> clazz) throws JAXBException {
//    JAXBContext jc = JAXBContext.newInstance(clazz);
//    Unmarshaller unmarshaller = jc.createUnmarshaller();
//    unmarshaller.setProperty(Marshaller.JAXB_ENCODING, "UTF-8");
//    T obj = clazz.cast(unmarshaller.unmarshal(new StringReader(xml)));
//    return obj;
//  }
  
}
