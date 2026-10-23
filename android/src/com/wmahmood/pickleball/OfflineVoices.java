package com.wmahmood.pickleball;

import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import java.util.*;

/** Shared eligibility and ordering for the chooser and actual speech output. */
final class OfflineVoices {
    static List<Voice> available(TextToSpeech tts) {
        List<Voice> result=new ArrayList<>();
        Set<Voice> all=tts.getVoices();
        if (all != null) for (Voice voice : all) {
            if (eligible(voice.getLocale(),voice.isNetworkConnectionRequired(),voice.getFeatures())) result.add(voice);
        }
        result.sort(Comparator.comparingInt((Voice v) -> v.getLocale().equals(Locale.US) ? 0 : 1)
            .thenComparing(v -> v.getLocale().toLanguageTag()).thenComparing(Voice::getName));
        return result;
    }
    static boolean eligible(Locale locale,boolean network,Set<String> features) {
        return locale != null && locale.getLanguage().equals("en") && !network &&
            (features == null || !features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED));
    }
    static String label(Voice voice) { return voice.getLocale().getDisplayName(Locale.ENGLISH) + " · " + voice.getName(); }
}
