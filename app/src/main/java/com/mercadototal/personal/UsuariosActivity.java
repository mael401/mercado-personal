package com.mercadototal.personal;

import android.os.Bundle;
import android.text.InputType;
import android.util.Patterns;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.*;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.*;
import java.util.*;

public class UsuariosActivity extends AppCompatActivity {
    private FirebaseFirestore db;
    private LinearLayout list;
    private ListenerRegistration reg;
    private static final String SECONDARY_APP = "total-market-user-creation";

    private EditText field(String hint, int type) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setInputType(type);
        return e;
    }

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        db = FirebaseFirestore.getInstance();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(1);
        root.setPadding(16, 8, 16, 8);
        setContentView(root);

        Button add = new Button(this);
        add.setText("Crear cuenta de personal");
        root.addView(add);
        add.setOnClickListener(v -> create());

        list = new LinearLayout(this);
        list.setOrientation(1);
        ScrollView s = new ScrollView(this);
        s.addView(list);
        root.addView(s, new LinearLayout.LayoutParams(-1, 0, 1));

        FirebaseUser current = FirebaseAuth.getInstance().getCurrentUser();
        if (current == null) { finish(); return; }
        db.collection("users").document(current.getUid()).get()
            .addOnSuccessListener(d -> {
                if (!d.exists() || !"admin".equals(d.getString("rol"))
                        || Boolean.FALSE.equals(d.get("activo"))) {
                    Toast.makeText(this, "Solo un administrador activo puede gestionar usuarios.", Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
                reg = db.collection("users").addSnapshotListener((q, e) -> {
                    if (e != null || q == null) return;
                    list.removeAllViews();
                    for (DocumentSnapshot u : q) {
                        TextView t = new TextView(this);
                        t.setText(u.getString("nombre") + " · " + u.getString("email")
                            + " · " + u.getString("rol") + " · "
                            + (Boolean.FALSE.equals(u.get("activo")) ? "inactivo" : "activo"));
                        list.addView(t);
                        Button edit = new Button(this);
                        edit.setText("Modificar rol/estado");
                        edit.setOnClickListener(v -> edit(u));
                        list.addView(edit);
                    }
                });
            })
            .addOnFailureListener(e -> {
                Toast.makeText(this, "No se pudo verificar el administrador: " + e.getMessage(), Toast.LENGTH_LONG).show();
                finish();
            });
    }

    private void create() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(1);
        EditText n = field("Nombre", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        EditText e = field("Correo", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        EditText phone = field("Teléfono (opcional)", InputType.TYPE_CLASS_PHONE);
        EditText p = field("Contraseña temporal (mínimo 8)", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        Spinner role = new Spinner(this);
        role.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
            new String[]{"repartidor", "almacenero", "admin"}));
        box.addView(n); box.addView(e); box.addView(phone); box.addView(p); box.addView(role);

        new AlertDialog.Builder(this).setTitle("Nueva cuenta").setView(box)
            .setPositiveButton("Crear", (d, w) -> {
                String nombre = n.getText().toString().trim();
                String email = e.getText().toString().trim().toLowerCase(Locale.ROOT);
                String telefono = phone.getText().toString().trim();
                String password = p.getText().toString();
                String rol = role.getSelectedItem().toString();

                if (nombre.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()
                        || password.length() < 8
                        || !Arrays.asList("admin", "repartidor", "almacenero").contains(rol)) {
                    toast("Revisa el nombre, correo, rol y contraseña (mínimo 8 caracteres).");
                    return;
                }
                crearCuentaSecundaria(nombre, email, telefono, password, rol);
            }).setNegativeButton("Cancelar", null).show();
    }

    /**
     * Auth secundario crea el usuario sin sustituir la sesión del administrador.
     * Firestore predeterminado conserva la sesión admin para autorizar la creación del perfil.
     */
    private void crearCuentaSecundaria(String nombre, String email, String telefono,
                                       String password, String rol) {
        FirebaseApp secondaryApp;
        try {
            try {
                secondaryApp = FirebaseApp.getInstance(SECONDARY_APP);
            } catch (IllegalStateException noExiste) {
                FirebaseOptions options = FirebaseOptions.fromResource(getApplicationContext());
                if (options == null) throw new IllegalStateException("No se encontró la configuración Firebase de la app.");
                secondaryApp = FirebaseApp.initializeApp(getApplicationContext(), options, SECONDARY_APP);
            }
        } catch (Exception ex) {
            toast("No se pudo inicializar Firebase Auth secundario: " + ex.getMessage());
            return;
        }

        FirebaseAuth secondaryAuth = FirebaseAuth.getInstance(secondaryApp);
        secondaryAuth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener(result -> {
                FirebaseUser created = result.getUser();
                if (created == null) {
                    secondaryAuth.signOut();
                    toast("Firebase no devolvió el usuario creado.");
                    return;
                }
                String uid = created.getUid();
                Map<String, Object> profile = new HashMap<>();
                profile.put("nombre", nombre);
                profile.put("email", email);
                profile.put("telefono", telefono);
                profile.put("rol", rol);
                profile.put("activo", true);
                profile.put("creado", FieldValue.serverTimestamp());
                FirebaseUser admin = FirebaseAuth.getInstance().getCurrentUser();
                profile.put("creadoPor", admin == null ? "" : admin.getUid());

                // Importante: usar Firestore de la app PRINCIPAL, autenticada como admin.
                db.collection("users").document(uid).set(profile)
                    .addOnSuccessListener(x -> {
                        secondaryAuth.signOut();
                        toast("Cuenta y perfil creados correctamente.");
                    })
                    .addOnFailureListener(error -> created.delete()
                        .addOnCompleteListener(cleanup -> {
                            secondaryAuth.signOut();
                            String extra = cleanup.isSuccessful()
                                ? " Se eliminó la cuenta Auth creada."
                                : " Atención: no se pudo eliminar la cuenta Auth; revisa Firebase Console.";
                            toast("No se pudo guardar el perfil: " + error.getMessage() + extra);
                        }));
            })
            .addOnFailureListener(error -> {
                secondaryAuth.signOut();
                toast("No se pudo crear la cuenta: " + error.getMessage());
            });
    }

    private void edit(DocumentSnapshot u) {
        String[] roles = {"cliente", "admin", "repartidor", "almacenero"};
        Spinner sp = new Spinner(this);
        sp.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, roles));
        for (int i = 0; i < roles.length; i++)
            if (roles[i].equals(u.getString("rol"))) sp.setSelection(i);
        CheckBox active = new CheckBox(this);
        active.setText("Cuenta activa");
        active.setChecked(!Boolean.FALSE.equals(u.get("activo")));
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(1);
        box.addView(sp); box.addView(active);

        new AlertDialog.Builder(this).setTitle("Actualizar usuario").setView(box)
            .setPositiveButton("Guardar", (d, w) -> {
                Map<String, Object> m = new HashMap<>();
                m.put("rol", sp.getSelectedItem().toString());
                m.put("activo", active.isChecked());
                db.collection("users").document(u.getId()).update(m)
                    .addOnSuccessListener(x -> toast("Usuario actualizado."))
                    .addOnFailureListener(x -> toast("No se pudo actualizar: " + x.getMessage()));
            }).setNegativeButton("Cancelar", null).show();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    @Override protected void onDestroy() {
        if (reg != null) reg.remove();
        super.onDestroy();
    }
}