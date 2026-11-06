# Configuración de Firebase para Total Market

Las APK nuevas usan:
- Firestore `configuracion/tasaUSD` para la tasa CUP por USD.
- Reglas de Firestore de este repositorio para restringir tasa, roles, productos y pedidos.
- Cloud Function callable `crearUsuarioPersonal` para crear cuentas privilegiadas sin exponer Firebase Admin SDK en Android.

## Despliegue inicial

Desde la raíz de este repositorio, con Node.js 20 y Firebase CLI instalados:

```bash
npm install -g firebase-tools
firebase login
cd functions
npm install
cd ..
firebase deploy --only firestore:rules,functions --project mercado-domicilio
```

No se despliegan reglas ni funciones al instalar una APK. Este despliegue es un paso independiente y requiere una cuenta autorizada en el proyecto Firebase.

## Orden de puesta en marcha

1. Desplegar las reglas y la Cloud Function.
2. Entrar en la app de personal con una cuenta administradora existente.
3. Abrir **Productos e inventario** y establecer la tasa del día (CUP por 1 USD).
4. Crear usuarios de personal desde **Administrar usuarios**.
5. Probar un producto por peso con 0,5 kg y otro por unidad.
6. Hacer un pedido de prueba, confirmarlo y comprobar la exportación Excel.

Las fotos se comprimen en Android y se guardan como Base64 en el documento del producto. Mantenerlas por debajo del límite de tamaño de Firestore.
