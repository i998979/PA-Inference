package com.example.gpt_sovits_demo;

import java.io.File;

public class AudioItem {
    public String filePath;
    public String displayName;
    public boolean isPlaying = false;

    public AudioItem(String filePath) {
        this.filePath = filePath;
        this.displayName = new File(filePath).getName();
    }
}
