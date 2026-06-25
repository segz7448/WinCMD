package com.zenas.wincmd.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.zenas.wincmd.R;
import com.zenas.wincmd.commands.CmdInterpreter;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private TextView tvOutput;
    private EditText etInput;
    private TextView tvPrompt;
    private ScrollView scrollView;
    private LinearLayout keyToolbar;

    private CmdInterpreter interpreter;
    private SpannableStringBuilder outputBuffer = new SpannableStringBuilder();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private int historyIndex = -1;

    // CMD text colors
    private static final int COLOR_DEFAULT  = Color.parseColor("#C0C0C0");
    private static final int COLOR_ERROR    = Color.parseColor("#FF4444");
    private static final int COLOR_PROMPT   = Color.parseColor("#C0C0C0");
    private static final int COLOR_INPUT    = Color.WHITE;
    private static final int COLOR_SYSTEM   = Color.parseColor("#AAAAFF");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvOutput   = findViewById(R.id.tvOutput);
        etInput    = findViewById(R.id.etInput);
        tvPrompt   = findViewById(R.id.tvPrompt);
        scrollView = findViewById(R.id.scrollView);
        keyToolbar = findViewById(R.id.keyToolbar);

        interpreter = new CmdInterpreter(this);

        setupUI();
        setupKeyToolbar();
        showBanner();
        updatePrompt();
    }

    private void setupUI() {
        // Enter key runs command
        etInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                        && event.getAction() == KeyEvent.ACTION_DOWN)) {
                runCommand();
                return true;
            }
            return false;
        });

        // Hardware Enter key
        etInput.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_ENTER) {
                    runCommand();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    navigateHistory(-1);
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    navigateHistory(1);
                    return true;
                }
            }
            return false;
        });

        // Title bar close button
        TextView btnClose = findViewById(R.id.btnClose);
        if (btnClose != null) btnClose.setOnClickListener(v -> finish());

        // Focus input
        etInput.requestFocus();
        showKeyboard();
    }

    private void setupKeyToolbar() {
        // Toolbar shortcut keys like a real CMD power bar
        String[][] keys = {
            {"↑", "UP"}, {"↓", "DOWN"}, {"Tab", "TAB"},
            {"Ctrl+C", "CTRL_C"}, {"/", "/"}, {"\\", "\\"},
            {"|", "|"}, {"*", "*"}, {"?", "?"}, {">", ">"},
            {">>", ">>"}, {"&&", "&&"}, {"cls", "cls"},
            {"dir", "dir"}, {"cd ..", "cd .."},
            {"ipconfig", "ipconfig /all"},
            {"tasklist", "tasklist"},
            {"ping", "ping "},
            {"systeminfo", "systeminfo"},
            {"help", "help"},
        };

        for (String[] key : keys) {
            TextView btn = new TextView(this);
            btn.setText(key[0]);
            btn.setTextColor(Color.parseColor("#C0C0C0"));
            btn.setTextSize(11f);
            btn.setTypeface(Typeface.MONOSPACE);
            btn.setBackgroundColor(Color.parseColor("#2A2A2A"));
            btn.setPadding(18, 6, 18, 6);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.MATCH_PARENT);
            lp.setMargins(2, 2, 2, 2);
            btn.setLayoutParams(lp);

            final String action = key[1];
            btn.setOnClickListener(v -> {
                switch (action) {
                    case "UP":      navigateHistory(-1); break;
                    case "DOWN":    navigateHistory(1);  break;
                    case "CTRL_C":
                        appendOutput("^C", COLOR_ERROR);
                        appendNewline();
                        etInput.setText("");
                        historyIndex = -1;
                        updatePrompt();
                        break;
                    case "TAB":
                        tabComplete();
                        break;
                    default:
                        // If action ends with space, just insert into field
                        if (action.endsWith(" ")) {
                            etInput.setText(action);
                            etInput.setSelection(etInput.getText().length());
                        } else {
                            // Run immediately
                            etInput.setText(action);
                            runCommand();
                        }
                        break;
                }
            });

            keyToolbar.addView(btn);
        }
    }

    private void showBanner() {
        String banner =
            "Microsoft Windows [Version 10.0.19045.4651]\r\n" +
            "(c) Microsoft Corporation. All rights reserved.\r\n\r\n";
        appendOutput(banner, COLOR_DEFAULT);
    }

    private void runCommand() {
        String input = etInput.getText().toString().trim();
        etInput.setText("");
        historyIndex = -1;

        // Echo the command with prompt
        appendOutput(tvPrompt.getText().toString(), COLOR_PROMPT);
        appendOutput(input + "\r\n", COLOR_INPUT);

        if (input.isEmpty()) {
            updatePrompt();
            scrollToBottom();
            return;
        }

        // Handle pipe: cmd1 | cmd2 (basic)
        if (input.contains(" | ") && !input.toLowerCase().startsWith("ping")) {
            String[] piped = input.split(" \\| ", 2);
            String out1 = interpreter.execute(piped[0].trim(), null);
            if (out1 != null && !out1.isEmpty()) {
                appendOutput(out1 + "\r\n", COLOR_DEFAULT);
            }
            updatePrompt();
            scrollToBottom();
            return;
        }

        // Handle output redirect: cmd > file
        boolean redirect = false;
        boolean appendRedirect = false;
        String redirectFile = null;
        String cmdPart = input;

        if (input.contains(">>")) {
            int idx = input.indexOf(">>");
            cmdPart = input.substring(0, idx).trim();
            redirectFile = input.substring(idx + 2).trim();
            appendRedirect = true;
            redirect = true;
        } else if (input.contains(">")) {
            int idx = input.indexOf(">");
            cmdPart = input.substring(0, idx).trim();
            redirectFile = input.substring(idx + 1).trim();
            redirect = true;
        }

        // Execute
        String result = interpreter.execute(cmdPart, new CmdInterpreter.OutputCallback() {
            @Override
            public void onOutput(String text) {
                mainHandler.post(() -> {
                    appendOutput(text + "\r\n", COLOR_DEFAULT);
                    scrollToBottom();
                });
            }
            @Override
            public void onDone(String prompt) {
                mainHandler.post(() -> {
                    updatePrompt();
                    scrollToBottom();
                });
            }
            @Override
            public void onError(String text) {
                mainHandler.post(() -> {
                    appendOutput(text + "\r\n", COLOR_ERROR);
                    scrollToBottom();
                });
            }
        });

        if (result == null) {
            // Async command running (ping, tracert, nslookup)
            return;
        }

        // Special signals
        if (result.equals("\u001BCLS")) {
            outputBuffer.clear();
            tvOutput.setText("");
            updatePrompt();
            scrollToBottom();
            return;
        }

        if (result.equals("\u001BEXIT")) {
            finish();
            return;
        }

        if (!result.isEmpty()) {
            if (redirect && redirectFile != null) {
                // Write to file instead of screen
                try {
                    java.io.FileWriter fw = new java.io.FileWriter(
                            new java.io.File(getExternalFilesDir(null), redirectFile), appendRedirect);
                    fw.write(result);
                    fw.close();
                } catch (Exception e) {
                    appendOutput("Access is denied.\r\n", COLOR_ERROR);
                }
            } else {
                // Detect errors
                int color = COLOR_DEFAULT;
                if (result.startsWith("'") && result.contains("is not recognized")) color = COLOR_ERROR;
                if (result.startsWith("ERROR:")) color = COLOR_ERROR;
                if (result.startsWith("Access is denied")) color = COLOR_ERROR;
                if (result.startsWith("The system cannot find")) color = COLOR_ERROR;
                appendOutput(result + "\r\n", color);
            }
        }

        updatePrompt();
        scrollToBottom();
    }

    private void updatePrompt() {
        tvPrompt.setText(interpreter.getPrompt());
    }

    private void appendOutput(String text, int color) {
        if (text == null || text.isEmpty()) return;
        // Normalize line endings
        text = text.replace("\r\n", "\n").replace("\r", "\n");
        int start = outputBuffer.length();
        outputBuffer.append(text);
        outputBuffer.setSpan(new ForegroundColorSpan(color),
                start, outputBuffer.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        // Trim buffer if too large (keep last 50000 chars)
        if (outputBuffer.length() > 50000) {
            outputBuffer.delete(0, outputBuffer.length() - 50000);
        }
        tvOutput.setText(outputBuffer);
    }

    private void appendNewline() {
        appendOutput("\n", COLOR_DEFAULT);
    }

    private void scrollToBottom() {
        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
    }

    private void navigateHistory(int direction) {
        List<String> history = interpreter.getCommandHistory();
        if (history.isEmpty()) return;

        if (direction == -1) {
            // Up: go back in history
            if (historyIndex == -1) historyIndex = history.size();
            historyIndex = Math.max(0, historyIndex - 1);
        } else {
            // Down: go forward
            historyIndex = Math.min(history.size(), historyIndex + 1);
        }

        if (historyIndex >= 0 && historyIndex < history.size()) {
            etInput.setText(history.get(historyIndex));
            etInput.setSelection(etInput.getText().length());
        } else {
            etInput.setText("");
            historyIndex = -1;
        }
    }

    private void tabComplete() {
        String current = etInput.getText().toString();
        if (current.isEmpty()) return;

        // Try to complete file/dir name
        String[] parts = current.split("\\s+");
        String prefix = parts[parts.length - 1];

        java.io.File dir = getExternalFilesDir(null);
        if (dir == null) return;
        java.io.File[] files = dir.listFiles();
        if (files == null) return;

        for (java.io.File f : files) {
            if (f.getName().toLowerCase().startsWith(prefix.toLowerCase())) {
                String completed = current.substring(0, current.length() - prefix.length()) + f.getName();
                etInput.setText(completed);
                etInput.setSelection(etInput.getText().length());
                return;
            }
        }
    }

    private void showKeyboard() {
        etInput.postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(etInput, InputMethodManager.SHOW_IMPLICIT);
        }, 300);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) etInput.requestFocus();
    }
}
