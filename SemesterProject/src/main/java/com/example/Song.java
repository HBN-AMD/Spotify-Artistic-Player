package com.example;

public class Song {
    public int id;
    public String title;
    public String artist;
    public String path;
    public String coverPath;
    public Song(int id, String title, String artist, String path, String coverPath) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.path = path;
        this.coverPath = coverPath;
    }
}