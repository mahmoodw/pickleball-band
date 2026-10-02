package com.wmahmood.pickleball;

/** Tracks discovery separately from choosing a result; callbacks run on the UI thread. */
final class BandSelection {
    private enum Stage { IDLE, DISCOVERING, CHOOSING, SELECTED, FAILED }
    private Stage stage = Stage.IDLE;
    private long attempt;
    private String status = "Select your band, then start the announcer.";

    long begin() {
        ++attempt;
        stage = Stage.DISCOVERING;
        status = "Looking for bands in Notify...";
        return attempt;
    }

    boolean discovered(long request, int count) {
        if (!waiting(request)) return false;
        stage = count > 0 ? Stage.CHOOSING : Stage.FAILED;
        status = count > 0 ? "Notify responded. Choose your band." : "Notify returned no connected bands. Check the connection in Notify.";
        return true;
    }

    boolean select(long request) {
        if (request != attempt || stage != Stage.CHOOSING) return false;
        stage = Stage.SELECTED;
        status = "Band selected. Tap Start announcer, then open Pickleball on your band.";
        return true;
    }

    boolean failed(long request, String message) {
        if (!waiting(request)) return false;
        stage = Stage.FAILED;
        status = "Band discovery failed: " + message;
        return true;
    }

    boolean timedOut(long request) {
        return failed(request, "Notify has not returned the band list. Open Notify, then tap Select band to retry.");
    }

    void cancel() {
        ++attempt;
        stage = Stage.IDLE;
        status = "Selection cancelled. Your previously selected band is unchanged.";
    }

    String status() { return status; }
    private boolean waiting(long request) { return request == attempt && stage == Stage.DISCOVERING; }
}
