# Configuración de Firebase para Total Market (plan Spark)

Esta versión de las aplicaciones no utiliza Cloud Functions ni Firebase Admin SDK en el servidor.

## Servicios utilizados

- Firebase Authentication con correo y contraseña.
- Cloud Firestore para perfiles, productos, pedidos y `configuracion/tasaUSD`.
- Reglas de Firestore definidas en `firestore.rules`.

La creación de usuarios de personal se realiza desde la app mediante una instancia secundaria de Firebase Authentication. La sesión principal del administrador se conserva y se utiliza para crear el documento `users/{uid}` en Firestore. No se guardan contraseñas en Firestore.

## Configuración requerida en Firebase Console

1. Mantener el proyecto en el plan Spark si se desea evitar asociar una cuenta de facturación.
2. En Authentication → Sign-in method, habilitar **Email/Password**.
3. Crear o conservar al menos una cuenta administradora inicial en Authentication.
4. Crear manualmente su documento en `users/{UID}` con los campos:
   - `nombre`: nombre del administrador.
   - `email`: correo de la cuenta.
   - `rol`: `admin`.
   - `activo`: `true`.
5. Revisar las reglas de `firestore.rules` y publicarlas desde Firebase Console → Firestore Database → Rules. Este repositorio no las publica automáticamente.
6. En la app de personal, iniciar sesión con esa cuenta y crear los perfiles de repartidores, almaceneros y otros administradores.

## Consideración de seguridad importante

La creación de cuentas con Firebase Authentication desde una aplicación cliente no equivale a una operación privilegiada del servidor. Un tercero puede intentar crear cuentas Auth directamente usando el SDK/API público. Las reglas de Firestore deben impedir que esas cuentas creen perfiles con roles privilegiados: la regla de creación de perfiles solo debe permitir que un administrador activo cree cuentas de personal, mientras que el alta pública de clientes debe quedar limitada al rol `cliente`.

Una cuenta Auth sin documento de perfil válido no debe poder entrar en la app de personal ni leer/escribir datos protegidos. Si la escritura del perfil falla tras crear la cuenta Auth, la app intenta eliminar la cuenta recién creada; si esa limpieza falla, elimina manualmente la cuenta huérfana desde Authentication → Users.

## Límites y costos

Spark no requiere vincular una cuenta de facturación, pero Authentication y Firestore siguen sujetos a cuotas y límites del plan gratuito. El proyecto puede dejar de atender operaciones al alcanzar ciertos límites; “gratis” no significa capacidad ilimitada.

## Pruebas recomendadas antes de usar en producción

- Crear un repartidor y comprobar que la sesión del administrador permanece abierta.
- Crear un almacenero y verificar que solo accede a productos/inventario.
- Crear un segundo administrador y verificar la gestión de usuarios.
- Intentar iniciar sesión con una cuenta Auth sin perfil y comprobar que no obtiene acceso.
- Probar que una cuenta desactivada pierde acceso.
- Probar un alta con correo duplicado y confirmar que la app muestra el error.
- Verificar en Firestore que el perfil guarda `creadoPor` y no contiene contraseña.

No ejecutar ningún despliegue de reglas hasta revisar el contenido completo de `firestore.rules` y validar las reglas en el simulador de Firebase.