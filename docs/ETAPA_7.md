# Etapa 7 — Organización, búsqueda, favoritos y estadísticas

## Uso

Los cuatro módulos nuevos se abren desde el menú de la barra superior (icono de organización) y desde los accesos rápidos del dashboard. La barra inferior conserva sus cinco destinos históricos.

- **Carpetas:** pestaña con jerarquía expandible. Cada fila muestra el recuento directo de elementos, la ruta (`Trabajo / Proyectos`) y la descripción. El menú permite editar, añadir una subcarpeta o eliminar. Al expandir una carpeta se listan sus elementos directos y cada uno tiene una acción «Mover» para enviarlo a otra carpeta o a la raíz.
- **Categorías:** nombre único sin distinguir mayúsculas, color y recuento de uso. Se asignan desde los editores de nota, tarea, tabla y evento.
- **Etiquetas:** se reutiliza la tabla `labels` de la Etapa 3, ahora con color y nombre normalizado. Un elemento puede tener varias y el filtro de búsqueda exige todas las seleccionadas.
- **Favoritos:** pantalla unificada con notas, tareas, tablas y eventos; cada fila permite abrir el elemento o quitar el favorito.
- **Búsqueda global:** consulta agrupada por tipo con filtros de categoría, carpeta (con o sin subcarpetas), etiquetas, tipo, estado, fecha, solo favoritos y ordenación. Los resultados abren directamente el editor o la pantalla correspondiente.
- **Vistas guardadas:** guardan consulta y filtros. Se pueden crear, aplicar, renombrar y eliminar. Se ofrecen cuatro sugerencias en un toque: Pendientes, Importantes, Trabajo y Personal.
- **Estadísticas:** recuentos reales, tasa de finalización sin división por cero, distribución por tipo, actividad de los últimos días y series de 7/8/6 periodos dibujadas con Compose nativo.

## Modelo y decisiones

- `Organization.kt` contiene `Folder`, `Category`, `Tag`, `SavedView`, `OrganizationItem`, `SearchFilters`, validadores, detección de ciclos, descendientes, rutas y `computeOrganizationStats`.
- Una carpeta no puede ser padre de sí misma ni de uno de sus descendientes (`folderCreatesCycle`). La comprobación se hace en el repositorio antes de escribir.
- **Eliminar carpeta:** dentro de una transacción, sus subcarpetas pasan al padre de la carpeta eliminada (o a la raíz) y todo su contenido se reasigna igualmente. Después se borra la carpeta. Nada se elimina en cascada por accidente; la interfaz lo explica antes de confirmar.
- Las columnas `folderId` y `categoryId` son NULL para «sin carpeta/categoría» y usan `ON DELETE SET NULL` como red de seguridad referencial. `favorite` es un booleano por elemento.
- Las categorías usan `COLLATE NOCASE` con índice único; las etiquetas se normalizan a minúsculas y tampoco admiten duplicados. Intentar guardar un duplicado devuelve un error legible.
- `saved_views` guarda cada filtro en columnas explícitas; las etiquetas y los tipos se serializan como CSV. Las claves foráneas de carpeta y categoría usan `SET NULL`. Si una etiqueta se elimina, la vista se sanea al leerla para no conservar identificadores huérfanos.
- Los filtros que mezclan semánticas (estado de tarea frente a notas) solo se aplican al tipo que los admite; una nota nunca se considera «pendiente».

## Persistencia

- Room versión 8; esquema exportado en `app/schemas/com.example.nexonote.data.local.NexoDatabase/8.json`.
- Migración aditiva `MIGRATION_7_8`: crea `folders`, `categories`, `saved_views`, `table_labels` y `event_labels`; añade color a `labels`; añade `favorite`, `folderId` y `categoryId` a `notes`, `tasks`, `custom_tables` y `events`, con sus índices. No se usa `fallbackToDestructiveMigration`.
- Las filas existentes conservan todos sus datos: los campos nuevos quedan en `NULL`/`0` y las etiquetas previas reciben el color por defecto.
- `OrganizationDao` expone `Flow` para carpetas, categorías, etiquetas, favoritos y búsqueda. La búsqueda es una sola consulta `UNION ALL` de los cuatro tipos; el filtrado por subárbol de carpetas, etiquetas, estado y tipo se completa en el repositorio para no multiplicar consultas.
- El traslado y el borrado de carpetas se ejecutan con `withTransaction`. Los editores validan que la carpeta y la categoría sigan existiendo antes de guardar.
- Los repositorios de notas, tareas, tablas y eventos persisten los campos de organización y sus etiquetas en la misma transacción que el contenido. La lista de tablas combina `table_labels` con los nombres de etiqueta para reflejar renombrados.

## Estadísticas

`computeOrganizationStats` recibe listas ya observadas desde Room:

- Notas activas (sin papelera), tareas pendientes/completadas/vencidas, tablas y eventos programados/próximos.
- Los registros de tabla se cuentan una sola vez por tabla aunque el flujo contenga duplicados.
- La tasa de finalización es `0` cuando no hay tareas; el porcentaje usa esa misma tasa.
- `activityTimes` y `rowCreationTimes` son flujos independientes, de modo que las series no dependen del límite de la lista de actividad reciente.
- Los gráficos son `Canvas` y componentes Compose; no hay cifras simuladas ni datos de relleno.

## Interfaz

- Material 3 en claro/oscuro, con estados de carga, vacío y error reutilizables en `OrganizationComponents.kt`.
- Distribución adaptable: listas en una columna en teléfono y tarjetas de estadísticas en varias columnas según el ancho; el contenido respeta el escalado de fuente.
- Acciones con etiquetas accesibles: botones con `contentDescription`, menús nombrados por elemento y confirmaciones destructivas explícitas.
- Los editores de nota, tarea, tabla y evento incorporan selectores de carpeta/categoría, favorito y etiquetas con el mismo componente compartido.

## Archivos de esta etapa

Nuevos, bajo `app/src/main/java/com/example/nexonote/`:

- `domain/model/Organization.kt`
- `domain/repository/OrganizationRepository.kt`
- `data/local/OrganizationDao.kt`
- `data/local/OrganizationMigration.kt`
- `data/repository/LocalOrganizationRepository.kt`
- `ui/organization/OrganizationComponents.kt`
- `ui/organization/FoldersViewModel.kt`
- `ui/organization/FoldersScreen.kt`
- `ui/organization/FavoritesScreen.kt`
- `ui/organization/SearchViewModel.kt`
- `ui/organization/GlobalSearchScreen.kt`
- `ui/organization/StatisticsViewModel.kt`
- `ui/organization/StatisticsScreen.kt`

Modificados:

- `data/local/NexoDatabase.kt`
- `data/repository/LocalNoteRepository.kt`
- `data/repository/LocalTaskRepository.kt`
- `data/repository/LocalTableRepository.kt`
- `data/repository/LocalEventRepository.kt`
- `di/AppContainer.kt`
- `MainActivity.kt`
- `ui/NexoNoteApp.kt`
- `ui/dashboard/DashboardScreen.kt`
- `ui/notes/NoteEditorViewModel.kt`, `ui/notes/NoteEditorScreen.kt`
- `ui/tasks/TaskEditorViewModel.kt`, `ui/tasks/TaskEditorScreen.kt`
- `ui/tables/TablesViewModel.kt`, `ui/tables/TablesUiState.kt`, `ui/tables/TablesScreen.kt`, `ui/tables/TableFormDialogs.kt`
- `ui/calendar/CalendarViewModel.kt`, `ui/calendar/CalendarScreen.kt`, `ui/calendar/EventEditor.kt`
- `README.md` y este documento.

Otros:

- `app/schemas/com.example.nexonote.data.local.NexoDatabase/8.json`
- `app/src/test/java/com/example/nexonote/domain/model/OrganizationTest.kt`
- `app/src/test/java/com/example/nexonote/ui/organization/FoldersUiStateTest.kt`
- `app/src/test/java/com/example/nexonote/testing/FakeOrganizationRepository.kt`
- `app/src/androidTest/java/com/example/nexonote/data/OrganizationRepositoryTest.kt`
- `app/src/androidTest/java/com/example/nexonote/data/DatabaseMigrationTest.kt` (prueba 7→8)
- Pruebas de UI existentes actualizadas con los nuevos parámetros de los ViewModels.

## Verificación

Resultado en `Medium_Phone_API_37.0`: **BUILD SUCCESSFUL**, **61 pruebas unitarias** y **46 instrumentadas** aprobadas. Lint: **0 errores y 24 advertencias** preexistentes (versiones de dependencias, recursos sin uso, etiqueta redundante y sugerencias KTX).

Cobertura añadida:

- CRUD de carpetas, jerarquía, rutas, ciclos y borrado con reubicación de subcarpetas y contenido.
- Traslado de los cuatro tipos de contenido y rechazo de destinos inexistentes.
- Asignación de categorías y etiquetas a notas, tareas, tablas y eventos; recuentos y filtros combinados.
- Búsqueda por título, cuerpo, carpeta, categoría y nombre de etiqueta.
- Favoritos unificados y eliminación individual.
- Vistas guardadas de ida y vuelta y saneado tras borrar una etiqueta.
- Estadísticas con datos duplicados, sin tareas y sin actividad.
- Migración 7→8 con contenido previo y valores por defecto.
- Agrupación de contenidos por carpeta en el estado de la pantalla.

Comando de verificación:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:connectedDebugAndroidTest --console=plain
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. Informes: `app/build/reports/tests/testDebugUnitTest/index.html`, `app/build/reports/androidTests/connected/debug/index.html` y `app/build/reports/lint-results-debug.html`.

## Limitaciones conocidas

- La búsqueda devuelve como máximo 500 coincidencias antes de aplicar los filtros de repositorio; es un límite de seguridad, no una paginación completa.
- La actividad y las series temporales reflejan lo registrado por la aplicación; no se reconstruyen datos anteriores a la Etapa 2.
- No hay sincronización, copia de seguridad en la nube ni adjuntos; todo es local.
- No se ha certificado el recorrido visual en tablets físicas ni con lectores de pantalla reales; las comprobaciones se hicieron en el emulador y con escalado de fuente.
