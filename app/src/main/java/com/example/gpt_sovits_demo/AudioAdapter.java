package com.example.gpt_sovits_demo;

import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.Collections;
import java.util.List;

public class AudioAdapter extends RecyclerView.Adapter<AudioAdapter.ViewHolder> {
    private final List<AudioItem> audioItems;
    private final Context context;
    private MediaPlayer mediaPlayer = null;

    public AudioAdapter(Context context, List<AudioItem> audioItems) {
        this.context = context;
        this.audioItems = audioItems;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView name;
        Button play, stop, delete;

        public ViewHolder(View view) {
            super(view);
            name = view.findViewById(R.id.audioName);
            play = view.findViewById(R.id.playBtn);
            stop = view.findViewById(R.id.stopBtn);
            delete = view.findViewById(R.id.deleteBtn);
        }
    }

    @NonNull
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.audio_item, parent, false);
        return new ViewHolder(view);
    }

    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AudioItem item = audioItems.get(position);
        holder.name.setText(item.displayName);

        holder.play.setOnClickListener(v -> {
            if (mediaPlayer != null) {
                mediaPlayer.stop();
                mediaPlayer.release();
            }
            mediaPlayer = MediaPlayer.create(context, Uri.fromFile(new File(item.filePath)));
            mediaPlayer.start();
        });

        holder.stop.setOnClickListener(v -> {
            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
                mediaPlayer.release();
                mediaPlayer = null;
            }
        });

        holder.delete.setOnClickListener(v -> {
            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
                mediaPlayer.release();
                mediaPlayer = null;
            }
            new File(item.filePath).delete();
            int pos = holder.getAdapterPosition();
            if (pos != RecyclerView.NO_POSITION && pos < audioItems.size()) {
                audioItems.remove(pos);
                notifyItemRemoved(pos);
            }
        });
    }

    public int getItemCount() {
        return audioItems.size();
    }

    public void onItemMove(int fromPosition, int toPosition) {
        Collections.swap(audioItems, fromPosition, toPosition);
        notifyItemMoved(fromPosition, toPosition);
    }
}
