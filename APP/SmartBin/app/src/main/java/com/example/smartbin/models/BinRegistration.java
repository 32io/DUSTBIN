package com.example.smartbin.models;

public class BinRegistration {
    private String binId;
    private int fillLevel;
    private String status;

    // Constructor
    public BinRegistration(String binId, int fillLevel, String status) {
        this.binId = binId;
        this.fillLevel = fillLevel;
        this.status = status;
    }

    // Getters and setters
    public String getBinId() {
        return binId;
    }

    public void setBinId(String binId) {
        this.binId = binId;
    }

    public int getFillLevel() {
        return fillLevel;
    }

    public void setFillLevel(int fillLevel) {
        this.fillLevel = fillLevel;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
