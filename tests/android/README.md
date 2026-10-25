# Pruebas reales de edición

Primero compilar el proyecto principal. Después ejecutar build-checks.ps1. Instalar tests/android/build/checks.apk con adb install -t y ejecutar:

```powershell
adb shell am instrument -w mx.dibujos.editchecks/mx.dibujos.kiosko.EditChecks
adb uninstall mx.dibujos.editchecks
```

El paquete es independiente, corre contra sí mismo y crea dibujos sintéticos fuera de pantalla. No modifica los datos de mx.dibujos.kiosko.
