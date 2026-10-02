package com.wmahmood.pickleball;

/** Replays delayed callbacks, including the false timeout reported on Notify 23.8.4. */
public final class BandSelectionTest {
    static int checks;
    static void check(boolean ok) { checks++; if (!ok) throw new AssertionError("Check " + checks); }
    public static void main(String[] args) {
        BandSelection flow = new BandSelection();
        long first = flow.begin();
        check(flow.discovered(first, 1));
        check(!flow.timedOut(first)); // User can spend more than eight seconds choosing.
        check(flow.select(first));
        String selected = flow.status();
        check(selected.startsWith("Band selected."));
        check(!flow.timedOut(first)); // The reported regression: success followed by timeout.
        check(!flow.failed(first, "late error"));
        check(flow.status().equals(selected));

        long second = flow.begin();
        long third = flow.begin();
        check(!flow.discovered(second, 1));
        check(!flow.timedOut(second));
        check(flow.discovered(third, 2));
        check(!flow.select(second));
        check(flow.select(third));

        long empty = flow.begin();
        check(flow.discovered(empty, 0));
        check(!flow.select(empty));
        check(!flow.timedOut(empty));
        check(flow.status().contains("no connected bands"));

        long timeout = flow.begin();
        check(flow.timedOut(timeout));
        check(flow.status().contains("has not returned the band list"));
        check(!flow.discovered(timeout, 1)); // Retry must not open an old dialog.
        long retry = flow.begin();
        check(flow.discovered(retry, 1));
        check(flow.select(retry));

        long cancelled = flow.begin();
        flow.cancel();
        check(!flow.discovered(cancelled, 1));
        check(!flow.timedOut(cancelled));
        long destroyed = flow.begin();
        check(flow.discovered(destroyed, 1));
        flow.cancel();
        check(!flow.select(destroyed));

        long failed = flow.begin();
        check(flow.failed(failed, "example service error"));
        check(flow.status().contains("example service error"));
        check(!flow.timedOut(failed));
        System.out.println(checks + " band selection callback checks passed");
    }
}
