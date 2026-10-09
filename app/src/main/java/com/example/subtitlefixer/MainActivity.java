package com.example.subtitlefixer;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private TextView tvStatus;
    private RecyclerView recyclerView;
    private FileAdapter adapter;
    private List<File> srtFiles = new ArrayList<>();

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

        tvStatus = findViewById(R.id.tvStatus);
        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new FileAdapter(srtFiles, new FileAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(File file) {
                processAndOverwriteOriginal(file);
            }
        });
        recyclerView.setAdapter(adapter);

        checkAndRequestPermissions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (hasStoragePermission()) {
            loadSrtFilesSortedByDate();
        }
    }

    private boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } else {
                loadSrtFilesSortedByDate();
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 101);
            } else {
                loadSrtFilesSortedByDate();
            }
        }
    }

    private void loadSrtFilesSortedByDate() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<File> foundFiles = new ArrayList<>();
                File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                scanDirectory(downloadsDir, foundFiles);

                // SORT BY LAST MODIFIED (NEWEST FIRST)
                Collections.sort(foundFiles, new Comparator<File>() {
                    @Override
                    public int compare(File f1, File f2) {
                        return Long.compare(f2.lastModified(), f1.lastModified());
                    }
                });

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        srtFiles.clear();
                        srtFiles.addAll(foundFiles);
                        adapter.notifyDataSetChanged();
                        if (srtFiles.isEmpty()) {
                            tvStatus.setText("No .srt files found in Downloads folder.");
                        } else {
                            tvStatus.setText("Found " + srtFiles.size() + " files (Sorted: Newest First).\nTap to fix overlaps:");
                        }
                    }
                });
            }
        }).start();
    }

    private void scanDirectory(File dir, List<File> foundFiles) {
        if (dir != null && dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        scanDirectory(file, foundFiles);
                    } else if (file.getName().toLowerCase().endsWith(".srt")) {
                        foundFiles.add(file);
                    }
                }
            }
        }
    }

    private void processAndOverwriteOriginal(final File file) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    List<Subtitle> list = parseSrtFile(file);
                    fixOverlap(list);
                    overwriteSrtFile(file, list);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            tvStatus.setText("Updated: " + file.getName());
                            Toast.makeText(MainActivity.this, "Overwrote original file!", Toast.LENGTH_SHORT).show();
                            loadSrtFilesSortedByDate();
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            tvStatus.setText("Error: " + e.getMessage());
                        }
                    });
                }
            }
        }).start();
    }

    private List<Subtitle> parseSrtFile(File file) throws Exception {
        List<Subtitle> list = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)));

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

    private void overwriteSrtFile(File file, List<Subtitle> list) throws Exception {
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file, false)));

        for (Subtitle s : list) {
            writer.write(s.index + "\n");
            writer.write(s.start + " --> " + s.end + "\n");
            writer.write(s.text + "\n\n");
        }
        writer.flush();
        writer.close();
    }

    // RecyclerView Adapter for listing files
    static class FileAdapter extends RecyclerView.Adapter<FileAdapter.ViewHolder> {
        interface OnItemClickListener {
            void onItemClick(File file);
        }

        private final List<File> files;
        private final OnItemClickListener listener;
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());

        FileAdapter(List<File> files, OnItemClickListener listener) {
            this.files = files;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_file, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            final File file = files.get(position);
            holder.text1.setText(file.getName());
            holder.text2.setText("Modified: " + dateFormat.format(new Date(file.lastModified())));
            holder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    listener.onItemClick(file);
                }
            });
        }

        @Override
        public int getItemCount() {
            return files.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView text1, text2;

            ViewHolder(View itemView) {
                super(itemView);
                text1 = itemView.findViewById(R.id.tvFileName);
                text2 = itemView.findViewById(R.id.tvFileDate);
            }
        }
    }
}