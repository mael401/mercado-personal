package com.mercadototal.personal;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import java.util.Map;

public class LoginActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private TextInputEditText etEmail, etPassword;
    private Button btnEntrar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        auth = FirebaseAuth.getInstance();
        setContentView(R.layout.activity_login);

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnEntrar = findViewById(R.id.btnEntrar);
        Button btnOlvide = findViewById(R.id.btnOlvide);

        btnEntrar.setOnClickListener(v -> iniciar());
        btnOlvide.setOnClickListener(v -> olvidePassword());

        if (auth.getCurrentUser() != null) {
            btnEntrar.setEnabled(false);
            verificarRol();
        }
    }

    private String texto(TextInputEditText e) {
        return e.getText() == null ? "" : e.getText().toString().trim();
    }

    private void iniciar() {
        String email = texto(etEmail);
        String pass = texto(etPassword);
        if (email.isEmpty() || pass.isEmpty()) {
            aviso("Escribe tu correo y contraseña.");
            return;
        }
        btnEntrar.setEnabled(false);
        auth.signInWithEmailAndPassword(email, pass)
            .addOnSuccessListener(r -> verificarRol())
            .addOnFailureListener(e -> {
                btnEntrar.setEnabled(true);
                aviso(mensajeError(e));
            });
    }

    /** Solo entran cuentas activas con rol admin o repartidor. */
    private void verificarRol() {
        Repo.perfil(new Repo.Callback<Map<String, Object>>() {
            @Override
            public void ok(Map<String, Object> p) {
                Object rol = p.get("rol");
                boolean activa = !Boolean.FALSE.equals(p.get("activo"));
                if (activa && ("admin".equals(rol) || "repartidor".equals(rol))) {
                    startActivity(new Intent(LoginActivity.this, PedidosActivity.class));
                    finish();
                } else {
                    auth.signOut();
                    btnEntrar.setEnabled(true);
                    aviso(activa
                        ? "Esta cuenta no tiene acceso a la app del personal."
                        : "Esta cuenta está desactivada.");
                }
            }
            @Override
            public void error(String m) {
                btnEntrar.setEnabled(true);
                aviso("No se pudo verificar tu cuenta: " + m);
            }
        });
    }

    private void olvidePassword() {
        EditText entrada = new EditText(this);
        entrada.setHint("Correo registrado");
        entrada.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        entrada.setText(texto(etEmail));

        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        FrameLayout caja = new FrameLayout(this);
        caja.setPadding(pad, 8, pad, 0);
        caja.addView(entrada);

        new AlertDialog.Builder(this)
            .setTitle("Restablecer contraseña")
            .setMessage("Te enviaremos un enlace a tu correo para crear una contraseña nueva.")
            .setView(caja)
            .setPositiveButton("Enviar", (d, w) -> enviarReset(entrada.getText().toString().trim()))
            .setNegativeButton("Cancelar", null)
            .show();
    }

    private void enviarReset(String email) {
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            aviso("Escribe un correo válido.");
            return;
        }
        auth.sendPasswordResetEmail(email)
            .addOnSuccessListener(x -> aviso(
                "Listo. Si el correo está registrado, recibirás un enlace para restablecer tu contraseña. Revisa también la carpeta de spam."))
            .addOnFailureListener(e -> aviso(mensajeError(e)));
    }

    private String mensajeError(Exception e) {
        if (e instanceof FirebaseAuthInvalidCredentialsException
                || e instanceof FirebaseAuthInvalidUserException)
            return "Correo o contraseña incorrectos.";
        if (e instanceof FirebaseNetworkException)
            return "Sin conexión a internet (¿VPN activa?).";
        return "Error: " + e.getMessage();
    }

    private void aviso(String m) {
        Toast.makeText(this, m, Toast.LENGTH_LONG).show();
    }
}
