package com.mercadototal.personal;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Repo {

    public interface Callback<T> {
        void ok(T resultado);
        void error(String mensaje);
    }

    private static FirebaseFirestore db() {
        return FirebaseFirestore.getInstance();
    }

    public static String uid() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            throw new IllegalStateException("No hay una sesión autenticada.");
        }
        return FirebaseAuth.getInstance().getCurrentUser().getUid();
    }

    public static void perfil(Callback<Map<String, Object>> cb) {
        db().collection("users").document(uid()).get()
            .addOnSuccessListener(d -> {
                Map<String, Object> m = d.exists() ? d.getData() : new HashMap<>();
                cb.ok(m == null ? new HashMap<>() : m);
            })
            .addOnFailureListener(e -> cb.error(e.getMessage()));
    }

    /**
     * Admin: escucha todos los pedidos recientes.
     * Repartidor: escucha pedidos confirmados disponibles y los asignados a su UID.
     * Las reglas Firestore deben permitir exactamente esas consultas.
     */
    @SuppressWarnings("unchecked")
    public static ListenerRegistration pedidos(Callback<List<Pedido>> cb) {
        RegistroCompuesto compuesto = new RegistroCompuesto();
        Map<String, DocumentSnapshot> documentos = new HashMap<>();
        String usuario = uid();

        db().collection("users").document(usuario).get()
            .addOnSuccessListener(perfil -> {
                if (compuesto.removido) return;
                if (!perfil.exists() || Boolean.FALSE.equals(perfil.get("activo"))) {
                    cb.error("La cuenta no existe o está desactivada.");
                    return;
                }
                String rol = perfil.getString("rol");
                if ("admin".equals(rol)) {
                    ListenerRegistration r = db().collection("pedidos")
                        .orderBy("fecha", Query.Direction.DESCENDING)
                        .limit(200)
                        .addSnapshotListener((snap, e) -> {
                            if (e != null) { cb.error(e.getMessage()); return; }
                            if (snap == null) return;
                            documentos.clear();
                            for (DocumentSnapshot d : snap.getDocuments()) documentos.put(d.getId(), d);
                            cb.ok(convertirPedidos(documentos));
                        });
                    compuesto.agregar(r);
                } else if ("repartidor".equals(rol)) {
                    ListenerRegistration disponibles = db().collection("pedidos")
                        .whereEqualTo("estado", "confirmado")
                        .limit(200)
                        .addSnapshotListener((snap, e) -> {
                            if (e != null) { cb.error(e.getMessage()); return; }
                            if (snap == null) return;
                            reemplazarGrupo(documentos, snap.getDocuments(), "disponibles:");
                            cb.ok(convertirPedidos(documentos));
                        });
                    ListenerRegistration asignados = db().collection("pedidos")
                        .whereEqualTo("repartidorId", usuario)
                        .limit(200)
                        .addSnapshotListener((snap, e) -> {
                            if (e != null) { cb.error(e.getMessage()); return; }
                            if (snap == null) return;
                            reemplazarGrupo(documentos, snap.getDocuments(), "asignados:");
                            cb.ok(convertirPedidos(documentos));
                        });
                    compuesto.agregar(disponibles);
                    compuesto.agregar(asignados);
                } else {
                    cb.error("Esta cuenta no tiene permisos para consultar pedidos.");
                }
            })
            .addOnFailureListener(e -> cb.error(e.getMessage()));
        return compuesto;
    }

    // Mantiene separados los resultados de las dos consultas del repartidor.
    private static void reemplazarGrupo(Map<String, DocumentSnapshot> destino,
                                        List<DocumentSnapshot> nuevos, String prefijo) {
        List<String> quitar = new ArrayList<>();
        for (String k : destino.keySet()) if (k.startsWith(prefijo)) quitar.add(k);
        for (String k : quitar) destino.remove(k);
        for (DocumentSnapshot d : nuevos) destino.put(prefijo + d.getId(), d);
    }

    @SuppressWarnings("unchecked")
    private static List<Pedido> convertirPedidos(Map<String, DocumentSnapshot> documentos) {
        Map<String, DocumentSnapshot> unicos = new LinkedHashMap<>();
        for (DocumentSnapshot d : documentos.values()) unicos.put(d.getId(), d);
        List<Pedido> lista = new ArrayList<>();
        for (DocumentSnapshot d : unicos.values()) {
            try {
                Pedido p = new Pedido();
                p.id = d.getId();
                p.clienteNombre = d.getString("clienteNombre");
                p.telefono = d.getString("telefono");
                p.direccion = d.getString("direccion");
                p.estado = d.getString("estado");
                p.repartidorId = d.getString("repartidorId");
                p.repartidorNombre = d.getString("repartidorNombre");
                Object total = d.get("total");
                p.total = total instanceof Number ? ((Number) total).doubleValue() : 0;
                Timestamp ts = d.getTimestamp("fecha");
                p.fecha = ts == null ? null : ts.toDate();
                List<Map<String, Object>> its = (List<Map<String, Object>>) d.get("items");
                if (its != null) {
                    for (Map<String, Object> m : its) {
                        Pedido.Linea l = new Pedido.Linea();
                        l.nombre = String.valueOf(m.get("nombre"));
                        l.unidadMedida = m.get("unidadMedida") == null ? "" : String.valueOf(m.get("unidadMedida"));
                        Object pr = m.get("precio");
                        l.precio = pr instanceof Number ? ((Number) pr).doubleValue() : 0;
                        Object c = m.get("cantidad");
                        l.cantidad = c instanceof Number ? ((Number) c).doubleValue() : 0;
                        p.lineas.add(l);
                    }
                }
                if (p.estado == null) p.estado = "pendiente";
                lista.add(p);
            } catch (Exception ignorado) {
                // Un documento mal formado no debe cerrar la pantalla.
            }
        }
        Collections.sort(lista, (a, b) -> {
            long fa = a.fecha == null ? Long.MIN_VALUE : a.fecha.getTime();
            long fb = b.fecha == null ? Long.MIN_VALUE : b.fecha.getTime();
            return Long.compare(fb, fa);
        });
        return lista;
    }

    /**
     * Cambia el estado dentro de una transacción y valida rol, asignación y transición.
     * Al confirmar, vuelve a leer precios/nombres/estado de los productos y recalcula
     * el total usando los datos de Firestore, no los importes enviados por el móvil.
     */
    @SuppressWarnings("unchecked")
    public static void cambiarEstado(String pedidoId, String nuevo, String repNombre,
                                     Callback<Void> cb) {
        final String usuario;
        try {
            usuario = uid();
        } catch (Exception e) {
            cb.error(e.getMessage());
            return;
        }

        FirebaseFirestore firestore = db();
        DocumentReference pedidoRef = firestore.collection("pedidos").document(pedidoId);
        DocumentReference perfilRef = firestore.collection("users").document(usuario);

        firestore.runTransaction(tr -> {
            DocumentSnapshot perfil = tr.get(perfilRef);
            DocumentSnapshot pedido = tr.get(pedidoRef);
            if (!perfil.exists() || Boolean.FALSE.equals(perfil.get("activo"))) {
                throw new FirebaseFirestoreException("Cuenta inexistente o desactivada.",
                    FirebaseFirestoreException.Code.PERMISSION_DENIED);
            }
            if (!pedido.exists()) {
                throw new FirebaseFirestoreException("El pedido ya no existe.",
                    FirebaseFirestoreException.Code.NOT_FOUND);
            }

            String rol = perfil.getString("rol");
            boolean admin = "admin".equals(rol);
            boolean repartidor = "repartidor".equals(rol);
            if (!admin && !repartidor) {
                throw new FirebaseFirestoreException("Rol sin permisos para operar pedidos.",
                    FirebaseFirestoreException.Code.PERMISSION_DENIED);
            }

            String actual = pedido.getString("estado");
            String asignadoA = pedido.getString("repartidorId");
            boolean confirmar = admin && "pendiente".equals(actual) && "confirmado".equals(nuevo);
            boolean cancelarPendiente = admin && "pendiente".equals(actual) && "cancelado".equals(nuevo);
            boolean cancelarConfirmado = admin && "confirmado".equals(actual) && "cancelado".equals(nuevo);
            boolean cancelarEnCamino = admin && "en camino".equals(actual) && "cancelado".equals(nuevo);
            boolean adminIniciaEntrega = false;
            boolean repartidorAcepta = repartidor && "confirmado".equals(actual)
                && "en camino".equals(nuevo) && (asignadoA == null || asignadoA.isEmpty());
            boolean repartidorEntrega = repartidor && "en camino".equals(actual)
                && "entregado".equals(nuevo) && usuario.equals(asignadoA);
            boolean adminEntrega = admin && "en camino".equals(actual) && "entregado".equals(nuevo);

            if (!(confirmar || cancelarPendiente || cancelarConfirmado || cancelarEnCamino
                    || adminIniciaEntrega || repartidorAcepta || repartidorEntrega || adminEntrega)) {
                throw new FirebaseFirestoreException(
                    "Transición no permitida o pedido asignado a otro repartidor.",
                    FirebaseFirestoreException.Code.PERMISSION_DENIED);
            }

            boolean descontar = confirmar;
            boolean reponer = cancelarConfirmado || cancelarEnCamino;
            Map<String, Double> cantidades = new LinkedHashMap<>();
            
            Object bruto = pedido.get("items");
            if (descontar || reponer) {
                if (!(bruto instanceof List) || ((List<?>) bruto).isEmpty()) {
                    throw new FirebaseFirestoreException("El pedido no contiene artículos válidos.",
                        FirebaseFirestoreException.Code.INVALID_ARGUMENT);
                }
                for (Object elemento : (List<?>) bruto) {
                    if (!(elemento instanceof Map)) {
                        throw new FirebaseFirestoreException("Línea de pedido inválida.",
                            FirebaseFirestoreException.Code.INVALID_ARGUMENT);
                    }
                    Map<String, Object> item = (Map<String, Object>) elemento;
                    Object idObj = item.get("productoId");
                    Object cantObj = item.get("cantidad");
                    String id = idObj == null ? "" : idObj.toString().trim();
                    double cantidad = cantObj instanceof Number ? ((Number) cantObj).doubleValue() : 0;
                    if (id.isEmpty() || !Double.isFinite(cantidad) || cantidad <= 0 || cantidad > 1000) {
                        throw new FirebaseFirestoreException("Producto o cantidad inválida.",
                            FirebaseFirestoreException.Code.INVALID_ARGUMENT);
                    }
                    double acumulada = cantidades.containsKey(id) ? cantidades.get(id) : 0;
                    if (acumulada + cantidad > 10000.000001d) {
                        throw new FirebaseFirestoreException("Cantidad acumulada fuera de rango.",
                            FirebaseFirestoreException.Code.INVALID_ARGUMENT);
                    }
                    cantidades.put(id, acumulada + cantidad);
                }
            }

            List<DocumentReference> refs = new ArrayList<>();
            List<DocumentSnapshot> productos = new ArrayList<>();
            if (descontar || reponer) {
                for (String id : cantidades.keySet()) {
                    DocumentReference ref = firestore.collection("productos").document(id);
                    refs.add(ref);
                    productos.add(tr.get(ref));
                }
            }

            List<Map<String, Object>> itemsCanonicos = new ArrayList<>();
            double totalCanonico = 0;
            for (int i = 0; i < refs.size(); i++) {
                DocumentSnapshot producto = productos.get(i);
                String id = refs.get(i).getId();
                double cantidad = cantidades.get(id);
                if (!producto.exists()) {
                    throw new FirebaseFirestoreException("Producto inexistente: " + id,
                        FirebaseFirestoreException.Code.NOT_FOUND);
                }
                Object exObj = producto.get("existencia");
                double existencia = exObj instanceof Number ? ((Number) exObj).doubleValue() : 0;
                if (descontar) {
                    if (!Boolean.TRUE.equals(producto.get("activo"))) {
                        throw new FirebaseFirestoreException("Producto inactivo: " + producto.getString("nombre"),
                            FirebaseFirestoreException.Code.FAILED_PRECONDITION);
                    }
                    Object precioObj = producto.get("precio");
                    if (!(precioObj instanceof Number)) {
                        throw new FirebaseFirestoreException("Precio inválido para " + producto.getString("nombre"),
                            FirebaseFirestoreException.Code.FAILED_PRECONDITION);
                    }
                    double precio = ((Number) precioObj).doubleValue();
                    if (!Double.isFinite(precio) || precio < 0 || !Double.isFinite(existencia) || existencia < cantidad
                            || ("unidad".equals(producto.getString("tipoExistencia")) && cantidad != Math.floor(cantidad))) {
                        throw new FirebaseFirestoreException("Precio o existencia inválida para " + producto.getString("nombre"),
                            FirebaseFirestoreException.Code.ABORTED);
                    }
                    String nombre = producto.getString("nombre");
                    if (nombre == null || nombre.trim().isEmpty()) nombre = id;
                    Map<String, Object> linea = new HashMap<>();
                    linea.put("productoId", id);
                    linea.put("nombre", nombre);
                    linea.put("precio", precio);
                    linea.put("cantidad", cantidad);
                    linea.put("unidadMedida", producto.getString("unidadMedida") == null ? ("peso".equals(producto.getString("tipoExistencia")) ? "kg" : "unidad") : producto.getString("unidadMedida"));
                    itemsCanonicos.add(linea);
                    totalCanonico += precio * cantidad;
                    tr.update(refs.get(i), "existencia", existencia - cantidad);
                } else if (reponer) {
                    if (existencia + cantidad > 1000000000d) {
                        throw new FirebaseFirestoreException("Desbordamiento al reponer inventario.",
                            FirebaseFirestoreException.Code.ABORTED);
                    }
                    tr.update(refs.get(i), "existencia", existencia + cantidad);
                }
            }

            Map<String, Object> cambios = new HashMap<>();
            cambios.put("estado", nuevo);
            if ("en camino".equals(nuevo)) {
                String nombrePerfil = perfil.getString("nombre");
                cambios.put("repartidorId", usuario);
                cambios.put("repartidorNombre",
                    nombrePerfil == null || nombrePerfil.trim().isEmpty() ? repNombre : nombrePerfil);
            }
            if (confirmar) {
                cambios.put("items", itemsCanonicos);
                cambios.put("total", totalCanonico);
                Object tasaPedido = pedido.get("tasaUSD");
                double tasa = tasaPedido instanceof Number ? ((Number)tasaPedido).doubleValue() : 0;
                if (tasa > 0) cambios.put("totalUSD", totalCanonico / tasa);
            }
            tr.update(pedidoRef, cambios);
            return null;
        })
        .addOnSuccessListener(r -> cb.ok(null))
        .addOnFailureListener(e -> cb.error(e.getMessage()));
    }

    private static final class RegistroCompuesto implements ListenerRegistration {
        private final List<ListenerRegistration> registros = new ArrayList<>();
        private boolean removido = false;
        void agregar(ListenerRegistration r) {
            if (removido) r.remove(); else registros.add(r);
        }
        @Override public void remove() {
            removido = true;
            for (ListenerRegistration r : registros) r.remove();
            registros.clear();
        }
    }
}
