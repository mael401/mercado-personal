package com.mercadototal.personal;

import android.app.Activity;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class Cuenta {

    private static EditText campo(Activity a, String pista) {
        EditText e = new EditText(a);
        e.setHint(pista);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        return e;
    }

    public static void cambiarPassword(Activity a) {
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u == null || u.getEmail() == null) return;

        int pad = (int) (20 * a.getResources().getDisplayMetrics().density);
        LinearLayout caja = new LinearLayout(a);
        caja.setOrientation(LinearLayout.VERTICAL);
        caja.setPadding(pad, 8, pad, 0);
        EditText actual = campo(a, "Contraseña actual");
        EditText nueva = campo(a, "Nueva contraseña (mínimo 6)");
        EditText repite = campo(a, "Repite la nueva contraseña");
        caja.addView(actual);
        caja.addView(nueva);
        caja.addView(repite);

        AlertDialog d = new AlertDialog.Builder(a)
            .setTitle("Cambiar contraseña")
            .setView(caja)
            .setPositiveButton("Guardar", null)
            .setNegativeButton("Cancelar", null)
            .create();

        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String act = actual.getText().toString();
            String nue = nueva.getText().toString();
            String rep = repite.getText().toString();
            if (act.isEmpty()) {
                Toast.makeText(a, "Escribe tu contraseña actual.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (nue.length() < 6) {
                Toast.makeText(a, "La nueva contraseña debe tener al menos 6 caracteres.",
                    Toast.LENGTH_SHORT).show();
                return;
            }
            if (!nue.equals(rep)) {
                Toast.makeText(a, "Las contraseñas nuevas no coinciden.", Toast.LENGTH_SHORT).show();
                return;
            }
            AuthCredential cred = EmailAuthProvider.getCredential(u.getEmail(), act);
            u.reauthenticate(cred)
                .addOnSuccessListener(r -> u.updatePassword(nue)
                    .addOnSuccessListener(z -> Toast.makeText(a, "Contraseña actualizada.",
                        Toast.LENGTH_LONG).show())
                    .addOnFailureListener(e -> Toast.makeText(a,
                        "No se pudo cambiar: " + e.getMessage(), Toast.LENGTH_LONG).show()))
                .addOnFailureListener(e -> Toast.makeText(a,
                    "La contraseña actual no es correcta.", Toast.LENGTH_LONG).show());
            d.dismiss();
        }));
        d.show();
    }
}
