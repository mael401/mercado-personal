package com.mercadototal.personal;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
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

        btnEntrar.setOnClickListener(v -> iniciar());

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

    /** Solo entran cuentas con rol admin o repartidor. */
    private void verificarRol() {
        Repo.perfil(new Repo.Callback<Map<String, Object>>() {
            @Override
            public void ok(Map<String, Object> p) {
                Object rol = p.get("rol");
                if ("admin".equals(rol) || "repartidor".equals(rol)) {
                    startActivity(new Intent(LoginActivity.this, PedidosActivity.class));
                    finish();
                } else {
                    auth.signOut();
                    btnEntrar.setEnabled(true);
                    aviso("Esta cuenta no tiene acceso a la app del personal.");
                }
            }
            @Override
            public void error(String m) {
                btnEntrar.setEnabled(true);
                aviso("No se pudo verificar tu cuenta: " + m);
            }
        });
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
