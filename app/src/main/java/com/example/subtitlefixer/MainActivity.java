package com.example.subtitlefixer;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private static final int REQ_OPEN_FILE = 1001;

    private TextView tvStatus;

    static class Subtitle {
        int index;
        String start;
        String end;
        String text;

        Subtitle(int index, String start, String end, String text) {
            this.index = index;
            this.start = start;
            this.end = end;
            this.text = text;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = (TextView) findViewById(R.id.tvStatus);
        Button btnSelectFile = (Button) findViewById(R.id.btnSelectFile);

        btnSelectFile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Request read/write access to the selected file
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                startActivityForResult(intent, REQ_OPEN_FILE);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            if (requestCode == REQ_OPEN_FILE) {
                processAndOverwriteOriginal(uri);
            }
        }
    }

    private void processAndOverwriteOriginal(final Uri uri) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // Step 1: Read and parse the original file
                    List<Subtitle> list = parseSrtUri(uri);

                    // Step 2: Fix the overlaps in memory
                    fixOverlap(list);

                    // Step 3: Overwrite the original file in-place using "wt" (write-truncate) mode
                    overwriteSrtUri(uri, list);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            tvStatus.setText("Successfully fixed and overwrote the original file!");
                            Toast.makeText(MainActivity.this, "Original file updated!", Toast.LENGTH_LONG).show();
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            tvStatus.setText("Error: " + e.getMessage());
                            Toast.makeText(MainActivity.this, "Failed to update file", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    private List<Subtitle> parseSrtUri(Uri uri) throws Exception {
        List<Subtitle> list = new ArrayList<>();
        InputStream inputStream = getContentResolver().openInputStream(uri);
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

        List<String> lines = new ArrayList<>();
        String line;
        while ((line = reader.readLine()) != null) {
            lines.add(line);
        }
        reader.close();

        int i = 0;
        while (i < lines.size()) {
            if (lines.get(i).trim().isEmpty()) {
                i++;
                continue;
            }

            String indexStr = lines.get(i++).trim().replace("\uFEFF", "");
            int index;
            try {
                index = Integer.parseInt(indexStr);
            } catch (NumberFormatException e) {
                continue;
            }

            if (i >= lines.size()) break;
            String timeLine = lines.get(i++);
            String[] timeSplit = timeLine.split(" --> ");
            if (timeSplit.length < 2) continue;

            StringBuilder textBuilder = new StringBuilder();
            while (i < lines.size() && !lines.get(i).trim().isEmpty()) {
                textBuilder.append(lines.get(i++)).append("\n");
            }

            list.add(new Subtitle(index, timeSplit[0].trim(), timeSplit[1].trim(), textBuilder.toString().trim()));
        }

        return list;
    }

    private void fixOverlap(List<Subtitle> list) {
        for (int i = 0; i < list.size() - 1; i++) {
            Subtitle current = list.get(i);
            Subtitle next = list.get(i + 1);

            if (current.end.compareTo(next.start) > 0) {
                current.end = next.start;
            }
        }
    }

    private void overwriteSrtUri(Uri uri, List<Subtitle> list) throws Exception {
        // "wt" mode clears the existing file content and writes the updated content directly
        OutputStream outputStream = getContentResolver().openOutputStream(uri, "wt");
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream));

        for (Subtitle s : list) {
            writer.write(s.index + "\n");
            writer.write(s.start + " --> " + s.end + "\n");
            writer.write(s.text + "\n\n");
        }
        writer.flush();
        writer.close();
    }
}