# Etapa 9: auditoría, estabilización y preparación de la versión 1.0.0

## Objetivo

Cerrar la primera versión estable de NexoNote sin rehacer la aplicación: auditar el proyecto
existente, corregir defectos de datos y de interfaz con evidencia, verificar la compilación y las
pruebas, fijar la versión `1.0.0` y preparar los artefactos de depuración y publicación.

La arquitectura se mantiene: Kotlin + Jetpack Compose + Material 3 + MVVM/Repository, sin
Flutter, Angular, React Native ni WebView. No se eliminó funcionalidad, no se borraron datos ni
archivos y no se cambió el esquema de Room.

## Alcance auditado

- Configuración Gradle, manifiesto, versión, SDK, iconos, tema de arranque, R8 y reglas de backup.
- Room v8, migraciones 1→8, consultas, transacciones, índices y restauración de copias.
- Repositorios de notas, tareas, tablas, eventos, organización, exportación, importación y copias.
- ViewModels y pantallas de edición: concurrencia, doble pulsación, errores y pérdida de estado.
- Navegación, ciclo de vida, rotación, estados de carga/vacío/error y accesibilidad básica.
- Preferencias de apariencia (tema, color de acento y nombre), su persistencia y su inclusión en copias.
- Compilación Debug/Release, R8, pruebas unitarias, instrumentadas y lint.

## Problemas detectados y estado

### Corregidos en esta etapa

| Severidad | Problema | Corrección |
| --- | --- | --- |
| Alta | Importación de tablas: una clave principal repetida en el mismo archivo se insertaba dos veces aunque la estrategia fuese omitir o actualizar. | El mapa de claves existentes se actualiza tras cada inserción/actualización (`LocalTableImportRepository`). |
| Alta | Importación con estrategia actualizar: se borraban todas las celdas de la fila, incluidas las columnas sin mapear y las celdas vacías. | `ImportRowValues` transporta qué columnas están mapeadas; solo se reemplazan esas celdas (`TableImportService`, `LocalTableImportRepository`). |
| Media | Duplicar una tabla podía fallar si un valor predeterminado apuntaba a una opción eliminada. | El duplicado remapea solo identificadores existentes y `deleteOption` limpia el valor predeterminado afectado (`LocalTableRepository`). |
| Media | Eliminar una ocurrencia de evento podía lanzar `IllegalArgumentException` si faltaba `originalStart`. | Se comprueba el valor y se degrada a borrar el evento (`CalendarViewModel`). |
| Media | La copia de seguridad recorría las tablas fuera de una transacción y podía mezclar estados. | El volcado completo se ejecuta en una transacción de lectura (`LocalBackupRepository`). |
| Media | Los botones «Reintentar» de Carpetas, Estadísticas y Favoritos no hacían nada o salían de la pantalla. | Contador de reintento con `flatMapLatest` en los tres ViewModels y callback real en las pantallas. |
| Media | Favoritos ocultaba errores de base de datos como si la lista estuviera vacía. | El error se propaga al estado y se muestra `OrganizationError` con reintento. |
| Media | Los filtros de la búsqueda global se desbordaban en pantallas de 360 dp y ocultaban el orden. | Fila de filtros convertida a `FlowRow`. |
| Media | El detalle de tabla mostraba «Todavía no hay registros» durante la carga inicial. | Estado de progreso mientras `loading` y no hay filas. |
| Media | El formulario de carpeta no se desplazaba en horizontal o con fuente grande. | `heightIn(max)` + `verticalScroll`. |
| Media | El editor de eventos no tenía en cuenta el teclado. | `imePadding()` en la superficie del diálogo. |
| Media | Rotación: los extras de `MainActivity` y la fecha inicial del calendario se reaplicaban. | Solo se consumen con `savedInstanceState == null` / una vez por pantalla. |
| Media | Rotación: carpeta, categoría, favorito y etiquetas del editor de eventos se perdían. | `rememberSaveable`. |
| Media | Rotación: la casilla de cifrado de la copia externa volvía a desactivada. | `rememberSaveable`. |
| Media | La fila de colores de nota se recortaba en pantallas pequeñas y usaba objetivos de 42 dp. | `FlowRow` y objetivos de 48 dp. |
| Baja | Iconos autoespejados obsoletos (`DriveFileMove`, `Label`, `List`, `Notes`, `Sort`, etc.). | Migrados a `Icons.AutoMirrored`. |
| Baja | `Color(android.graphics.Color.parseColor(...))` duplicaba la conversión. | `toColorInt()`. |
| Baja | `Log.e` de producción en `TableDetailViewModel`. | Eliminado; el error ya se muestra en la UI. |
| Baja | Etiquetas de la barra inferior truncadas sin indicador. | `TextOverflow.Ellipsis`. |
| Baja | Tema de arranque claro en modo oscuro. | `values-night` con fondo y barra de estado oscuros. |

### Personalización añadida en esta etapa

| Área | Cambio |
| --- | --- |
| Color de acento | `AccentColor` (morado, azul, turquesa, verde, naranja, rosa y rojo) guardado en DataStore y aplicado a `ColorScheme` (primario, contenedores, secundario y terciario) en claro y oscuro. `NexoViewModel` lo expone en `NexoUiState` y `MainActivity` lo pasa al tema. |
| Nombre | Campo de nombre en Ajustes con contador 0/40, guardado mediante `SettingsRepository`; el diálogo «Configurar mi nombre» del dashboard se eliminó. El saludo usa el nombre guardado y lo omite si está vacío. |
| Cabecera del inicio | Tarjeta más compacta (18/16 dp de relleno, título `titleLarge`, fecha y lema en `bodySmall`) sin botón de nombre, para dejar más espacio al contenido. |
| Copias | El color de acento se incluye en `readSettings`/`applySettings`, de modo que restaurar una copia conserva la apariencia. Valores desconocidos se ignoran y se mantiene el valor actual. |
| Pruebas | Prueba instrumentada de Ajustes que cambia nombre, tema y acento y comprueba la persistencia al navegar; pruebas unitarias de `NexoViewModel` para acento/nombre y para el error de escritura con reintento. |

### Detectados y no corregidos (documentados)

| Severidad | Problema | Motivo / recomendación |
| --- | --- | --- |
| Media | Varios diálogos de formulario (tabla, columna, celda, carpeta) usan `remember` y pierden lo escrito al rotar. | Requiere salvadores explícitos por formulario; se recomienda en v1.1. No hay pérdida de datos guardados. |
| Media | Contraste insuficiente de algunos colores fijos (tipos de contenido, barras de estadísticas, color de evento como texto) en tema oscuro. | Cambio de diseño visual; se recomienda derivar de `MaterialTheme.colorScheme`. |
| Media | La cuadrícula del calendario filtra actividades 42 veces por composición y el reloj de 30 s reemite el estado. | Optimización de rendimiento; se recomienda agrupar por día en el ViewModel. |
| Baja | Recurrencia mensual de tareas anclada al mes anterior (31 → 28 → 28). | Cambio de contrato de recurrencia; planificado para v1.1. |
| Baja | Paginación de tablas por desplazamiento puede repetir u omitir filas si cambian datos entre páginas. | Requiere paginación por clave; planificado para v1.1. |
| Baja | Mover un elemento de carpeta no actualiza `modifiedAt`. | Afecta al orden «reciente»; planificado para v1.1. |
| Baja | Los filtros de fecha de búsqueda capturan «ahora» al crear el flujo. | Se recalcula al recomponer el flujo; planificado para v1.1. |
| Baja | Objetivos táctiles menores de 48 dp en algunos iconos secundarios. | Mejora de accesibilidad incremental. |

## Configuración de versión y publicación

- `applicationId` y `namespace`: `com.example.nexonote`.
- `versionName`: `1.0.0`.
- `versionCode`: `1` (válido para la primera publicación; incrementar en cada subida a la tienda).
- `minSdk`: 24; `targetSdk` y `compileSdk`: 37.
- Release con R8, reducción de recursos y `packageScope` para `androidx`, `kotlin` y `kotlinx`.
- Firma de Release opcional y externa al repositorio. Se leen, por este orden, propiedades de
  Gradle, variables de entorno o `keystore.properties`:
  `NEXONOTE_STORE_FILE`, `NEXONOTE_STORE_PASSWORD`, `NEXONOTE_KEY_ALIAS`,
  `NEXONOTE_KEY_PASSWORD`.
- Si faltan esos cuatro valores, el Release se genera **sin firmar** y la compilación no falla.
- `.gitignore` excluye `keystore.properties`, `*.jks` y `*.keystore`. No se creó ni se sustituyó
  ninguna clave de firma.
- Backup: se excluyen las copias gestionadas (`filesDir/backups`) y los adjuntos del backup en
  nube; la transferencia entre dispositivos conserva los adjuntos y excluye solo las copias
  gestionadas.
- Icono propio a partir de `ic_app-playstore.png`: PNG por densidad, variante redonda y capa adaptativa con fondo degradado y primer plano; el arte completo queda visible en máscaras circulares. Tema de arranque claro/oscuro.

## Verificación ejecutada

Comandos ejecutados en Windows con el emulador `Medium_Phone_API_37.0` (`emulator-5554`):

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --console=plain
.\gradlew.bat :app:connectedDebugAndroidTest --console=plain
.\gradlew.bat :app:assembleRelease :app:bundleRelease --console=plain
```

Resultados reales:

- Compilación Debug: correcta.
- Pruebas unitarias: **92 aprobadas, 0 fallidas, 0 omitidas**.
- Pruebas instrumentadas: **60 aprobadas, 0 fallidas, 0 omitidas** (incluye dos pruebas de
  regresión nuevas para claves repetidas en importación y para conservar columnas sin mapear, y
  la prueba de nombre, tema y acento desde Ajustes).
- Lint: **0 errores, 22 advertencias** (versiones de dependencias y colores de plantilla sin
  usar, entre otras preexistentes). Se eliminaron los recursos `ic_launcher*` de plantilla que ya
  no referenciaba el manifiesto.
- Compilación Release y bundle: correctos con R8 activo, regenerados tras la personalización.
- Instalación y arranque del APK Debug en el emulador: correctos, sin `FATAL EXCEPTION` en
  `logcat`. El nombre y el acento se conservaron tras cerrar y volver a abrir la app.
- Icono verificado en el emulador (lanzador con máscara circular e «Información de la app»): el
  arte completo se ve sin recortes. También se comprobó en una copia temporal del Release firmada
  con la clave de depuración solo para revisión visual; el artefacto del repositorio sigue sin
  firmar.
- No se afirmó ningún resultado no ejecutado.

## Artefactos

| Artefacto | Ruta | Tamaño aproximado |
| --- | --- | --- |
| APK Debug instalable | `app/build/outputs/apk/debug/app-debug.apk` | 22,7 MB (23 838 182 B) |
| APK Release sin firmar | `app/build/outputs/apk/release/app-release-unsigned.apk` | 4,1 MB (4 248 926 B) |
| Android App Bundle | `app/build/outputs/bundle/release/app-release.aab` | 6,4 MB (6 714 599 B) |
| Informe lint | `app/build/reports/lint-results-debug.html` | — |
| Pruebas unitarias | `app/build/reports/tests/testDebugUnitTest/index.html` | — |
| Pruebas instrumentadas | `app/build/reports/androidTests/connected/debug/index.html` | — |

Los tamaños pueden variar entre compilaciones. `apksigner verify` sobre el APK Release informa
«DOES NOT VERIFY» porque no contiene firma, tal como se espera sin las variables `NEXONOTE_*`.

## Instalación del APK Debug

1. Activar «Opciones de desarrollador» y «Depuración USB» en el dispositivo, o iniciar un
   emulador.
2. Conectar el dispositivo y comprobar `adb devices`.
3. Ejecutar:

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

4. Abrir NexoNote. No se necesita cuenta ni conexión; todos los datos son locales.
5. En Android 13 o posterior, conceder notificaciones si se desean recordatorios.

## Publicación del Release

El artefacto actual **no está firmado** y no puede subirse a Google Play tal cual.

1. Crear o recuperar el almacén de claves fuera del repositorio (nunca dentro del proyecto).
2. Definir las cuatro variables `NEXONOTE_*` (o un `keystore.properties` ignorado por Git).
3. Ejecutar `.\gradlew.bat :app:bundleRelease` y verificar el `.aab` firmado.
4. Revisar `versionCode`/`versionName` antes de cada subida.
5. Completar la ficha de Play: descripción, capturas, política de privacidad y clasificación.
6. Probar el artefacto firmado en al menos un dispositivo físico y una versión antigua de Android.

## Limitaciones conocidas de la 1.0.0

- El Release incluido no está firmado.
- La entrega de recordatorios depende de WorkManager y del ahorro de batería del fabricante.
- No hay sincronización en la nube ni multiusuario.
- La actividad anterior a la Etapa 2 no se reconstruye.
- Los elementos marcados como «detectados y no corregidos» siguen vigentes.

## Recomendaciones para v1.1

1. Persistir el estado de todos los diálogos de formulario con `rememberSaveable` y salvadores.
2. Revisar contraste en tema oscuro y completar objetivos táctiles de 48 dp.
3. Agrupar actividades del calendario por día en el ViewModel y reducir reemisiones del reloj.
4. Anclar la recurrencia mensual de tareas a su fecha original.
5. Paginar tablas por clave `(modifiedAt, id)` y actualizar `modifiedAt` al mover de carpeta.
6. Recalcular los filtros de fecha al emitir y ampliar las pruebas de regresión de importación y restauración.
7. Añadir pruebas instrumentadas de rotación en editores y diálogos.
