package com.asterion.foundry;
import java.io.*;import java.nio.file.*;import java.security.*;
public final class FilesUtil {
 public static void move(File a,File b)throws IOException{try{Files.move(a.toPath(),b.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(a.toPath(),b.toPath(),StandardCopyOption.REPLACE_EXISTING);}}
 public static void write(File f,String s)throws IOException{write(f,s.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
 public static void write(File f,byte[] data)throws IOException{File tmp=new File(f+".tmp");try(FileOutputStream o=new FileOutputStream(tmp)){o.write(data);o.getFD().sync();}move(tmp,f);}
 public static String hash(File f)throws Exception{MessageDigest d=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(f)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)d.update(b,0,n);}return hex(d.digest());}
 public static String hash(String s)throws Exception{return hex(MessageDigest.getInstance("SHA-256").digest(s.getBytes("UTF-8")));}
 static String hex(byte[] a){StringBuilder b=new StringBuilder();for(byte v:a)b.append(String.format("%02x",v&255));return b.toString();}
 /** Android-compatible UTF-8 file text read (no Java 11 Files.readString stub). */
 public static String readString(Path path)throws IOException{return readString(path,java.nio.charset.StandardCharsets.UTF_8);}
 public static String readString(Path path,java.nio.charset.Charset charset)throws IOException{
  return new String(Files.readAllBytes(path),charset);
 }
 public static String quote(String s){return "\""+s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r").replace("\t","\\t")+"\"";}
}
