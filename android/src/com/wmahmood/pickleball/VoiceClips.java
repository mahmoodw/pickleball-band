package com.wmahmood.pickleball;

import java.io.*;
import java.util.*;

/** Maps the scorer's fixed vocabulary to PCM clips without changing score rules. */
final class VoiceClips {
    static final int RATE=24000, MAX_CLIP_BYTES=RATE*2*6, MAX_CALL_BYTES=RATE*2*11;
    private static final Map<String,String> WORDS=new HashMap<>();
    static {
        for (int i=0;i<100;i++) WORDS.put(Score.word(i),String.format(Locale.ROOT,"n%02d",i));
        WORDS.put("you just got pickled","pickled"); WORDS.put("turnover","turnover");
        WORDS.put("correction","correction"); WORDS.put("game","game");
        WORDS.put("we win","we_win"); WORDS.put("they win","they_win"); WORDS.put("to","to");
    }
    static List<String> plan(String text) {
        List<String> clips=new ArrayList<>();
        if (text==null || text.length()>256) throw new IllegalArgumentException("Invalid announcement");
        for (String sentence : text.toLowerCase(Locale.ROOT).split("[.!]")) {
            sentence=sentence.trim();
            if (sentence.isEmpty()) continue;
            String key=WORDS.get(sentence);
            if (key!=null) clips.add(key);
            else {
                String[] scores=sentence.split(" to ",-1);
                if (scores.length!=2 || !WORDS.containsKey(scores[0]) || !WORDS.containsKey(scores[1]) ||
                    !WORDS.get(scores[0]).startsWith("n") || !WORDS.get(scores[1]).startsWith("n"))
                    throw new IllegalArgumentException("Unknown announcement words");
                clips.add(WORDS.get(scores[0])); clips.add("to"); clips.add(WORDS.get(scores[1]));
            }
        }
        if (clips.isEmpty() || clips.size()>10) throw new IllegalArgumentException("Invalid announcement length");
        return Collections.unmodifiableList(clips);
    }
    interface Reader { InputStream open(String path) throws IOException; }
    static byte[] assemble(String voice,List<String> clips,Reader source) throws IOException {
        if (!AnnouncerVoice.find(voice).id.equals(voice) || !AnnouncerVoice.find(voice).recorded()) throw new IOException("Unknown recorded voice");
        ByteArrayOutputStream result=new ByteArrayOutputStream();
        for (String key : clips) {
            if (!WORDS.containsValue(key)) throw new IOException("Unknown clip");
            if (result.size()>0) result.write(new byte[RATE*2*160/1000]);
            int size=0;
            try (InputStream in=source.open("voices/"+voice+"/"+key+".pcm")) {
                byte[] buffer=new byte[8192]; int count;
                while ((count=in.read(buffer))!=-1) {
                    size+=count;
                    if (size>MAX_CLIP_BYTES || result.size()+count>MAX_CALL_BYTES) throw new IOException("Voice clip too long");
                    result.write(buffer,0,count);
                }
            }
            if (size<2 || size%2!=0) throw new IOException("Invalid voice clip");
        }
        if (result.size()==0) throw new IOException("Empty announcement");
        return result.toByteArray();
    }
}
