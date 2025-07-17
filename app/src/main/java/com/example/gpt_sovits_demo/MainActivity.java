package com.example.gpt_sovits_demo;

import android.app.Activity;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    public enum Lang {
        YUE,
        ZH,
        EN
    }

    static {
        System.loadLibrary("gpt_sovits_demo_jni");
    }

    private native long initModel(
            String g2pWPath, String g2pEnPath, String vitsPath, String sslPath,
            String t2sEncoderPath, String t2sFsDecoderPath, String t2sSDecoderPath, String bertPath
    );

    private native boolean processReferenceSync(long modelHandle, String refAudioPath, String refText, long langId);

    private native float[] runInferenceSync(long modelHandle, String text, long langId);

    private native void freeModel(long modelHandle);


    private MediaPlayer mediaPlayer;
    // private long modelHandle = 0;
    private Map<Lang, Long> modelHandles;
    private ActivityResultLauncher<Intent> folderPicker;
    private String selectedModelFolder;
    private ExecutorService executorService = Executors.newSingleThreadExecutor();
    private List<AudioItem> audioItems = new ArrayList<>();
    private AudioAdapter audioAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        mediaPlayer = new MediaPlayer();
        modelHandles = new HashMap<>(3);

        Button selectModel = findViewById(R.id.selectModel);
        TextView selectedModel = findViewById(R.id.selectedModel);

        selectModel.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            folderPicker.launch(intent);
        });

        EditText refYue = findViewById(R.id.refYue);
        EditText refZh = findViewById(R.id.refZh);
        EditText refEn = findViewById(R.id.refEn);

        EditText inferenceText = findViewById(R.id.inferenceText);

        Button cantonese = findViewById(R.id.cantonese);
        Button mandarin = findViewById(R.id.mandarin);
        Button english = findViewById(R.id.english);

        folderPicker = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri folderUri = result.getData().getData();
                        if (folderUri != null) {
                            getContentResolver().takePersistableUriPermission(folderUri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

                            selectedModelFolder = folderUri.toString();
                            Toast.makeText(this, "Folder selected", Toast.LENGTH_SHORT).show();
                            selectedModel.setText(selectedModelFolder);

                            Arrays.asList(Lang.YUE, Lang.ZH, Lang.EN).forEach(lang -> {
                                Long handle = modelHandles.remove(lang);
                                if (handle != null) freeModel(handle);
                            });
                            cantonese.setEnabled(true);
                            mandarin.setEnabled(true);
                            english.setEnabled(true);

                        }
                    }
                }
        );

        cantonese.setOnClickListener(v -> {
            cantonese.setEnabled(false);
            mandarin.setEnabled(false);
            english.setEnabled(false);

            Arrays.asList(Lang.ZH, Lang.EN).forEach(lang -> {
                Long handle = modelHandles.remove(lang);
                if (handle != null) freeModel(handle);
            });

            CompletableFuture<Boolean> loadFuture = modelHandles.containsKey(Lang.YUE)
                    ? CompletableFuture.completedFuture(true)
                    : loadModelAsync(refYue.getText().toString(), Lang.YUE);

            loadFuture.thenCompose(success -> success
                            ? runInferenceAsync(inferenceText.getText().toString(), Lang.YUE)
                            : CompletableFuture.completedFuture(false))
                    .whenComplete((result, err) -> {
                        runOnUiThread(() -> {
                            cantonese.setEnabled(true);
                            mandarin.setEnabled(true);
                            english.setEnabled(true);
                        });
                    });
        });
        mandarin.setOnClickListener(v -> {
            cantonese.setEnabled(false);
            mandarin.setEnabled(false);
            english.setEnabled(false);

            Arrays.asList(Lang.YUE, Lang.EN).forEach(lang -> {
                Long handle = modelHandles.remove(lang);
                if (handle != null) freeModel(handle);
            });

            CompletableFuture<Boolean> loadFuture = modelHandles.containsKey(Lang.ZH)
                    ? CompletableFuture.completedFuture(true)
                    : loadModelAsync(refZh.getText().toString(), Lang.ZH);

            loadFuture.thenCompose(success -> success
                            ? runInferenceAsync(inferenceText.getText().toString(), Lang.ZH)
                            : CompletableFuture.completedFuture(false))
                    .whenComplete((result, err) -> {
                        runOnUiThread(() -> {
                            cantonese.setEnabled(true);
                            mandarin.setEnabled(true);
                            english.setEnabled(true);
                        });
                    });
        });
        english.setOnClickListener(v -> {
            cantonese.setEnabled(false);
            mandarin.setEnabled(false);
            english.setEnabled(false);

            Arrays.asList(Lang.YUE, Lang.ZH).forEach(lang -> {
                Long handle = modelHandles.remove(lang);
                if (handle != null) freeModel(handle);
            });

            CompletableFuture<Boolean> loadFuture = modelHandles.containsKey(Lang.EN)
                    ? CompletableFuture.completedFuture(true)
                    : loadModelAsync(refEn.getText().toString(), Lang.EN);

            loadFuture.thenCompose(success -> success
                            ? runInferenceAsync(inferenceText.getText().toString(), Lang.EN)
                            : CompletableFuture.completedFuture(false))
                    .whenComplete((result, err) -> {
                        runOnUiThread(() -> {
                            cantonese.setEnabled(true);
                            mandarin.setEnabled(true);
                            english.setEnabled(true);
                        });
                    });
        });

        RecyclerView audioList = findViewById(R.id.audioList);
        audioList.setLayoutManager(new LinearLayoutManager(this));
        File audioDir = getCacheDir();
        audioItems = loadAudioItemsFromDirectory(audioDir);
        audioAdapter = new AudioAdapter(this, audioItems);
        audioList.setAdapter(audioAdapter);

        ItemTouchHelper.SimpleCallback callback = new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                int from = viewHolder.getAdapterPosition();
                int to = target.getAdapterPosition();
                audioAdapter.onItemMove(from, to);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            }
        };

        new ItemTouchHelper(callback).attachToRecyclerView(audioList);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        for (long modelHandle : modelHandles.values()) {
            if (modelHandle != 0) {
                freeModel(modelHandle);
            }
        }
        executorService.shutdown();
        if (mediaPlayer != null) {
            mediaPlayer.release();
        }
    }

    private Map<String, String> getModelsFromFolder(String folderUriString, Lang lang) {
        Map<String, String> modelMap = new HashMap<>();
        modelMap.put("g2pW", "g2pW.onnx");
        modelMap.put("g2p_en", "g2p_en"); // folder
        modelMap.put("vits", "custom_vits.onnx");
        modelMap.put("ssl", "ssl.onnx");
        modelMap.put("t2s_encoder", "custom_t2s_encoder.onnx");
        modelMap.put("t2s_fs_decoder", "custom_t2s_fs_decoder.onnx");
        modelMap.put("t2s_s_decoder", "custom_t2s_s_decoder.onnx");
        modelMap.put("bert", "bert.onnx");
        modelMap.put("ref", "ref.wav");

        Map<String, String> outputMap = new HashMap<>();

        try {
            Uri folderUri = Uri.parse(folderUriString);
            DocumentFile modelFolder = DocumentFile.fromTreeUri(this, folderUri);
            if (modelFolder == null || !modelFolder.isDirectory()) {
                Log.e("MainActivity", "Invalid model folder URI");
                return null;
            }
            DocumentFile rootFolder = modelFolder.findFile(lang.name().toLowerCase());
            if (rootFolder == null || !rootFolder.isDirectory()) {
                Log.e("MainActivity", "Language subfolder not found: " + lang.name().toLowerCase());
                return null;
            }

            for (Map.Entry<String, String> entry : modelMap.entrySet()) {
                String key = entry.getKey();
                String fileName = entry.getValue();

                DocumentFile fileEntry = rootFolder.findFile(fileName);
                if (fileEntry == null) {
                    Log.e("MainActivity", "Missing: " + fileName);
                    return null;
                }

                if (fileEntry.isFile()) {
                    File cacheFile = new File(getCacheDir(), fileName);
                    try (InputStream in = getContentResolver().openInputStream(fileEntry.getUri());
                         FileOutputStream out = new FileOutputStream(cacheFile)) {

                        byte[] buffer = new byte[4096];
                        int len;
                        while ((len = in.read(buffer)) != -1) {
                            out.write(buffer, 0, len);
                        }
                    }
                    outputMap.put(key, cacheFile.getAbsolutePath());

                } else if (fileEntry.isDirectory()) {
                    File cacheDir = new File(getCacheDir(), fileName);
                    if (!cacheDir.exists()) {
                        if (!cacheDir.mkdirs()) {
                            Log.e("MainActivity", "Failed to create cache folder: " + cacheDir.getAbsolutePath());
                            return null;
                        }
                    }

                    for (DocumentFile subFile : fileEntry.listFiles()) {
                        if (!subFile.isFile()) continue;

                        File localFile = new File(cacheDir, subFile.getName());
                        try (InputStream in = getContentResolver().openInputStream(subFile.getUri());
                             FileOutputStream out = new FileOutputStream(localFile)) {

                            byte[] buffer = new byte[4096];
                            int len;
                            while ((len = in.read(buffer)) != -1) {
                                out.write(buffer, 0, len);
                            }
                        }
                    }

                    // Confirm directory is not empty
                    if (cacheDir.listFiles() == null || cacheDir.listFiles().length == 0) {
                        Log.e("MainActivity", "Directory empty after copy: " + fileName);
                        return null;
                    }

                    outputMap.put(key, cacheDir.getAbsolutePath());
                }
            }

        } catch (Exception e) {
            Log.e("MainActivity", "Error accessing folder: " + e.getMessage(), e);
            return null;
        }

        return outputMap;
    }


    private CompletableFuture<Boolean> loadModelAsync(@NonNull String refText, Lang lang) {
        return CompletableFuture.supplyAsync(() -> {
            if (selectedModelFolder == null) {
                runOnUiThread(() -> Toast.makeText(this, "Please select a model folder first", Toast.LENGTH_LONG).show());
                return false;
            }

            try {
                Map<String, String> modelFiles = getModelsFromFolder(selectedModelFolder, lang);
                if (modelFiles == null || modelFiles.isEmpty()) {
                    runOnUiThread(() -> Toast.makeText(this, "Failed to load models from folder", Toast.LENGTH_LONG).show());
                    return false;
                }

                runOnUiThread(() -> Toast.makeText(this, "Initializing model", Toast.LENGTH_LONG).show());
                long modelHandle = initModel(
                        modelFiles.get("g2pW"),
                        modelFiles.get("g2p_en"),
                        modelFiles.get("vits"),
                        modelFiles.get("ssl"),
                        modelFiles.get("t2s_encoder"),
                        modelFiles.get("t2s_fs_decoder"),
                        modelFiles.get("t2s_s_decoder"),
                        modelFiles.get("bert")
                );
                runOnUiThread(() -> Toast.makeText(this, "Initialized model", Toast.LENGTH_LONG).show());

                if (modelHandle == 0L) {
                    runOnUiThread(() -> Toast.makeText(this, "Model initialization failed", Toast.LENGTH_LONG).show());
                    return false;
                }

                modelHandles.put(lang, modelHandle);

                String refAudioPath = modelFiles.get("ref");
                long langId = lang != Lang.ZH ? 1L : 0L;

                boolean refSuccess = processReferenceSync(modelHandle, refAudioPath, refText, langId);
                if (!refSuccess) {
                    freeModel(modelHandle);
                    modelHandles.remove(lang);
                    runOnUiThread(() -> Toast.makeText(this, "Reference audio processing failed", Toast.LENGTH_LONG).show());
                    return false;
                }

                runOnUiThread(() -> Toast.makeText(this, "Model loaded successfully", Toast.LENGTH_SHORT).show());
                return true;

            } catch (Exception e) {
                Log.e("MainActivity", "Error loading model", e);
                runOnUiThread(() -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
                return false;
            }
        });
    }


    private CompletableFuture<Boolean> runInferenceAsync(@NonNull String text, Lang lang) {
        if (!modelHandles.containsKey(lang)) {
            runOnUiThread(() -> Toast.makeText(this, "Please load model first", Toast.LENGTH_SHORT).show());
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            runOnUiThread(() -> Toast.makeText(this, "Inferencing " + text + " in " + lang.name(), Toast.LENGTH_SHORT).show());
            try {
                float[] samples = runInferenceSync(modelHandles.get(lang), text, lang != Lang.ZH ? 1L : 0L);
                if (samples != null) {
                    String baseName = text.trim().replaceAll("[^\\w.-]", "_");
                    if (baseName.isEmpty()) baseName = "output";

                    File dir = getCacheDir();
                    File wavFile = new File(dir, baseName + ".wav");

                    int counter = 1;
                    while (wavFile.exists()) {
                        wavFile = new File(dir, baseName + "_" + counter++ + ".wav");
                    }

                    writeWavFile(wavFile, new WavSpec(32000, 16, 1), samples);
                    File wavFile1 = wavFile;
                    runOnUiThread(() -> playAudioFromFile(wavFile1.getAbsolutePath()));

                    audioItems.add(new AudioItem(wavFile.getAbsolutePath()));
                    runOnUiThread(() -> audioAdapter.notifyItemInserted(audioItems.size() - 1));

                    return true;
                } else {
                    runOnUiThread(() -> Toast.makeText(this, "Inference failed", Toast.LENGTH_SHORT).show());
                    return false;
                }
            } catch (Exception e) {
                Log.e("MainActivity", "runInferenceAsync: " + e.getMessage(), e);
                runOnUiThread(() -> Toast.makeText(this, "Inference error: " + e.getMessage(), Toast.LENGTH_LONG).show());
                return false;
            }
        });
    }


    private List<AudioItem> loadAudioItemsFromDirectory(File directory) {
        File[] files = directory.listFiles((dir, name) ->
                (name.endsWith(".wav") || name.endsWith(".mp3")) && !name.equalsIgnoreCase("ref.wav"));

        List<AudioItem> items = new ArrayList<>();
        if (files != null) {
            for (File file : files) {
                items.add(new AudioItem(file.getAbsolutePath()));
            }
        }
        return items;
    }

    private void playAudioFromFile(String audioPath) {
        try {
            mediaPlayer.reset();
            mediaPlayer.setDataSource(audioPath);
            mediaPlayer.prepare();
            mediaPlayer.start();
        } catch (Exception e) {
            Toast.makeText(this, "Error playing audio: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void writeWavFile(File file, WavSpec spec, float[] samples) {
        try (FileOutputStream out = new FileOutputStream(file)) {
            short[] pcm = new short[samples.length];
            for (int i = 0; i < samples.length; i++) {
                pcm[i] = (short) Math.max(Math.min(samples[i] * 32767, 32767), -32768);
            }
            int byteRate = spec.sampleRate * spec.channels * spec.bitsPerSample / 8;
            int totalAudioLen = pcm.length * 2;
            int totalDataLen = totalAudioLen + 36;

            out.write("RIFF".getBytes());
            out.write(intToByteArray(totalDataLen));
            out.write("WAVEfmt ".getBytes());
            out.write(intToByteArray(16)); // Subchunk1Size
            out.write(shortToByteArray((short) 1)); // PCM
            out.write(shortToByteArray((short) spec.channels));
            out.write(intToByteArray(spec.sampleRate));
            out.write(intToByteArray(byteRate));
            out.write(shortToByteArray((short) (spec.channels * spec.bitsPerSample / 8)));
            out.write(shortToByteArray((short) spec.bitsPerSample));
            out.write("data".getBytes());
            out.write(intToByteArray(totalAudioLen));

            ByteBuffer buffer = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN);
            for (short sample : pcm) {
                buffer.clear();
                buffer.putShort(sample);
                out.write(buffer.array());
            }
        } catch (Exception e) {
            Log.e("MainActivity", "writeWavFile: " + e.getMessage(), e);
        }
    }

    private byte[] intToByteArray(int value) {
        return new byte[]{
                (byte) (value & 0xFF),
                (byte) ((value >> 8) & 0xFF),
                (byte) ((value >> 16) & 0xFF),
                (byte) ((value >> 24) & 0xFF)
        };
    }

    private byte[] shortToByteArray(short value) {
        return new byte[]{
                (byte) (value & 0xFF),
                (byte) ((value >> 8) & 0xFF)
        };
    }

    private static class WavSpec {
        int sampleRate;
        int bitsPerSample;
        int channels;

        private WavSpec(int sampleRate, int bitsPerSample, int channels) {
            this.sampleRate = sampleRate;
            this.bitsPerSample = bitsPerSample;
            this.channels = channels;
        }
    }
}
