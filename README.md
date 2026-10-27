# NexoNote

Aplicación Android 100 % nativa con Kotlin, Jetpack Compose y Material 3.

## Funcionalidad actual

### Etapa 1

- Navegación inferior con Inicio, Notas, Tareas, Tablas y Ajustes, restaurando el estado de cada destino.
- Creación de notas, tareas y tablas por título (hasta 120 caracteres), guardadas en Room.
- Marcar y desmarcar tareas completadas.
- Apariencia clara, oscura o según el sistema, persistida con DataStore.
- Estados de carga, listas vacías, validación y mensajes de error.

### Etapa 2: dashboard

- Saludo dinámico por hora, nombre configurable en DataStore y fecha actual.
- Cuatro estadísticas reactivas: notas, tareas pendientes, tareas completadas y tablas.
- Progreso de tareas con porcentaje animado y mensaje específico cuando no hay tareas.
- Acciones rápidas que abren Notas, Tareas o Tablas con el formulario de creación listo.
- Notas recientes por fecha de modificación y tareas próximas por vencimiento y prioridad.
- Actividad reciente desde eventos persistidos; la sección se oculta si no hay historia.
- Distribución adaptable: una, dos o cuatro columnas según ancho y escala de fuente.
- Navegación conectada desde tarjetas, listas y actividad.

### Etapa 3: notas

- Editor de notas con título y contenido, autoguardado con retardo y guardado inmediato al salir o al pasar la app a segundo plano.
- Notas de texto y listas de comprobación con progreso, elementos editables y persistencia transaccional.
- Colores, notas fijadas, favoritas, archivadas y papelera con restauración o borrado definitivo.
- Etiquetas reutilizables normalizadas, búsqueda por título, contenido, etiqueta o elemento y filtros de activas, archivadas y papelera.
- Listado adaptable con vista previa, estado de checklist, etiquetas y fecha de modificación o eliminación.
- El dashboard cuenta solo notas activas, abre el editor desde Notas recientes y desde la actividad, y conserva filas resumen en `entries` para no romper tareas, tablas ni actividad.
- Migración 3→4 que recupera las notas existentes sin inventar contenido, fechas de creación ni eventos de actividad.

### Etapa 4: tareas

- CRUD completo de tareas con editor dedicado, título, descripción, estado, prioridad, fecha y hora de vencimiento.
- Subtareas reordenables con progreso; al completar una tarea se completan todas sus subtareas y al reabrir una subtarea se reabre la tarea.
- Etiquetas reutilizables, búsqueda, filtros por estado y fecha, ordenación y listado agrupado por vencimiento y prioridad.
- Recordatorios con WorkManager y notificaciones; se programan a la hora indicada o a las 09:00 cuando la tarea solo tiene fecha, y se cancelan al completar, eliminar o desactivar.
- Recurrencias diarias, semanales y mensuales con fecha de fin opcional; al completar se crea la siguiente ocurrencia conservando el historial y reiniciando subtareas.
- Finalización de una serie recurrente completa y programación de recordatorios tras reinicios o cambios de hora.
- El dashboard muestra tareas próximas reales, permite abrir el editor y refleja las actualizaciones de Room.

### Etapa 5: tablas dinámicas

- Crear, editar, duplicar y eliminar tablas con nombre, descripción, icono y color.
- Columna principal «Nombre» y columnas configurables: texto corto/largo, entero, decimal, moneda, fecha, fecha-hora, casilla, selección, selección múltiple, estado, URL, correo y teléfono.
- Opciones con nombre y color, campos obligatorios, valores predeterminados y conversión de tipos con validación para evitar pérdida de datos.
- Crear, editar, duplicar y eliminar registros; editores de celdas por tipo y panel de edición de registro.
- Búsqueda por contenido y nombres de opciones, filtros combinados, ordenación y páginas de 50 registros.
- Vista de cuadrícula con desplazamiento horizontal compartido y vista de tarjetas.
- Dashboard con contador real de tablas, registros recientes y navegación desde la actividad.
- Room v6 con entidades compartidas para tablas, columnas, opciones, registros y celdas. La migración 5→6 conserva las tablas anteriores y añade su columna principal.
- Las consultas SQL directas de paginación se ejecutan en `Dispatchers.IO`; al duplicar una tabla se reasignan las opciones tanto de las celdas como de los valores predeterminados.

### Etapa 6: calendario y recordatorios

- Acceso desde la barra superior y desde la tarjeta de próximos siete días del dashboard, conservando los cinco destinos inferiores.
- Vistas mensual, semanal por horas con carriles para actividades superpuestas y agenda diaria cronológica.
- Selección de fecha, navegación de periodos, selector de mes/año, botón Hoy e indicadores de actividades. Fechas e inicio de semana según la configuración regional.
- CRUD de eventos con título, descripción, inicio/final, día completo, color, categoría y confirmación al eliminar.
- Tareas existentes proyectadas desde `TaskRepository`, con estado, prioridad y apertura de su editor; no se copian a eventos.
- Recurrencias diarias, semanales y mensuales, fecha final inclusiva y excepciones para editar/eliminar una ocurrencia. Editar toda la serie restablece sus excepciones, con aviso en el editor.
- Recordatorios al momento, 5/10/15/30 minutos, 1 hora o 1 día antes; permiso contextual de notificaciones y WorkManager con reemplazo, cancelación y recuperación del aviso pendiente.
- Room v7 y migración aditiva 6→7. Datos locales sin internet, series ancladas a una zona horaria y duraciones civiles para día completo durante cambios de horario de verano.

Contrato de recurrencias, decisiones de recordatorios y archivos de la etapa: [docs/ETAPA_6.md](docs/ETAPA_6.md).

Las fórmulas, las notificaciones de tablas y la sincronización corresponden a etapas posteriores. La actividad no se reconstruye para datos anteriores a la Etapa 2.

## Arquitectura

Código en `app/src/main/java/com/example/nexonote/`:

| Archivo o paquete | Responsabilidad |
| --- | --- |
| `MainActivity.kt` | Entrada Compose, estado observado con lifecycle y barras del sistema. |
| `NexoNoteApplication.kt` | Contenedor de dependencias y canal de notificaciones. |
| `di/AppContainer.kt` | Inyección manual, base Room, DataStore y scheduler únicos. |
| `domain/model/Models.kt` | Elementos, prioridades, actividad y preferencias. |
| `domain/model/Notes.kt` | Notas, tipos, colores, checklist, etiquetas y borradores. |
| `domain/model/Tasks.kt` | Tareas, subtareas, filtros, validación, recurrencias y fechas. |
| `domain/model/Tables.kt` | Tipos de columnas, filtros, validación, normalización y formato de celdas. |
| `domain/repository/Repositories.kt` | Contratos de elementos, notas, tareas, tablas, actividad y ajustes. |
| `domain/reminder/ReminderScheduler.kt` | Contrato de programación y cancelación de recordatorios. |
| `data/local/NexoDatabase.kt` | Entidades, consultas reactivas, transacciones y esquema Room v7. |
| `data/local/DatabaseMigrations.kt` | Migraciones explícitas 1→2, 2→3, 3→4, 4→5 y 5→6. |
| `data/local/EventDao.kt`, `data/local/EventMigration.kt` | Eventos, excepciones, estado persistente de avisos, DAO con Flow y migración 6→7. |
| `domain/model/Events.kt`, `domain/repository/EventRepository.kt` | Fechas, recurrencias, integración de tareas, carriles y contratos. |
| `data/repository/LocalEventRepository.kt` | Transacciones de eventos y reconciliación de recordatorios. |
| `data/repository/LocalEntryRepository.kt` | Elementos y actividad persistidos, validación y mapeo. |
| `data/repository/LocalNoteRepository.kt` | Guardado transaccional de notas, checklist, etiquetas y ciclo de vida. |
| `data/repository/LocalTaskRepository.kt` | Tareas, subtareas, etiquetas, recurrencias y recordatorios transaccionales. |
| `data/repository/LocalTableRepository.kt` | Tablas, columnas, opciones, registros, celdas y consultas paginadas. |
| `data/repository/DataStoreSettingsRepository.kt` | Tema y nombre visible. |
| `reminder/` | Worker de WorkManager, notificaciones, scheduler y reprogramación tras reinicio. |
| `navigation/Destination.kt` | Rutas, etiquetas e iconos de los cinco destinos y editor de tareas. |
| `ui/NexoViewModel.kt` | Estado de colecciones y tema con StateFlow. |
| `ui/NexoNoteApp.kt` | Scaffold Material 3, Navigation Compose, deep links y Snackbar. |
| `ui/dashboard/DashboardUiState.kt` | Estado derivado y ordenación del dashboard. |
| `ui/dashboard/DashboardViewModel.kt` | Combinación de Flows, reintentos y guardado del nombre. |
| `ui/dashboard/DashboardScreen.kt` | Dashboard adaptable, saludo, estados y diálogo de nombre. |
| `ui/dashboard/DashboardComponents.kt` | Tarjetas, progreso, acciones, listas y actividad reutilizables. |
| `ui/notes/NotesUiState.kt` | Filtrado, búsqueda y ordenación de notas. |
| `ui/notes/NotesViewModel.kt` | Estado reactivo y acciones de organización y papelera. |
| `ui/notes/NotesScreen.kt` | Listado adaptable, búsqueda, filtros, tarjetas y confirmaciones. |
| `ui/notes/NoteEditorViewModel.kt` | Carga, edición, autoguardado y salida segura. |
| `ui/notes/NoteEditorScreen.kt` | Editor de texto/checklist, colores, etiquetas y acciones. |
| `ui/tasks/TaskListUiState.kt` | Filtrado, búsqueda, agrupación y estadísticas de tareas. |
| `ui/tasks/TaskListViewModel.kt` | Estado reactivo y acciones de completar, borrar y abrir tareas. |
| `ui/tasks/TaskListScreen.kt` | Listado adaptable, filtros, búsqueda y confirmaciones. |
| `ui/tasks/TaskEditorViewModel.kt` | Carga, edición, validación, recordatorios y recurrencias. |
| `ui/tasks/TaskEditorScreen.kt` | Editor de tareas, fechas, etiquetas y subtareas. |
| `ui/tables/` | Listado y detalle, ViewModels, estados, filtros y editores de columnas, opciones y celdas. |
| `ui/calendar/` | CalendarViewModel, calendario, tarjeta de dashboard y editor de eventos. |
| `ui/screens/Screens.kt` | Pantallas compartidas y Ajustes. |
| `ui/theme/` | Temas claro/oscuro y tipografía Compose. |

Flujo: **UI → ViewModel → contratos Repository → Room / DataStore / WorkManager**. Room emite cambios por Flow; el `DashboardViewModel` los combina con notas, tareas, actividad, nombre y reloj para producir un `DashboardUiState` inmutable. La UI no accede a DAOs ni usa datos simulados. Las transacciones de Room insertan la nota o tarea, su fila resumen en `entries`, el evento de actividad, las relaciones y las subtareas de forma atómica. El scheduler de recordatorios se invoca después de confirmar la transacción.

`app/schemas/` contiene los esquemas exportados. Las migraciones preservan los registros históricos: las fechas desconocidas quedan nulas y no se inventan contenido, eventos ni fechas de creación al recuperar notas anteriores. La migración 4→5 conserva las tareas existentes como filas resumen y crea sus detalles sin inventar descripción, recordatorios ni recurrencias.

## Configuración

Se conservan namespace/applicationId `com.example.nexonote`, AGP 9.4.1, Gradle 9.6.0, compilador Compose 2.2.10, SDK 37 y minSdk 24. AGP proporciona soporte Kotlin integrado.

Dependencias principales: Navigation Compose 2.9.7, Room 2.8.4 con KSP 2.3.4, WorkManager 2.12.0, DataStore 1.2.0, Coroutines 1.10.2, Lifecycle 2.10.0 y Activity Compose 1.12.4. Para las verificaciones se usa Espresso 3.7.0, AndroidX Test JUnit 1.3.0, Room Testing 2.8.4, Coroutines Test y kotlinx.serialization JSON 1.9.0 (alineado con Room Testing). Versiones centralizadas en `gradle/libs.versions.toml`.

## Ejecutar en Android Studio

1. Abrir esta carpeta con una versión de Android Studio compatible con AGP 9.4.1.
2. Instalar Android SDK Platform 37 y revisar la ruta de `local.properties`.
3. Usar JDK 25 para Gradle, conforme a `gradle/gradle-daemon-jvm.properties`.
4. Ejecutar **Sync Project with Gradle Files**.
5. Crear un emulador o conectar un dispositivo con Android 7.0 / API 24 o posterior.
6. Seleccionar **app** y pulsar **Run**.

Las bases de las etapas anteriores migran automáticamente a la versión 7. En Android 13 o posterior, acepta el permiso de notificaciones para recibir recordatorios de tareas y eventos. WorkManager ofrece avisos diferibles: Android puede retrasarlos por ahorro de batería; no se promete entrega exacta.

## Verificación

Desde PowerShell en la raíz:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:connectedDebugAndroidTest --console=plain
```

- APK: `app/build/outputs/apk/debug/app-debug.apk`
- Lint: `app/build/reports/lint-results-debug.html`
- Pruebas instrumentadas: `app/build/reports/androidTests/connected/debug/index.html`

Las pruebas cubren cálculo de progreso sin tareas, estadísticas, ordenación de notas/tareas/actividad, filtros y búsqueda, autoguardado y salida segura, errores y reintentos, guardado del nombre, creación y actualización transaccional de notas, checklist/etiquetas, papelera, migraciones 1→5, validación y recurrencia de tareas, recordatorios, subtareas, flujo completo de creación/edición/búsqueda/finalización/borrado de tareas, acciones rápidas, actualización reactiva tras cambios en Room y navegación entre temas. Se ejecutaron en el emulador `Medium_Phone_API_37.0`.

Las pruebas de tablas cubren normalización y validación, migración 5→6, CRUD, filtros, ordenación, paginación, conversión de tipos, duplicado independiente con opciones y valores predeterminados, y carga de registros invocada desde el hilo principal.

Las pruebas del calendario cubren validación, límites mensuales, años bisiestos, horario de verano, zonas horarias, inicio regional de semana, superposiciones, tareas, excepciones, CRUD, reapertura de base, migración 6→7, avisos simultáneos/retrasados, deduplicación y programación/cancelación real de trabajos WorkManager. La prueba Compose crea, edita y elimina un evento, cambia de vista y abre una tarea.

Estado verificado de Etapa 6: **51 pruebas unitarias y 37 instrumentadas aprobadas**, compilación debug correcta y lint con **0 errores, 24 advertencias preexistentes**. Detalles y alcance de verificación: [docs/ETAPA_6.md](docs/ETAPA_6.md).
