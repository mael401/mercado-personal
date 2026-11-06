package com.mercadototal.personal;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Pedido {

    public static class Linea {
        public String nombre;
        public double precio;
        public double cantidad;
    }

    public String id, clienteNombre, telefono, direccion, estado, repartidorId, repartidorNombre;
    public double total;
    public Date fecha;
    public List<Linea> lineas = new ArrayList<>();

    public double unidades() {
        double u = 0;
        for (Linea l : lineas) u += l.cantidad;
        return u;
    }

    public String resumen(String separador) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lineas.size(); i++) {
            if (i > 0) sb.append(separador);
            sb.append(lineas.get(i).cantidad).append(" x ").append(lineas.get(i).nombre);
        }
        return sb.toString();
    }
}
