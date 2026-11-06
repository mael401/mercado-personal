package com.mercadototal.personal;

import android.graphics.Color;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class Fmt {

    public static String dinero(double v) {
        if (v == Math.floor(v)) return "$" + String.format(Locale.US, "%.0f", v);
        return "$" + String.format(Locale.US, "%.2f", v);
    }

    public static String estadoBonito(String e) {
        if (e == null || e.isEmpty()) return "";
        return Character.toUpperCase(e.charAt(0)) + e.substring(1);
    }

    public static String fecha(Date d) {
        if (d == null) return "";
        return new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(d);
    }

    public static int colorEstado(String e) {
        if ("pendiente".equals(e)) return Color.parseColor("#EF6C00");
        if ("confirmado".equals(e)) return Color.parseColor("#00796B");
        if ("en camino".equals(e)) return Color.parseColor("#1565C0");
        if ("entregado".equals(e)) return Color.parseColor("#2E7D32");
        if ("cancelado".equals(e)) return Color.parseColor("#C62828");
        return Color.parseColor("#5F6F6C");
    }
}
