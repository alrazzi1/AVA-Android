package com.example.ausbctest;
import static java.lang.System.currentTimeMillis;

import android.annotation.SuppressLint;
import android.media.MediaPlayer;
import android.os.Handler;

import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.util.Timer;

public class SoundManager {
    private static SoundManager instance;
    private final AppCompatActivity activity;
    private final Handler handler;
    private MediaPlayer player = null;
    private long lastStarted = -1;

    public static SoundManager getInstance(AppCompatActivity activity) {
        if (instance != null) {
            instance.destroy();
        }
        instance = new SoundManager(activity);
        return instance;
    }
    @SuppressLint("MissingPermission")
    void destroy() {
    }
    SoundManager(AppCompatActivity activity) {
        this.activity=activity;
        handler = new Handler(activity.getMainLooper());
    }

    public void play(int resid){
        if(player!=null){
            player.release();
        }
        player = MediaPlayer.create(activity,resid);
        player.start();
        lastStarted = currentTimeMillis();
    }
    public void stop(){
        if(player!=null){
            player.pause();
        }
    }
    public void resume(){
        if(player!=null){
            player.start();
        }
    }

    public long timeSinceCompleted(){
        if(player==null){
            return Integer.MAX_VALUE;
        }else{
            return timeSinceStarted() - player.getDuration();
        }
    }

    public long timeSinceStarted(){
        long currentTime = currentTimeMillis();
        long timeSinceStarted = currentTime - lastStarted;
        return timeSinceStarted;
    }
}
