# Etapa 6 — Calendario inteligente y recordatorios

## Uso

El icono «Abrir calendario» de la barra superior y la tarjeta «Calendario · Próximos 7 días» del dashboard abren el módulo. La barra inferior conserva sus cinco destinos. El calendario ofrece Mes, Semana y Día, filtros Todo/Tareas/Eventos, selección de fecha desde el encabezado (incluido año) y acceso rápido a Hoy.

La semana muestra siete columnas desplazables horizontalmente en teléfonos y una escala horaria vertical; las actividades que coinciden ocupan carriles separados. Las actividades de día completo se muestran fuera de la escala horaria. Las tarjetas de la agenda abren eventos o el editor de tareas existente.

## Persistencia y arquitectura

- Room versión 7, esquema exportado `app/schemas/com.example.nexonote.data.local.NexoDatabase/7.json`.
- Migración aditiva `MIGRATION_6_7`: tabla `events`, índices y relación autorreferenciada en cascada. Las tablas previas se conservan.
- `EventEntity` contiene también el recordatorio opcional (`reminderMinutes`), revisión, ocurrencia planificada y última ocurrencia notificada. Es el equivalente de una relación uno-a-uno de recordatorio; cada evento admite un aviso.
- `EventDao` emite `Flow`; `LocalEventRepository` realiza operaciones transaccionales. `CalendarViewModel` combina eventos y `TaskRepository.tasks` con StateFlow, SavedStateHandle y cálculo fuera del hilo principal.
- Las tareas son proyecciones de sus entidades existentes. El calendario no genera tareas recurrentes por adelantado ni las duplica: muestra las ocurrencias que ya gestiona el módulo Tareas.
- La tarjeta del dashboard observa el mismo repositorio y calcula los próximos siete días.

## Contrato de fechas y recurrencias

- Inicio y final se guardan como instantes, junto con la zona horaria de la serie. La interfaz muestra horas en la zona del dispositivo y explica la zona de la serie.
- Las recurrencias mantienen la hora civil de la zona original al cambiar de zona del dispositivo o al entrar/salir del horario de verano. No desplazan todos los eventos para conservar la hora del nuevo dispositivo.
- Los eventos de día completo usan inicio a medianoche y final exclusivo a medianoche del día posterior. El editor presenta una fecha final inclusiva. Su duración se calcula en días civiles, que pueden durar 23 o 25 horas.
- Repetición diaria, semanal y mensual. El límite de repetición incluye la fecha elegida en la zona de la serie.
- La repetición mensual se calcula desde el día original: 31 enero → 28/29 febrero → 31 marzo, sin arrastrar el ajuste de febrero.
- No se materializan copias infinitas. Las ocurrencias se expanden para el intervalo consultado.
- **Esta ocurrencia:** crea/actualiza una excepción identificada por serie e inicio original; puede moverse de fecha sin dejar duplicada la original. Eliminarla guarda una exclusión.
- **Toda la serie:** actualiza la regla y restablece sus excepciones. El editor lo advierte antes de guardar. Eliminar la serie elimina también sus excepciones.
- Categoría opcional de texto y color persistente quedan disponibles para futuras agrupaciones y estadísticas.

## Recordatorios

Opciones: al momento, 5, 10, 15 o 30 minutos, 1 hora o 1 día antes. Se reutilizan WorkManager, el canal de notificaciones y el receptor de cambios de hora/reinicio de Etapa 4. La notificación abre el calendario en la fecha del evento.

Se solicita `POST_NOTIFICATIONS` desde el editor al activar un aviso en Android 13+. Rechazarlo no impide guardar el evento. Los permisos y la habilitación de notificaciones se comprueban de nuevo antes de publicar.

Cada evento tiene trabajo único `event_reminder_<id>`. Editar reemplaza el trabajo; eliminar lo cancela. El worker comprueba revisión, vigencia de la ocurrencia y última entrega persistida antes de publicar. Una ocurrencia planificada se conserva aunque Android retrase el worker o se reinicie el dispositivo; entregar un aviso no descarta otro pendiente a la misma hora. Tras entregar se programa la próxima ocurrencia. El arranque de la aplicación y los broadcasts de reinicio/cambio de hora/zona vuelven a reconciliar los trabajos.

La marca de entrega se persiste antes de publicar para priorizar la ausencia de duplicados. Como en cualquier operación entre SQLite y NotificationManager, no existe una transacción atómica entre ambos: una terminación del proceso justo entre la marca y la publicación podría perder ese aviso.

### WorkManager frente a AlarmManager

Esta etapa implementa avisos diferibles, no alarmas exactas. WorkManager persiste los trabajos y respeta las restricciones energéticas del sistema. No garantiza el minuto exacto ni entrega mientras el dispositivo está apagado o la aplicación permanece detenida forzosamente.

Se evaluó AlarmManager: para garantizar alarmas exactas habría que definir ese requisito de producto, gestionar el acceso especial a alarmas exactas y sus restricciones en Android moderno. No se solicita ese permiso para estos avisos diferibles. El editor informa de posibles retrasos; no se implementan ni se prometen alarmas exactas.

## Archivos de esta etapa

Nuevos, bajo `app/src/main/java/com/example/nexonote/`:

- `domain/model/Events.kt`
- `domain/repository/EventRepository.kt`
- `data/local/EventDao.kt`
- `data/local/EventMigration.kt`
- `data/repository/LocalEventRepository.kt`
- `reminder/EventReminderWorker.kt` (scheduler, worker de entrega y worker de reconciliación)
- `ui/calendar/CalendarViewModel.kt`
- `ui/calendar/CalendarScreen.kt`
- `ui/calendar/EventEditor.kt`

Modificados en ese mismo directorio:

- `data/local/NexoDatabase.kt`
- `di/AppContainer.kt`
- `reminder/ReminderNotifications.kt`
- `reminder/ReminderRescheduleReceiver.kt`
- `NexoNoteApplication.kt`
- `MainActivity.kt`
- `ui/NexoNoteApp.kt`
- `ui/dashboard/DashboardScreen.kt`

Otros archivos:

- `app/build.gradle.kts`: orden de exportación KSP y empaquetado de esquemas para las pruebas de migración.
- `app/schemas/com.example.nexonote.data.local.NexoDatabase/7.json`
- `app/src/test/java/com/example/nexonote/domain/model/EventsTest.kt`
- `app/src/androidTest/java/com/example/nexonote/data/EventRepositoryTest.kt`
- `app/src/androidTest/java/com/example/nexonote/data/EventWorkManagerTest.kt`
- `app/src/androidTest/java/com/example/nexonote/ui/CalendarFlowTest.kt`
- `README.md` y este documento.

## Verificación

Resultado final en `Medium_Phone_API_37.0`: **BUILD SUCCESSFUL**, **51 pruebas unitarias** y **37 instrumentadas** aprobadas. Lint: **0 errores y 24 advertencias** preexistentes (versiones de dependencias, recursos sin uso, etiqueta redundante y sugerencia KTX en tablas). No quedan fallos conocidos de las verificaciones ejecutadas.

APK: `app/build/outputs/apk/debug/app-debug.apk`. Informes: `app/build/reports/tests/testDebugUnitTest/index.html`, `app/build/reports/androidTests/connected/debug/index.html` y `app/build/reports/lint-results-debug.html`.

Comando de verificación:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:connectedDebugAndroidTest --console=plain
```

Las pruebas incluyen los módulos anteriores, CRUD y persistencia de eventos, migración, proyección de tareas, recurrencias, zonas horarias y horario de verano, excepciones, carriles de superposición, recuperación/deduplicación de recordatorios, trabajos reales WorkManager y flujo Compose de calendario.

No se ha realizado una certificación de puntualidad en dispositivos físicos bajo Doze ni un recorrido visual exhaustivo en tablets. Las pruebas de cambios horarios usan zonas y fechas controladas; la persistencia se comprueba cerrando/reabriendo Room y reconstruyendo el repositorio. Esas comprobaciones no equivalen a un reinicio físico del dispositivo.
