package com.cachens.nexusdashboard;

final class ResolvedLocation {
    final String name;
    final double latitude;
    final double longitude;

    ResolvedLocation(String name, double latitude, double longitude) {
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
    }
}
