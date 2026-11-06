const {onCall, HttpsError} = require("firebase-functions/v2/https");
const {setGlobalOptions} = require("firebase-functions/v2");
const admin = require("firebase-admin");
admin.initializeApp();
setGlobalOptions({maxInstances: 5, region: "us-central1"});

exports.crearUsuarioPersonal = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Debes iniciar sesión.");
  const caller = await admin.firestore().collection("users").doc(request.auth.uid).get();
  if (!caller.exists || caller.get("rol") !== "admin" || caller.get("activo") === false) {
    throw new HttpsError("permission-denied", "Solo un administrador activo puede crear cuentas.");
  }
  const {nombre, email, password, telefono, rol} = request.data || {};
  if (!nombre || !email || !password || !["admin", "repartidor", "almacenero"].includes(rol)) {
    throw new HttpsError("invalid-argument", "Datos incompletos o rol no permitido.");
  }
  if (String(password).length < 8) throw new HttpsError("invalid-argument", "La contraseña debe tener al menos 8 caracteres.");
  let user;
  try {
    user = await admin.auth().createUser({
      displayName: String(nombre).trim(),
      email: String(email).trim().toLowerCase(),
      password: String(password),
      disabled: false
    });
    await admin.firestore().collection("users").doc(user.uid).set({
      nombre: String(nombre).trim(),
      email: String(email).trim().toLowerCase(),
      telefono: String(telefono || "").trim(),
      rol,
      activo: true,
      creado: admin.firestore.FieldValue.serverTimestamp(),
      creadoPor: request.auth.uid
    });
    return {uid: user.uid};
  } catch (e) {
    if (user) await admin.auth().deleteUser(user.uid).catch(() => {});
    throw new HttpsError("internal", e.message || "No se pudo crear el usuario.");
  }
});