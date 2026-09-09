package com.cachens.nexusdashboard;

final class AirCareAqi {
    private AirCareAqi() {
    }

    static int category(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0) return -1;
        if (value <= 26) return 0;
        if (value <= 33) return 1;
        if (value <= 66) return 2;
        if (value <= 100) return 3;
        return 4;
    }

    static int color(double value) {
        switch (category(value)) {
            case 0: return 0xff4ba062;
            case 1: return 0xffdba42a;
            case 2: return 0xfff87728;
            case 3: return 0xffc60045;
            case 4: return 0xff7d2181;
            default: return 0xff646e78;
        }
    }
}