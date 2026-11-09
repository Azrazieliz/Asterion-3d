package com.asterion.foundry;
import android.content.*;import android.database.*;import android.database.sqlite.*;import org.json.*;import java.io.*;import java.util.*;
/** Durable queue and versioned local production memory. No asset sources are deleted. */
public final class Store extends SQLiteOpenHelper {
 final File root;
 public Store(Context c){super(c,"production.db",null,1);root=new File(c.getFilesDir(),"foundry");for(String d:new String[]{"sources","projects","cache","verified","mobile","archive"})new File(root,d).mkdirs();}
 public void onCreate(SQLiteDatabase d){d.execSQL("CREATE TABLE jobs(id TEXT PRIMARY KEY, request TEXT NOT NULL, state TEXT NOT NULL, stage INTEGER NOT NULL DEFAULT 0, message TEXT NOT NULL DEFAULT '', created INTEGER NOT NULL)");d.execSQL("CREATE TABLE history(id INTEGER PRIMARY KEY, job TEXT, recipe TEXT, duration_ms INTEGER, reused INTEGER, receipt TEXT, recorded INTEGER)");}
 public void onUpgrade(SQLiteDatabase d,int a,int b){throw new IllegalStateException("Unsupported database migration");}
 public synchronized String enqueue(JSONObject req)throws Exception{String id=UUID.randomUUID().toString();ContentValues v=new ContentValues();v.put("id",id);v.put("request",req.toString());v.put("state","queued");v.put("created",System.currentTimeMillis());getWritableDatabase().insertOrThrow("jobs",null,v);return id;}
 public synchronized void state(String id,String s,int stage,String message){ContentValues v=new ContentValues();v.put("state",s);v.put("stage",stage);v.put("message",message);getWritableDatabase().update("jobs",v,"id=?",new String[]{id});}
 public synchronized JSONObject job(String id)throws Exception{try(Cursor c=getReadableDatabase().rawQuery("SELECT * FROM jobs WHERE id=?",new String[]{id})){if(!c.moveToFirst())throw new IOException("Job not found");return row(c);}}
 JSONObject row(Cursor c)throws Exception{JSONObject o=new JSONObject();for(String s:new String[]{"id","request","state","message"})o.put(s,c.getString(c.getColumnIndexOrThrow(s)));o.put("stage",c.getInt(c.getColumnIndexOrThrow("stage")));return o;}
 public synchronized ArrayList<JSONObject> jobs()throws Exception{ArrayList<JSONObject> a=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT * FROM jobs ORDER BY created DESC",null)){while(c.moveToNext())a.add(row(c));}return a;}
 public synchronized JSONObject nextQueued() throws Exception {
  try (Cursor cursor = getReadableDatabase().rawQuery(
      "SELECT * FROM jobs WHERE state='queued' ORDER BY created, rowid LIMIT 1", null)) {
   return cursor.moveToFirst() ? row(cursor) : null;
  }
 }
 public synchronized boolean hasQueued() {
  try (Cursor cursor = getReadableDatabase().rawQuery("SELECT 1 FROM jobs WHERE state='queued' LIMIT 1", null)) {
   return cursor.moveToFirst();
  }
 }
 public synchronized void finish(String id, String recipe, long ms, boolean reused, JSONObject receipt) {
  finish(id, recipe, ms, reused, receipt, "complete",
          "Export ready · visual quality unvalidated" + (reused ? " · reused geometry" : ""));
 }
 public synchronized void finishCandidate(String id, String recipe, long ms, boolean reused, JSONObject receipt) {
  finish(id, recipe, ms, reused, receipt, "candidate", "Preview only · reference fidelity and rigging unverified");
 }
 private void finish(String id, String recipe, long ms, boolean reused, JSONObject receipt,
                     String outcome, String message) {
  SQLiteDatabase database = getWritableDatabase();
  database.beginTransaction();
  try {
   record(id, recipe, ms, reused, receipt);
   state(id, outcome, 4, message);
   database.setTransactionSuccessful();
  } finally {
   database.endTransaction();
  }
 }
 public synchronized void recover(){getWritableDatabase().execSQL("UPDATE jobs SET state='paused',message='Interrupted. Resume from durable checkpoint.' WHERE state='running'");}
 public synchronized void record(String id,String recipe,long ms,boolean reused,JSONObject receipt){ContentValues v=new ContentValues();v.put("job",id);v.put("recipe",recipe);v.put("duration_ms",ms);v.put("reused",reused?1:0);v.put("receipt",receipt.toString());v.put("recorded",System.currentTimeMillis());getWritableDatabase().insertOrThrow("history",null,v);}
 public String memory(){try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*),COALESCE(SUM(reused),0),COALESCE(AVG(duration_ms),0) FROM history",null)){c.moveToFirst();return c.getInt(0)+" recorded operations · "+c.getInt(1)+" cache reuses · "+c.getLong(2)+" ms average";}}
 public File project(String id){File f=new File(root,"projects/"+id);f.mkdirs();return f;}
}
