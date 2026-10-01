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
    private static final int REQ_SAVE_FILE = 1002;

    private TextView tvStatus;
    private List<Subtitle> loadedSubtitles = new ArrayList<>();

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
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
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
                processInputFile(uri);
            } else if (requestCode == REQ_SAVE_FILE) {
                saveFixedSubtitles(uri);
            }
        }
    }

    private void processInputFile(Uri uri) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<Subtitle> list = parseSrtUri(uri);
                    fixOverlap(list);
                    loadedSubtitles = list;

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            tvStatus.setText("Parsed " + list.size() + " subtitles.\nSelect save location.");
                            promptSaveFile();
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            tvStatus.setText("Error: " + e.getMessage());
                            Toast.makeText(MainActivity.this, "Failed to parse file", Toast.LENGTH_SHORT).show();
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

    private void promptSaveFile() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/x-subrip");
        intent.putExtra(Intent.EXTRA_TITLE, "fixed_subtitles.srt");
        startActivityForResult(intent, REQ_SAVE_FILE);
    }

    private void saveFixedSubtitles(final Uri uri) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    OutputStream outputStream = getContentResolver().openOutputStream(uri);
                    BufferedWriter writer = new BufferedWriter(OutputStreamWriter(outputStream));

                    for (Subtitle s : loadedSubtitles) {
                        writer.write(s.index + "\n");
                        writer.write(s.start + " --> " + s.end + "\n");
                        writer.write(s.text + "\n\n");
                    }
                    writer.flush();
                    writer.close();

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            tvStatus.setText("File successfully fixed and saved!");
                            Toast.makeText(MainActivity.this, "Saved!", Toast.LENGTH_LONG).show();
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            tvStatus.setText("Error saving: " + e.getMessage());
                        }
                    });
                }
            }
        }).start();
    }
}
