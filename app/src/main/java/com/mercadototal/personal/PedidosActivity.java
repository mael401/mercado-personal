package com.mercadototal.personal;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.ListenerRegistration;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PedidosActivity extends AppCompatActivity {

    private final List<Pedido> todos = new ArrayList<>();
    private final List<Pedido> visibles = new ArrayList<>();
    private List<Pedido> aExportar = new ArrayList<>();
    private PedidoAdapter adapter;
    private ListenerRegistration registro, tasaRegistro;

    private String nombre = "";
    private boolean esAdmin = false;
    private String[] opciones;

    private TextView tvSaludo, tvResumen, tvEstado;
    private Spinner spFiltro;
    private Button btnExportar;

    private final ActivityResultLauncher<String> guardar = registerForActivityResult(
        new ActivityResultContracts.CreateDocument(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
        uri -> {
            if (uri != null) escribirExcel(uri);
        });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            irALogin();
            return;
        }

        setContentView(R.layout.activity_pedidos);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        tvSaludo = findViewById(R.id.tvSaludo);
        tvResumen = findViewById(R.id.tvResumen);
        tvEstado = findViewById(R.id.tvEstado);
        TextView tvTasa = findViewById(R.id.tvTasa);
        tasaRegistro = com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("configuracion").document("tasaUSD").addSnapshotListener((d,e) -> {
            if (e != null) { tvTasa.setText("No se pudo consultar la tasa USD"); return; }
            Object valor = d == null ? null : d.get("valor");
            tvTasa.setText(valor instanceof Number ? "Tasa vigente: 1 USD = " + Fmt.dinero(((Number)valor).doubleValue()) + " CUP" : "Tasa USD pendiente de configurar");
        });
        spFiltro = findViewById(R.id.spFiltro);
        btnExportar = findViewById(R.id.btnExportar);
        RecyclerView rv = findViewById(R.id.rvPedidos);

        toolbar.inflateMenu(R.menu.menu_personal);
        toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.accion_productos) { startActivity(new Intent(this, ProductosActivity.class)); return true; }\n            if (id == R.id.accion_usuarios && esAdmin) { startActivity(new Intent(this, UsuariosActivity.class)); return true; }\n            if (id == R.id.accion_clave) {
                Cuenta.cambiarPassword(this);
                return true;
            }
            if (id == R.id.accion_salir) {
                FirebaseAuth.getInstance().signOut();
                irALogin();
                return true;
            }
            return false;
        });

        adapter = new PedidoAdapter();
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);

        btnExportar.setVisibility(View.GONE);
        btnExportar.setOnClickListener(v -> exportar());

        tvSaludo.setText("Cargando...");
        Repo.perfil(new Repo.Callback<Map<String, Object>>() {
            @Override
            public void ok(Map<String, Object> p) {
                Object rol = p.get("rol");
                boolean admin = "admin".equals(rol);
                boolean activa = !Boolean.FALSE.equals(p.get("activo"));
                if (!activa || (!admin && !"repartidor".equals(rol))) {
                    FirebaseAuth.getInstance().signOut();
                    irALogin();
                    return;
                }
                esAdmin = admin;
                Object n = p.get("nombre");
                nombre = n == null ? "" : n.toString();
                tvSaludo.setText("Hola, " + nombre + "  ·  " + (esAdmin ? "Administrador" : "Repartidor"));
                btnExportar.setVisibility(esAdmin ? View.VISIBLE : View.GONE);
                configurarFiltro();
                escuchar();
            }
            @Override
            public void error(String m) {
                tvSaludo.setText("No se pudo cargar tu perfil: " + m);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (registro != null) registro.remove();
        if (tasaRegistro != null) tasaRegistro.remove();
    }

    private void configurarFiltro() {
        opciones = esAdmin
            ? new String[]{"Todos", "Pendientes", "Confirmados", "En camino", "Entregados", "Cancelados"}
            : new String[]{"Por entregar", "Entregados"};
        spFiltro.setAdapter(new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_dropdown_item, opciones));
        spFiltro.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                aplicarFiltro();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });
    }

    private void escuchar() {
        registro = Repo.pedidos(new Repo.Callback<List<Pedido>>() {
            @Override
            public void ok(List<Pedido> r) {
                todos.clear();
                todos.addAll(r);
                aplicarFiltro();
            }
            @Override
            public void error(String m) {
                tvEstado.setText("No se pudieron cargar los pedidos: " + m);
                tvEstado.setVisibility(View.VISIBLE);
            }
        });
    }

    private boolean coincide(Pedido p, String op) {
        String e = p.estado;
        switch (op) {
            case "Pendientes":   return "pendiente".equals(e);
            case "Confirmados":  return "confirmado".equals(e);
            case "En camino":    return "en camino".equals(e);
            case "Por entregar": return "confirmado".equals(e) || "en camino".equals(e);
            case "Entregados":   return "entregado".equals(e);
            case "Cancelados":   return "cancelado".equals(e);
            default:             return true;
        }
    }

    private void aplicarFiltro() {
        if (opciones == null || adapter == null) return;
        String op = opciones[spFiltro.getSelectedItemPosition()];
        visibles.clear();
        double total = 0;
        double unidades = 0;
        for (Pedido p : todos) {
            if (!esAdmin && "pendiente".equals(p.estado)) continue;
            if (!esAdmin && "cancelado".equals(p.estado)) continue;
            if (!coincide(p, op)) continue;
            visibles.add(p);
            if (!"cancelado".equals(p.estado)) {
                total += p.total;
                unidades += p.unidades();
            }
        }
        adapter.notifyDataSetChanged();

        if ("Cancelados".equals(op)) {
            tvResumen.setText(visibles.size() + " pedidos cancelados");
        } else {
            tvResumen.setText(visibles.size() + " pedidos  ·  " + unidades
                + " productos  ·  Total: " + Fmt.dinero(total));
        }
        tvEstado.setText("No hay pedidos en esta vista.");
        tvEstado.setVisibility(visibles.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void cambiar(Pedido p, String nuevo) {
        Repo.cambiarEstado(p.id, nuevo, nombre, new Repo.Callback<Void>() {
            @Override
            public void ok(Void r) {
                Toast.makeText(PedidosActivity.this, "Pedido actualizado.",
                    Toast.LENGTH_SHORT).show();
            }
            @Override
            public void error(String m) {
                Toast.makeText(PedidosActivity.this, "No se pudo actualizar: " + m,
                    Toast.LENGTH_LONG).show();
            }
        });
    }

    private void confirmar(String texto, Runnable accion) {
        new AlertDialog.Builder(this)
            .setMessage(texto)
            .setPositiveButton("Sí", (d, w) -> accion.run())
            .setNegativeButton("No", null)
            .show();
    }

    private void irALogin() {
        Intent i = new Intent(this, LoginActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    // ---------- Exportar a Excel ----------

    private void exportar() {
        aExportar = new ArrayList<>();
        for (Pedido p : visibles) if ("entregado".equals(p.estado)) aExportar.add(p);
        if (aExportar.isEmpty()) {
            Toast.makeText(this, "No hay pedidos entregados para incluir en el informe.", Toast.LENGTH_LONG).show();
            return;
        }
        String fecha = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        guardar.launch("ventas_domicilio_" + fecha + ".xlsx");
    }

    private String nz(String s) { return s == null ? "" : s; }

    private void escribirExcel(android.net.Uri uri) {
        try {
            List<Object[]> filas = new ArrayList<>();
            Map<String, double[]> porProducto = new java.util.TreeMap<>();
            Map<String, Double> porDia = new java.util.TreeMap<>();
            double totalGeneral = 0;
            filas.add(new Object[]{"DETALLE DE VENTAS A DOMICILIO"});
            filas.add(new Object[]{"Fecha", "Pedido", "Cliente", "Teléfono", "Dirección",
                "Producto", "Cantidad", "Unidad", "Precio unitario (CUP)", "Recaudado (CUP)"});
            for (Pedido p : aExportar) {
                String dia = p.fecha == null ? "Sin fecha" :
                    new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(p.fecha);
                for (Pedido.Linea l : p.lineas) {
                    double importe = l.precio * l.cantidad;
                    String unidad = nz(l.unidadMedida);
                    filas.add(new Object[]{dia, p.id, nz(p.clienteNombre), nz(p.telefono),
                        nz(p.direccion), nz(l.nombre), l.cantidad, unidad, l.precio, importe});
                    String key = nz(l.nombre) + " (" + unidad + ")";
                    double[] agg = porProducto.get(key);
                    if (agg == null) { agg = new double[]{0, 0}; porProducto.put(key, agg); }
                    agg[0] += l.cantidad;
                    agg[1] += importe;
                    totalGeneral += importe;
                }
                porDia.put(dia, (porDia.containsKey(dia) ? porDia.get(dia) : 0d) + p.total);
            }
            filas.add(new Object[]{""});
            filas.add(new Object[]{"RESUMEN POR PRODUCTO"});
            filas.add(new Object[]{"Producto", "Cantidad vendida", "Unidad", "Recaudado (CUP)"});
            for (Map.Entry<String, double[]> e : porProducto.entrySet()) {
                String k = e.getKey();
                int cut = k.lastIndexOf(" (");
                String nombreProducto = cut < 0 ? k : k.substring(0, cut);
                String unidad = cut < 0 ? "" : k.substring(cut + 2, k.length() - 1);
                filas.add(new Object[]{nombreProducto, e.getValue()[0], unidad, e.getValue()[1]});
            }
            filas.add(new Object[]{"TOTAL GENERAL", "", "", totalGeneral});
            filas.add(new Object[]{""});
            filas.add(new Object[]{"RESUMEN DIARIO"});
            filas.add(new Object[]{"Fecha", "Total vendido (CUP)"});
            for (Map.Entry<String, Double> e : porDia.entrySet())
                filas.add(new Object[]{e.getKey(), e.getValue()});
            byte[] datos = XlsxWriter.crear("Ventas domicilio", filas);
            try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                if (os == null) throw new java.io.IOException("No se pudo abrir el archivo de destino.");
                os.write(datos);
            }
            Toast.makeText(this, "Excel de ventas entregadas guardado.", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "No se pudo guardar el Excel: " + e.getMessage(),
                Toast.LENGTH_LONG).show();
        }
    }

    // ---------- Lista ----------

    private void mostrarBoton(Button b, String texto, View.OnClickListener l) {
        b.setText(texto);
        b.setOnClickListener(l);
        b.setVisibility(View.VISIBLE);
    }

    class PedidoAdapter extends RecyclerView.Adapter<PedidoAdapter.VH> {

        class VH extends RecyclerView.ViewHolder {
            TextView fecha, chip, cliente, direccion, productos, total, repartidor;
            Button btnA, btnB;
            VH(View v) {
                super(v);
                fecha = v.findViewById(R.id.tvFecha);
                chip = v.findViewById(R.id.tvChip);
                cliente = v.findViewById(R.id.tvCliente);
                direccion = v.findViewById(R.id.tvDireccion);
                productos = v.findViewById(R.id.tvProductos);
                total = v.findViewById(R.id.tvTotal);
                repartidor = v.findViewById(R.id.tvRepartidor);
                btnA = v.findViewById(R.id.btnA);
                btnB = v.findViewById(R.id.btnB);
            }
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_pedido, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            Pedido p = visibles.get(pos);

            h.fecha.setText(Fmt.fecha(p.fecha));
            h.chip.setText(Fmt.estadoBonito(p.estado));
            h.chip.setTextColor(Fmt.colorEstado(p.estado));

            String tel = nz(p.telefono);
            h.cliente.setText(nz(p.clienteNombre) + (tel.isEmpty() ? "" : "  ·  " + tel));
            h.direccion.setText(nz(p.direccion));
            h.productos.setText(p.resumen("\n"));
            h.total.setText("A cobrar: " + Fmt.dinero(p.total));

            String rep = nz(p.repartidorNombre);
            h.repartidor.setText(rep.isEmpty() ? "" : "Repartidor: " + rep);
            h.repartidor.setVisibility(rep.isEmpty() ? View.GONE : View.VISIBLE);

            h.btnA.setVisibility(View.GONE);
            h.btnB.setVisibility(View.GONE);

            String e = p.estado;
            if ("pendiente".equals(e)) {
                if (esAdmin) {
                    mostrarBoton(h.btnA, "Confirmar", v -> cambiar(p, "confirmado"));
                    mostrarBoton(h.btnB, "Cancelar", v ->
                        confirmar("¿Cancelar este pedido?", () -> cambiar(p, "cancelado")));
                }
            } else if ("confirmado".equals(e)) {
                mostrarBoton(h.btnA, esAdmin ? "En camino" : "Aceptar pedido",
                    v -> cambiar(p, "en camino"));
                if (esAdmin) {
                    mostrarBoton(h.btnB, "Cancelar", v -> confirmar(
                        "¿Cancelar este pedido? Las existencias volverán al inventario.",
                        () -> cambiar(p, "cancelado")));
                }
            } else if ("en camino".equals(e)) {
                mostrarBoton(h.btnA, "Marcar entregado", v -> cambiar(p, "entregado"));
                if (esAdmin) {
                    mostrarBoton(h.btnB, "Cancelar", v -> confirmar(
                        "¿Cancelar este pedido? Las existencias volverán al inventario.",
                        () -> cambiar(p, "cancelado")));
                }
            }
        }

        @Override
        public int getItemCount() {
            return visibles.size();
        }
    }
}
