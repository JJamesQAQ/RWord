package com.riderword.plugin;

public final class RememberConfirmation {
    private Entry pending;
    private int originalMode;

    public boolean isPending(Entry current, int mode) {
        // A replacement entry or changed display preference invalidates the preview.
        if (pending != current || originalMode != mode) {
            cancel();
        }
        return pending != null;
    }

    public boolean click(Entry current, int mode) {
        if (current == null) {
            cancel();
            return false;
        }
        if (isPending(current, mode)) {
            cancel();
            return true;
        }
        pending = current;
        originalMode = mode;
        return false;
    }

    public void cancel() {
        pending = null;
    }
}
