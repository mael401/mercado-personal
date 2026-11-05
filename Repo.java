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
import java.util.HashMap;
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
        return FirebaseAuth.getInstance().getCurrentUser().getUid();
    }

    public static void perfil(Callback<Map<String, Object>> cb) {
        db().collection("users").document(uid()).get()
            .addOnSuccessListener(d -> {
                Map<String, Object> m = d.exists() ? d.getData() : new HashMap<>();
                cb.ok(m);
            })
            .addOnFailureListener(e -> cb.error(e.getMessage()));
    }

    /** Escucha los pedidos en tiempo real (los 200 más recientes). */
    @SuppressWarnings("unchecked")
    public static ListenerRegistration pedidos(Callback<List<Pedido>> cb) {
        return db().collection("pedidos")
            .orderBy("fecha", Query.Direction.DESCENDING)
            .limit(200)
            .addSnapshotListener((snap, e) -> {
                if (e != null) {
                    cb.error(e.getMessage());
                    return;
                }
                if (snap == null) return;
                List<Pedido> lista = new ArrayList<>();
                for (DocumentSnapshot d : snap.getDocuments()) {
                    try {
                        Pedido p = new Pedido();
                        p.id = d.getId();
                        p.clienteNombre = d.getString("clienteNombre");
                        p.telefono = d.getString("telefono");
                        p.direccion = d.getString("direccion");
                        p.estado = d.getString("estado");
                        p.repartidorNombre = d.getString("repartidorNombre");
                        Double t = d.getDouble("total");
                        p.total = t == null ? 0 : t;
                        Timestamp ts = d.getTimestamp("fecha");
                        p.fecha = ts == null ? null : ts.toDate();
                        List<Map<String, Object>> its = (List<Map<String, Object>>) d.get("items");
                        if (its != null) {
                            for (Map<String, Object> m : its) {
                                Pedido.Linea l = new Pedido.Linea();
                                l.nombre = String.valueOf(m.get("nombre"));
                                Object pr = m.get("precio");
                                l.precio = pr instanceof Number ? ((Number) pr).doubleValue() : 0;
                                Object c = m.get("cantidad");
                                l.cantidad = c instanceof Number ? ((Number) c).longValue() : 0;
                                p.lineas.add(l);
                            }
                        }
                        if (p.estado == null) p.estado = "pendiente";
                        lista.add(p);
                    } catch (Exception ignorado) {
                        // pedido mal formado: se omite
                    }
                }
                cb.ok(lista);
            });
    }

    /**
     * Cambia el estado de un pedido.
     * pendiente -> confirmado : descuenta existencias (solo administrador).
     * confirmado/en camino -> cancelado : devuelve las existencias.
     */
    @SuppressWarnings("unchecked")
    public static void cambiarEstado(String pedidoId, String nuevo, String repNombre,
                                     Callback<Void> cb) {
        FirebaseFirestore db = db();
        DocumentReference pr = db.collection("pedidos").document(pedidoId);

        db.runTransaction(tr -> {
            DocumentSnapshot ps = tr.get(pr);
            String actual = ps.getString("estado");
            List<Map<String, Object>> items = (List<Map<String, Object>>) ps.get("items");

            boolean descontar = "confirmado".equals(nuevo) && "pendiente".equals(actual);
            boolean reponer = "cancelado".equals(nuevo)
                && ("confirmado".equals(actual) || "en camino".equals(actual));

            if ((descontar || reponer) && items != null) {
                List<DocumentReference> refs = new ArrayList<>();
                List<DocumentSnapshot> snaps = new ArrayList<>();
                for (Map<String, Object> m : items) {
                    DocumentReference r = db.collection("productos")
                        .document(String.valueOf(m.get("productoId")));
                    refs.add(r);
                    snaps.add(tr.get(r));
                }
                for (int i = 0; i < refs.size(); i++) {
                    DocumentSnapshot s = snaps.get(i);
                    if (!s.exists()) continue;
                    Long ex = s.getLong("existencia");
                    long existencia = ex == null ? 0 : ex;
                    Object c = items.get(i).get("cantidad");
                    long cant = c instanceof Number ? ((Number) c).longValue() : 0;
                    if (descontar) {
                        if (existencia < cant) {
                            throw new FirebaseFirestoreException(
                                "No hay existencias suficientes de " + items.get(i).get("nombre"),
                                FirebaseFirestoreException.Code.ABORTED);
                        }
                        tr.update(refs.get(i), "existencia", existencia - cant);
                    } else {
                        tr.update(refs.get(i), "existencia", existencia + cant);
                    }
                }
            }

            if ("en camino".equals(nuevo)) {
                tr.update(pr, "estado", nuevo,
                    "repartidorId", uid(),
                    "repartidorNombre", repNombre);
            } else {
                tr.update(pr, "estado", nuevo);
            }
            return null;
        })
        .addOnSuccessListener(r -> cb.ok(null))
        .addOnFailureListener(e -> cb.error(e.getMessage()));
    }
}
