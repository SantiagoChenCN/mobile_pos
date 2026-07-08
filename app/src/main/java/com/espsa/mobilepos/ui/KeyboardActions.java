package com.espsa.mobilepos.ui;

import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

public final class KeyboardActions {
    private KeyboardActions() {
    }

    public static void bindSearchAction(EditText input, Runnable action) {
        bindAction(input, action, EditorInfo.IME_ACTION_SEARCH);
    }

    public static void bindDoneAction(EditText input, Runnable action) {
        bindAction(input, action, EditorInfo.IME_ACTION_DONE);
    }

    private static void bindAction(EditText input, Runnable action, int imeAction) {
        input.setImeOptions(imeAction);
        input.setSingleLine(true);
        input.setOnEditorActionListener((view, actionId, event) -> {
            if (isKeyboardAction(actionId, event, imeAction)) {
                action.run();
                return true;
            }
            return false;
        });
    }

    private static boolean isKeyboardAction(int actionId, KeyEvent event, int imeAction) {
        if (actionId == imeAction || actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
            return true;
        }
        return event != null
                && event.getAction() == KeyEvent.ACTION_UP
                && event.getKeyCode() == KeyEvent.KEYCODE_ENTER;
    }
}
