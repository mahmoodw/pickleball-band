package com.wmahmood.pickleball;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;

public final class WorkoutTest {
    static int checks;
    static javax.xml.validation.Schema schema;
    static void check(boolean ok,String message) { checks++; if (!ok) throw new AssertionError(message); }
    static int parse(int... bytes) { byte[] b=new byte[bytes.length]; for (int i=0;i<b.length;i++) b[i]=(byte)bytes[i]; return HeartRateMeasurement.parse(b); }
    static Document tcx(File file) throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream(); WorkoutSession.exportTcx(file,out);
        DocumentBuilderFactory f=DocumentBuilderFactory.newInstance(); f.setNamespaceAware(true);
        Document document=f.newDocumentBuilder().parse(new ByteArrayInputStream(out.toByteArray()));
        if (schema!=null) schema.newValidator().validate(new javax.xml.transform.dom.DOMSource(document));
        return document;
    }
    public static void main(String[] args) throws Exception {
        if (args.length>0) schema=javax.xml.validation.SchemaFactory.newInstance(javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI).newSchema(new File(args[0]));
        check(parse(0,120)==120,"8-bit HR");
        check(parse(1,200,0)==200,"16-bit HR");
        check(parse(6,85)==85,"Skin contact present");
        check(parse(4,85)==-1,"Off-wrist reading rejected");
        check(parse(0,0)==-1,"Zero is missing, not a sample");
        check(parse(1,0,1)==-1,"Out of range HR rejected");
        check(HeartRateMeasurement.parse(null)==-1,"Null packet");
        check(parse()==-1 && parse(0)==-1 && parse(1,85)==-1,"Truncated packets");
        check(parse(8,100,0)==-1 && parse(8,100,0,0)==100,"Energy field length");
        check(parse(16,100)==-1 && parse(16,100,1)==-1 && parse(16,100,1,0)==100,"RR field length");
        check(parse(25,100,0,0,0,1,0)==100,"16-bit HR, energy and RR");
        check(!HeartRateMeasurement.fresh(-1,1000),"No reading never fresh");
        check(HeartRateMeasurement.fresh(0,15000),"Fresh boundary");
        check(!HeartRateMeasurement.fresh(0,15001),"Stale boundary");
        check(!HeartRateMeasurement.fresh(100,50),"Clock reversal not fresh");
        File dir=Files.createTempDirectory("pickleball-workout-test").toFile();
        long start=1700000000000L;
        WorkoutSession session=new WorkoutSession(dir,start,10000);
        session.record(11000,"heartRate","100");
        session.record(12000,"score","rally 1 - 0 - 2");
        session.record(41000,"heartRate","125");
        session.record(42000,"finish","Finished");
        check(session.elapsed(5000)==32000,"Elapsed time never goes backwards");
        Document doc=tcx(session.file);
        check(doc.getElementsByTagName("Trackpoint").getLength()==2,"No synthetic HR through a 30-second gap");
        check(doc.getElementsByTagName("Time").item(0).getTextContent().equals(WorkoutSession.utc(start+1000)),"Sample timestamp from monotonic offset");
        check(doc.getElementsByTagName("Time").item(1).getTextContent().equals(WorkoutSession.utc(start+31000)),"Gap preserved in TCX");
        check(doc.getElementsByTagName("TotalTimeSeconds").item(0).getTextContent().equals("32.0"),"Recorded duration");
        check(doc.getElementsByTagName("Notes").item(0).getTextContent().contains("unmeasured"),"Unavailable metrics disclosed");
        check(doc.getElementsByTagName("Position").getLength()==0,"No invented GPS");
        WorkoutSession timer=new WorkoutSession(dir,start,0);
        timer.record(5000,"tick","");
        // A killed process can leave a partial trailing write. Export saved rows.
        try (FileOutputStream out=new FileOutputStream(timer.file,true)) { out.write("7000,he".getBytes(StandardCharsets.UTF_8)); }
        doc=tcx(timer.file);
        check(doc.getElementsByTagName("TotalTimeSeconds").item(0).getTextContent().equals("5.0"),"Interrupted duration ends at last saved event");
        check(doc.getElementsByTagName("Track").getLength()==0,"Timer-only session has no empty or fabricated HR track");
        check(!session.file.equals(timer.file),"Unique files even at same start time");
        WorkoutSession sanitised=new WorkoutSession(dir,start,0);
        sanitised.record(5,"status","hello,world\nmore\rdata");
        String csv=new String(Files.readAllBytes(sanitised.file.toPath()),StandardCharsets.UTF_8);
        check(csv.contains("5,status,hello;world more data\n"),"Journal fields cannot inject rows");
        System.out.println(checks+" workout parser/journal/export checks passed");
    }
}
