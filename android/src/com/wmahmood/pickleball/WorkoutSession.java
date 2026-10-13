package com.wmahmood.pickleball;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

/** Append-only local journal. Exports never fill gaps with an old heart rate. */
final class WorkoutSession {
    final File file;
    final long startWall, startMono;
    private long lastElapsed;
    WorkoutSession(File directory, long wall, long mono) throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create workout folder");
        startWall=wall; startMono=mono;
        file=new File(directory,"pickleball-"+wall+"-"+UUID.randomUUID().toString().substring(0,8)+".csv");
        try (Writer out=new OutputStreamWriter(new FileOutputStream(file),StandardCharsets.UTF_8)) {
            out.write("# start="+wall+"\nelapsed_ms,event,value\n");
        }
        append(0,"start","Pickleball Lab");
    }
    long elapsed(long mono) { return Math.max(lastElapsed,Math.max(0,mono-startMono)); }
    void record(long mono,String event,String value) throws IOException { append(elapsed(mono),event,value); }
    private void append(long elapsed,String event,String value) throws IOException {
        try (FileOutputStream stream=new FileOutputStream(file,true)) {
            String line=elapsed+","+clean(event)+","+clean(value)+"\n";
            stream.write(line.getBytes(StandardCharsets.UTF_8));
            stream.getFD().sync();
        }
        lastElapsed=elapsed;
    }
    private static String clean(String value) { return value.replace(',',';').replace('\n',' ').replace('\r',' '); }
    static String utc(long time) {
        SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC")); return f.format(new Date(time));
    }
    static void exportTcx(File source,OutputStream destination) throws IOException {
        long start, duration=0;
        try (BufferedReader in=new BufferedReader(new InputStreamReader(new FileInputStream(source),StandardCharsets.UTF_8))) {
            String first=in.readLine();
            if (first==null || !first.startsWith("# start=")) throw new IOException("Invalid workout file");
            try { start=Long.parseLong(first.substring(8)); } catch (NumberFormatException e) { throw new IOException("Invalid workout start",e); }
            String line;
            while ((line=in.readLine())!=null) {
                String[] p=line.split(",",3);
                try { if (p.length==3) duration=Math.max(duration,Long.parseLong(p[0])); } catch (NumberFormatException ignored) {}
            }
        }
        // TCX requires distance/calories even when unavailable; explicitly describe
        // their zero placeholders. No synthetic GPS, HR samples or activity metrics.
        Writer out=new OutputStreamWriter(destination,StandardCharsets.UTF_8);
        out.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<TrainingCenterDatabase xmlns=\"http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2\"><Activities><Activity Sport=\"Other\"><Id>"+utc(start)+"</Id><Lap StartTime=\""+utc(start)+"\"><TotalTimeSeconds>"+(duration/1000.0)+"</TotalTimeSeconds><DistanceMeters>0</DistanceMeters><Calories>0</Calories><Intensity>Active</Intensity><TriggerMethod>Manual</TriggerMethod>");
        boolean track=false;
        try (BufferedReader in=new BufferedReader(new InputStreamReader(new FileInputStream(source),StandardCharsets.UTF_8))) {
            String line;
            while ((line=in.readLine())!=null) {
                String[] p=line.split(",",3);
                if (p.length!=3 || !p[1].equals("heartRate")) continue;
                try {
                    long elapsed=Long.parseLong(p[0]); int bpm=Integer.parseInt(p[2]);
                    if (elapsed<0 || bpm<1 || bpm>255) continue;
                    if (!track) { out.write("<Track>"); track=true; }
                    out.write("<Trackpoint><Time>"+utc(start+elapsed)+"</Time><HeartRateBpm><Value>"+bpm+"</Value></HeartRateBpm></Trackpoint>");
                } catch (NumberFormatException ignored) {}
            }
        }
        if (track) out.write("</Track>");
        out.write("</Lap><Notes>Pickleball Lab experimental session. Distance and calories are unmeasured; required zero fields are placeholders. Heart-rate gaps are not filled. Duration ends at the last saved event if recording was interrupted.</Notes></Activity></Activities></TrainingCenterDatabase>");
        out.flush();
    }
}
