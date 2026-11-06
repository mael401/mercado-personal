package com.mercadototal.personal;

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
}
