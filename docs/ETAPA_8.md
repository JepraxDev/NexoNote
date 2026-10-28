# Etapa 8 — Exportación, importación, copias y restauración

## Uso

Todo se abre desde el menú **Datos y copias** de la barra superior y desde la tarjeta homónima de Ajustes. La barra inferior conserva sus cinco destinos históricos.

- **Exportar:** elegir notas, tareas o tablas; marcar uno, varios o todos los elementos; elegir formato y guardar con el selector de documentos de Android (SAF). Al terminar se ofrece compartir el archivo con la hoja de compartir del sistema.
- **Importar tabla:** elegir CSV o XLSX, revisar la hoja, la cabecera, los tipos detectados y una muestra; mapear columnas, elegir tabla destino o crear una nueva, decidir qué hacer con duplicados y si se omiten filas inválidas; confirmar y ver el resumen.
- **Copias:** crear una copia gestionada o guardarla en una carpeta elegida con SAF, opcionalmente cifrada con contraseña; activar copias automáticas diarias o semanales y consultar el historial.
- **Restaurar:** elegir una copia del historial o un archivo externo, introducir la contraseña si está cifrada, revisar el resumen y confirmar el modo reemplazo o fusión segura.

## Decisión sobre XLSX

No se usa Apache POI. En Android, POI arrastra dependencias de `java.awt`, `javax.xml.stream` y APIs de escritorio que no existen en la plataforma, además de un coste de métodos y tamaño desproporcionado para esta app. La solución implementada es un codec OOXML propio y acotado:

- **Escritura** (`XlsxCodec.write`): contenedor ZIP con `[Content_Types].xml`, relaciones, `styles.xml`, `sharedStrings`/hojas y celdas con **inline strings**. Nunca se emite un elemento `<f>`, así que una celda que empiece por `=`, `+`, `-` o `@` se guarda como texto literal.
- **Lectura** (`XlsxCodec.read`): SAX del parser XML de Android, sin DOM en memoria, con extracción previa de las entradas ZIP a un directorio temporal y límites de tamaño. Se soportan cadenas inline, cadenas compartidas, números, booleanos, fórmulas (se lee su valor en caché) y estilos de fecha de Excel (`numFmtId` y formatos personalizados), convirtiéndolos a ISO.
- **Límites:** 64 hojas, 50 000 filas y 1 024 columnas por libro; 16 MB por entrada y 64 MB descomprimidos.

En la exportación las fechas se escriben como texto ISO (`yyyy-MM-dd`, `yyyy-MM-dd HH:mm`) para que el archivo sea legible e independiente de la configuración regional. Al importar sí se reconocen fechas nativas de Excel.

## Formatos de exportación

### Notas — PDF, TXT y Markdown

Cada nota incluye título, contenido, fechas de creación y modificación, tipo (texto o checklist), carpeta, categoría, etiquetas y el estado de cada elemento de la lista. Se pueden exportar varias notas en un solo archivo, separadas por una línea divisoria. El PDF se genera con `PdfDocument` y `StaticLayout` nativos: respeta Unicode, saltos de línea y paginación, y no añade dependencias. TXT y Markdown se escriben en UTF-8; Markdown usa `# título`, `#etiqueta` y `- [x]` / `- [ ]` para los elementos de la lista.

### Tareas — CSV y XLSX

Columnas: título, descripción, estado, prioridad, inicio, vencimiento, indicador de hora, etiquetas, carpeta, categoría, subtareas, fecha de creación y fecha de modificación. Las subtareas se serializan como líneas `- [x]` / `- [ ]` dentro de una misma celda, y las fechas usan el mismo formato ISO en ambos formatos.

### Tablas — CSV y XLSX

Se usan los **nombres reales** de las columnas y sus tipos:

| Tipo | Representación |
| --- | --- |
| Texto, URL, correo, teléfono | texto tal cual |
| Entero, decimal, moneda | número sin separador de miles; decimal con punto |
| Fecha / fecha-hora | texto ISO (`yyyy-MM-dd` / `yyyy-MM-dd HH:mm`) |
| Casilla | `1` / `0` en XLSX y `Sí` / `No` en CSV |
| Selección / estado | nombre de la opción, no su identificador |
| Selección múltiple | nombres de las opciones separados por `, ` |

CSV admite una sola tabla por archivo; XLSX genera **una hoja por tabla**, con nombres saneados y únicos. Las celdas vacías se exportan vacías, no como cadenas «null».

## Seguridad de CSV y XLSX

`FormulaGuard` neutraliza la inyección de fórmulas en CSV anteponiendo un apóstrofo cuando el primer carácter significativo es `=`, `+`, `-`, `@`, tabulador o retorno de carro, incluidos los casos con espacios iniciales o BOM. Al importar, `FormulaGuard.unescape` retira ese prefijo para recuperar el valor original. En XLSX no hace falta porque el codec solo escribe celdas de texto/número, nunca fórmulas. Los nombres de archivo se sanean y se evitan rutas privadas: se usa SAF o un archivo temporal del caché servido por `FileProvider` (`${applicationId}.fileprovider`).

## Importación de tablas

1. **Selección:** el selector acepta CSV (`text/*`, `.csv`) y XLSX; el tipo se confirma por extensión y por la firma ZIP del archivo, no solo por el MIME declarado.
2. **Vista previa:** nombre de archivo, hojas disponibles, cabecera, 5 filas de muestra, total de filas y tipos detectados (entero, decimal, fecha, fecha-hora, casilla, correo, URL, teléfono, texto corto/largo).
3. **Mapeo:** cada columna destino puede apuntar a una columna de origen o quedar sin asignar. En una tabla nueva el mapeo se genera automáticamente y la primera columna pasa a ser la principal; en una tabla existente se conservan sus columnas, tipos, obligatoriedad y opciones de selección.
4. **Validación por fila:** números, fechas, casillas, opciones existentes, campos obligatorios y tamaño de texto. Los errores indican número de fila y motivo.
5. **Duplicados:** `SKIP` omite, `UPDATE` actualiza la fila existente y `ALLOW` inserta otra. La clave es la columna principal: el texto se compara sin distinguir mayúsculas y los números se normalizan; las selecciones usan el valor normalizado que se guarda en la celda.
6. **Transacción:** todas las filas válidas se escriben en una única transacción de Room junto con la tabla y sus columnas. Si se desactiva «omitir filas inválidas», una sola fila incorrecta aborta la importación completa y no deja tabla, columnas ni filas parciales. Nunca se sobrescribe una tabla sin que el usuario elija explícitamente el destino y confirme.

**Límites:** 20 000 filas, 200 columnas y 32 MB de texto CSV; al leer un XLSX se aplican además los límites del codec.

## Formato de copia de seguridad

Extensión `.nexobackup`; contenedor ZIP versionado:

```
manifest.json     texto plano, legible sin contraseña
payload.zip       ZIP interno: data.json, settings.json, attachments/
payload.enc       igual que payload.zip pero cifrado (solo si hay contraseña)
```

`manifest.json` contiene:

- `format` = `nexonote-backup`, `formatVersion` = 1, `schemaVersion` (8 en esta etapa), `createdAt` y `appVersion`.
- `encrypted`, `checksumSha256` del payload almacenado y `payloadBytes`.
- `counts` por tabla, número de adjuntos, y los parámetros de cifrado cuando aplica: KDF, iteraciones, sal e IV en hexadecimal.

`data.json` guarda las 20 tablas permitidas con sus columnas reales y todas las filas tal como las devuelve SQLite: `entries`, `folders`, `categories`, `labels`, `notes`, `checklist_items`, `tasks`, `subtasks`, `custom_tables`, `custom_columns`, `column_options`, `custom_rows`, `custom_cells`, `events`, `activities`, `note_labels`, `task_labels`, `table_labels`, `event_labels` y `saved_views`. Se conservan identificadores y claves foráneas, por lo que las relaciones, etiquetas, carpetas, categorías y adjuntos siguen apuntando a los mismos elementos. `settings.json` incluye nombre visible y tema; las preferencias de copias automáticas y el historial **no** forman parte del payload.

**Cifrado opcional:** AES-256-GCM con PBKDF2-HMAC-SHA256 (210 000 iteraciones) en API 26+ y PBKDF2-HMAC-SHA1 como alternativa en API 24–25. El algoritmo se guarda en el manifiesto para poder abrir la copia en cualquier dispositivo. La contraseña nunca se escribe en disco ni se guarda en preferencias; se mantiene en memoria el tiempo imprescindible y se borra el arreglo al terminar.

**Adjuntos:** si existe `filesDir/attachments`, se incluyen recursivamente conservando rutas relativas. Cada archivo se limita a 50 MB, 256 MB en total y 500 archivos. No hay sistema de adjuntos en etapas anteriores, por lo que en la práctica solo se incluyen si el usuario los coloca ahí.

**Límites del contenedor:** 512 MB de payload, 320 MB de `data.json`, 256 KB de ajustes, 600 entradas y 512 KB de manifiesto. Se rechazan entradas desconocidas, duplicadas, rutas con `..`, tamaños declarados engañosos y discrepancias de checksum o tamaño. No se incluyen tokens, contraseñas, claves ni secretos de sesión.

## Restauración

`RestoreService` separa inspección y ejecución:

1. **Validar tamaño y acceso:** archivo no vacío, accesible y ≤ 512 MB.
2. **Leer el manifiesto y verificar el payload:** formato conocido, versión de formato admitida, tamaño exacto y SHA-256.
3. **Descifrar si procede:** una contraseña incorrecta falla aquí, antes de tocar datos, y el GCM autentica el contenido.
4. **Validar datos:** formato `nexonote-data`, versión, columnas sin duplicados, tablas permitidas, límites de filas/valores y coherencia entre `schemaVersion` del manifiesto, del payload y de Room.
5. **Resumen previo:** fecha, versión de esquema, recuentos, adjuntos, estado de compatibilidad y problemas detectados. La confirmación solo se habilita si la copia es compatible y, cuando está cifrada, si la contraseña ya se verificó.
6. **Ejecución atómica:** los adjuntos se preparan en un directorio de staging. En modo **REPLACE** se apartan los adjuntos actuales antes de mover los nuevos y se restaura la base dentro de una transacción con `PRAGMA defer_foreign_keys = ON`, tras borrar las tablas en orden inverso. En modo **MERGE** no se borra nada: se rechaza si hay conflictos de identificadores o claves naturales, y los adjuntos solo se añaden o sobrescriben.
7. **Rollback:** si la escritura de la base falla, se restauran los adjuntos anteriores. Si fallan los ajustes después de escribir la base, se recuperan los adjuntos y se vuelve a aplicar la instantánea previa. Si algo no se puede revertir, el error se propaga y el resultado no se presenta como exitoso.
8. **Informe:** filas restauradas por tabla, adjuntos y modo aplicado. Cualquier error de validación se muestra antes o durante la operación sin eliminar los datos existentes.

El modo REPLACE sustituye el contenido de las tablas incluidas; el historial de copias y las preferencias de programación quedan fuera de la restauración.

## Copias automáticas e historial

- `WorkManagerBackupScheduler` programa un `PeriodicWorker` único con `ExistingPeriodicWorkPolicy.UPDATE`, intervalo de 1 o 7 días y restricciones de batería y almacenamiento no bajos. No se promete una hora exacta: Android puede diferir la ejecución.
- `BackupWorker` omite la ejecución si la última copia automática es más reciente que el intervalo, exige un destino SAF persistente y crea un documento hijo (`NexoNote-auto-yyyyMMdd-HHmmss.nexobackup`). Las copias automáticas no se cifran porque no se almacena ninguna contraseña.
- Los fallos (sin destino, sin permisos, espacio insuficiente, error de escritura) se registran en el historial con estado «Con error» y un mensaje, sin bloquear la app.
- `FileBackupHistoryStore` persiste el historial en un JSON pequeño con fecha, tamaño, estado, ubicación y cifrado. Las copias gestionadas viven en `filesDir/backups`; las externas conservan su URI. Antes de afirmar que una copia externa existe se intenta abrir su URI; si ya no está disponible se muestra como tal y no se permite restaurarla.
- Desde el historial se puede restaurar, compartir o eliminar una copia gestionada. Las externas se eliminan solo del historial, nunca del almacenamiento del usuario.

## Interfaz y accesibilidad

- Cuatro pantallas Compose Material 3 (`ExportScreen`, `ImportScreen`, `BackupScreen`, `RestoreScreen`) con estados de carga, error, vacío y éxito, barras de progreso durante operaciones largas y snackbars.
- Flujo de importación en tres pasos (archivo → revisión → resultado) y de restauración en dos (validación → confirmación), con el botón destructivo deshabilitado hasta que la copia es válida.
- Controles con etiquetas y descripciones accesibles, objetivos táctiles de tamaño mínimo, navegación por teclado/lector de pantalla y textos que no dependen solo del color.
- Diseño adaptable a teléfono, tableta y escalado de fuente; temas claro y oscuro.
- Todo el trabajo de archivos, ZIP, cifrado y base de datos se ejecuta en `Dispatchers.IO`. Los flujos grandes usan streaming y límites; no se cargan archivos completos innecesariamente en memoria.

## Arquitectura y archivos

Servicios y codecs nuevos en `app/src/main/java/com/example/nexonote/`:

- `domain/model/Transfer.kt`: formatos, opciones de exportación/importación, manifiesto, historial y resultados.
- `domain/model/BackupData.kt`: instantánea de tablas y parámetros de cifrado.
- `domain/repository/TransferRepository.kt`: contratos de exportación, importación, copias, ajustes e historial.
- `domain/export/`: `TransferFormats`, `NoteTextFormatter`, `TaskSheetBuilder`, `TableSheetBuilder` y `SheetData`.
- `data/transfer/`: `ExportService`, `TableImportService`, `BackupService`, `RestoreService`, `CsvCodec`, `XlsxCodec`, `FormulaGuard`, `NotePdfRenderer`, `BackupArchive`, `BackupCrypto`, `BackupJson` y `FileGateway`.
- `data/repository/`: `LocalExportDataRepository`, `LocalTableImportRepository`, `LocalBackupRepository`, `FileBackupHistoryStore` y `DataStoreBackupSettingsRepository`.
- `backup/BackupWorker.kt`: programación periódica y ejecución de copias automáticas.
- `ui/transfer/`: pantallas, ViewModels, estados, componentes compartidos, `TransferFactories`, `TransferRoutes` y el contrato SAF para crear documentos con MIME variable.

Los servicios se construyen en `di/AppContainer.kt` y se inyectan en `MainActivity` mediante `TransferFactories`; la navegación usa `TransferRoutes`. No se modificaron los contratos existentes de notas, tareas o tablas: las nuevas interfaces de solo lectura/escritura aíslan la etapa y mantienen compatibles los dobles de prueba anteriores.

## Verificación

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:connectedDebugAndroidTest --console=plain
```

- **91 pruebas unitarias, 0 fallos.** Incluyen round-trip de CSV con comillas, comas, saltos de línea, BOM y Unicode; detección de delimitador; guardado y desescape de fórmulas; round-trip de XLSX con varias hojas, números, booleanos, texto con fórmulas y archivos no ZIP; formato y análisis de fechas ISO/españolas; detección de tipos; formato de notas TXT/Markdown con checklist; columnas y tipos reales de tablas; serialización JSON de instantáneas, ajustes y manifiesto con parámetros criptográficos; y validación de contenedores de copia, checksum y payload corrupto.
- **58 pruebas instrumentadas, 0 fallos.** `TableImportServiceTest` cubre CSV con tipos detectados, estrategias de duplicados SKIP/UPDATE/ALLOW, filas inválidas con y sin omisión, desescape de fórmulas y archivo inexistente. `BackupRestoreTest` cubre copia y restauración REPLACE con identificadores, relaciones, etiquetas, checklist, subtareas, celdas y ajustes; contraseña incorrecta y correcta; copia corrupta; esquema incompatible; conflicto de MERGE; y rollback de adjuntos ante un fallo simulado a mitad de restauración.
- **Lint:** 0 errores. Las 24 advertencias restantes son preexistentes (versiones de dependencias, recursos de plantilla sin usar y etiqueta redundante); las 9 advertencias nuevas de esta etapa se corrigieron usando las extensiones KTX (`toUri`, `withTranslation`, `withClip`).

## Limitaciones conocidas

- No existe un sistema de adjuntos en la app; solo se copian los archivos que ya estén en `filesDir/attachments`.
- El formato de copia es de esta app: no se importan copias de otras aplicaciones ni se exporta a un formato estándar de terceros.
- La restauración exige la misma versión de esquema (8). Las copias de versiones futuras o anteriores se rechazan con un mensaje claro; no se migran automáticamente.
- La fusión segura es conservadora: ante cualquier colisión de identificador o clave natural se rechaza completa en lugar de mezclar parcialmente.
- WorkManager no garantiza una hora exacta y el sistema puede posponer la copia por batería o almacenamiento.
- Las copias automáticas no se cifran, porque guardar la contraseña contradiría el requisito de no almacenar secretos.
- CSV solo exporta una tabla por archivo; para varias tablas hay que usar XLSX.
- El lector XLSX no interpreta macros, gráficos, imágenes ni fórmulas complejas: solo valores y fechas.
