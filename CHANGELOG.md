# Changelog

Todas las versiones siguen [Semantic Versioning](https://semver.org/lang/es/). Las etiquetas de git llevan prefijo `v` (`v2.0.0`), que es la versión que se pide a JitPack.

## [2.3.0] — 2026-11-24

Versión menor: todo es nuevo o compatible.

### Añadido

- **`CenteredText`** (`utils`): centra líneas en el chat poniendo espacios delante, con el ancho en píxeles de cada carácter de la fuente por defecto. Acepta códigos `&` y MiniMessage mezclados, no mide las etiquetas ni lo que llevan dentro, y cuenta la negrita un píxel más ancha. Mide igual que el `centerMessage` de ExyliaCommons. También `width` para medir una línea.

## [2.2.0] — 2026-11-24

Versión menor: todo es nuevo o compatible.

### Añadido

- **`DepositMenu`** (`modules.menu.extra`): base de los menús en los que el jugador mete ítems, como reponer una tienda o entregar un pedido. Cada menú solo dice qué acepta (`accepts`), qué botones tiene (`buttons`) y qué hace con lo depositado (`deposit`). La base se encarga de:
  - leer los huecos de depósito y no vaciarlos nunca al redibujar; los botones no pueden caer en ellos;
  - aceptar solo lo que admite `accepts`, también con teclas numéricas, arrastres y shift-clic desde el inventario, que además solo llena huecos de depósito; los bundles y la clonación de creativo se rechazan;
  - redibujarse en el tick siguiente a cada cambio;
  - al cerrar o con `finish`, sacar del menú, una sola vez, todo lo que no sea un botón, aunque no esté en un hueco de depósito, antes de entregarlo; devolver lo que no se use; y devolverlo todo si el jugador se está desconectando.
- **`Menu.onPlayerInventoryClick`**: los clics en el inventario del jugador mientras el menú está abierto, que `MenuListener` antes ignoraba.

### Cambiado

- **`InventoryUtils.giveOrDrop`** tira lo que no cabe en pilas del tamaño del ítem; antes podía soltar una sola pila de más de 64.

## [2.1.0] — 2026-11-21

Versión menor: todo es nuevo o compatible. Recoge el código que los plugins GL tenían duplicado.

### Añadido

- **Ciclo de vida.** `Steps` (pasos protegidos de arranque y apagado, con `Dueño#paso` en el registro), `ConfigSet` (base del `ConfigManager`: `track`, `all` y `reload`) y `ConfigFiles` (`checkVersions` y `validateAll` sobre todos los archivos).
- **Comandos.**
  - `SubCommand.filter` y `join`, y `Completions` para usarlos fuera de un subcomando.
  - `HelpSubCommand`: ayuda por líneas de `language.yml` (con las de admin aparte) o por listado de subcomandos con permiso; `describedAs` para su uso y descripción.
- **Acciones y condiciones.**
  - `ActionRunner`: listas completas con `server`, `perm-command`, `wait`, `random`, `block-until-done` y las comprobaciones `permission`, `chance`, `item`, `take`, `if`, `limit` y `cost`, que se comprueban en bloque antes de cobrar nada. Tipos propios con `register`.
  - `UsageLimits` y `YamlUsageLimits` para los usos de `[limit]`.
  - `Condition`, `Period` y `Schedule` (horarios por días, horas y día o noche del mundo).
- **Jugadores.** `PlayerCache<T>`: datos de los jugadores conectados, con cargas que se descartan si el jugador ya salió, y `update` para cambiarlos sin cargarlos.
- **Utilidades.**
  - `Cooldowns` (esperas por jugador y clave, también de duración fija).
  - `Colors` (hexadecimal, nombres de MiniMessage, de Bukkit o una paleta propia) y `FireworkSpec` (leer de la configuración y lanzar).
  - `Amounts` (`1.5k`, `2m`), `TimeZones`, `Position` (ubicación por nombre de mundo) y `EntityVisibility`.
  - `ItemText` (un ítem en una columna de texto).
- **Menús.** `MenuSupport`: título, huecos, iconos con nombre y lore del archivo, pares de placeholders y petición por chat con mensaje de cancelación.
- **WorldGuard.** `WorldGuardHook` (`regionsAt`, `inAny`, `state`, `denied`, `canBuild`), que solo toca clases de WorldGuard si está activo; el plugin no necesita WorldGuard para compilar.
- **`nexus-storage`.** `StorageDefaults` y `DatabaseSettings`: leen el `database.yml` común de los plugins GL, abren el pool SQL o Mongo y explican el fallo de conexión.

### Cambiado

- **`LanguageFile` aplica PlaceholderAPI a la plantilla antes de meter los valores.** Un texto escrito por un jugador o un administrador que llegue como valor se muestra tal cual. Un plugin que pasara a propósito un valor con `%placeholders%` para que se resolviera tiene que resolverlo antes.

## [2.0.0] — 2026-10-31

Versión mayor: cambian las coordenadas y algunas firmas. Los plugins GL se migran en bloque; para un plugin externo basta con los pasos de "Migración desde 1.x".

### Cambios incompatibles

- **Coordenadas.** El proyecto se divide en tres módulos: `com.github.7Str1kes.Nexus:nexus-core`, `nexus-storage` y `nexus-network`. Las coordenadas antiguas (`org.glstudio:Nexus:1.0-SNAPSHOT` en Maven local, `com.github.7Str1kes:Nexus` en JitPack) ya no se publican.
- **Build.** Se pasa a Gradle con Kotlin DSL y version catalog, y Paper API pasa a `1.21.11-R0.1-SNAPSHOT`.
- **`Nexus.init(plugin)` ya no imprime el banner.** El plugin llama a `LoggerUtils.sendEnable(plugin)` al terminar su arranque, y `Nexus.shutdown(plugin)` imprime el de apagado. Así no salen banners duplicados ni un "enabled" de un arranque que luego falla.
- **`NexusAPI`.** Se eliminan `getTasks()`, `getDurationParser()`, `getFormatUtils()`, `getLocationUtils()` y `getLoggerUtils()`, que devolvían instancias de clases cuyos métodos son estáticos; ningún plugin GL los usaba.
- **`DynamicCommand`.** El constructor pasa a ser `DynamicCommand(CommandManager, Command)`. Es una clase interna del registro de comandos.
- **Incompatibilidades binarias** (el código fuente compila igual, pero un plugin compilado contra 1.x debe recompilarse):
  - `CommandManager.register(Command)` devuelve `boolean`.
  - `Placeholders.applyWithPapi` recibe `OfflinePlayer`.
  - `CC`, `LoggerUtils`, `InventoryUtils`, `DurationParser` y `FormatUtils` son `final` con constructor privado.

### Añadido

- **Ciclo de vida y diagnóstico.**
  - `Nexus.shutdown`/`isInitialized`, y `LoggerUtils.configure` con prefijo por plugin y banners de una sola vez.
  - `Diagnostics`: cadena de causas, causa raíz y SQLState.
  - `Futures.log`/`guard`, que registran el fallo sin tragarlo.
- **Concurrencia.**
  - `NexusExecutors` (pools con nombre, escritores ordenados, scheduler, apagado con drenaje).
  - `PersistenceQueue`.
  - `Tasks.mainThread()` y `Tasks.hop(future)`, y variantes de `Tasks` que devuelven `BukkitTask`.
- **Comandos.**
  - `CommandManager.registerAll`, `unregister` y `unregisterAll`, que retiran los comandos del `CommandMap` de Bukkit.
  - `bind(PluginCommand, Command)`, formato de texto y mensajes por manager, y aviso cuando una etiqueta ya está ocupada.
  - `CommandGroup`/`SubCommand` con contexto, alias, permisos por subcomando y tab-complete filtrado.
- **Configuración.**
  - `ConfigFile.resolved()` (`ResolvedConfig`: archivo, luego jar, luego valor por defecto).
  - En `ConfigFile`: `config-version` con `checkVersion` (un archivo antiguo se actualiza solo: cambia su línea de versión y conserva valores, comentarios y formato), `unknownKeys`/`missingKeys`, `saveAtomically`, `validate` y `bundled`.
  - `ConfigMigrator`, `ConfigProblems`, `YamlFiles` (escritura atómica) y `KeyStyle`.
- **Idioma.** `LanguageFile` (prefijo, claves con respaldo en el jar, MiniMessage o legacy, PlaceholderAPI opcional, listas, action bar, broadcast). `Placeholders.apply(text, Object... pairs)` en una sola pasada.
- **Menús.**
  - `MenuConfig`: título, tamaño, ítems, `slots`, botones condicionados, extras y controles de página.
  - `ButtonPresets` y `AsyncPaginatedMenu`.
  - Nombre y lore en MiniMessage en `ItemBuilder`/`ButtonBuilder`.
  - Antirrebote de clics por menú.
- **Entrada por chat.** `ChatInputManager.request(player)` con palabras de cancelación, tiempo límite, cierre de inventario y recorte. Lee `originalMessage()`.
- **Ítems.**
  - `ItemCodec`: formato binario de Paper `v2:`/`v2z:`, lectura de los formatos antiguos y distinción entre vacío y corrupto.
  - `InventoryUtils.give`/`giveOrDrop` con `GiveResult`.
- **Duraciones.** `DurationParser.tryParse` (estricto) y `DurationFormat` (compacto, reloj, "hace", patrón).
- **Registros y efectos.** `Registries` (sonidos, partículas, efectos, encantamientos, entidades, atributos y materiales por clave), `SoundSpec` y `ParticleSpec`.
- **Jugadores y eventos.** `Players`/`PlayerRef` (resolución de nombres, vanish, visibilidad) y `EventUtils.call`/`callAndExplain` (con el mensaje fijo o leído después de lanzar el evento).
- **Acciones.** `ActionLine` y `ActionRegistry` (`[console]`, `[player]`, `[message]`, `[broadcast]`, `[actionbar]`, `[title]`, `[sound]`).
- **Integraciones opcionales.** `VaultEconomy`/`EconomyResult` (sin tocar clases de Vault si no está instalado), `NexusExpansion` y `PlaceholderHooks`.
- **Proxy.** `ProxyMessenger` (conexión a otro servidor y lista de servidores por el canal BungeeCord).
- **`nexus-storage`.**
  - `DatabaseType`, `PoolOptions` y `DataSources` (Hikari con URLs de MySQL, MariaDB, PostgreSQL, SQLite y H2; `closeH2` para cerrar H2 con `SHUTDOWN`).
  - `SqlDialect` (upsert/replace por motor) y `Schema` (columnas e índices idempotentes, tabla de versión).
  - `Jdbc`, `ConnectionHints`, `MongoConnector` y `StorageException`.
- **`nexus-network`.**
  - `NetworkPacket`/`TrackedPacket`/`AbstractPacket`, `PacketCodec` (HMAC-SHA256, comparación en tiempo constante) y `PacketDeduplicator`.
  - `NetworkSettings` (claves kebab o UPPER_SNAKE, `${VARIABLE}`; `fromConfig` recurre al jar clave a clave) y `RedisConnector` (rechaza Redis sin contraseña y secretos débiles salvo `allow-insecure`).
- `RedisConnector.connect(String, boolean)`: acepta un secreto débil avisando en consola, para plugins cuyas versiones anteriores lo permitían (Chat).
- `ButtonBuilder.parseMaterial`, `MenuConfigUtils.parseMaterial` y `Registries` aceptan nombres con guion (`gray-stained-glass-pane`). `MenuConfig` lee `flags` además de `item-flags`, con guiones y el valor `ALL`.
- `RedisConnector` admite límites de pool propios y `resource()` para pedir una conexión y lanzar varios comandos.
- `PacketCodec.decodeUnverified` para redes que renuncian a firmar.
- `DataSources.create(..., Map)` para pasar propiedades al driver, y `CommandGroup.extraCompletions` para sugerir algo más que subcomandos en el primer argumento.
  - `PacketChannel` (pub/sub con reconexión y Redis Streams para entrega dirigida) y `NetworkSupervisor`.
- **Documentación y tests.** Javadoc en todas las clases y miembros públicos y protegidos; tests unitarios de placeholders, duraciones, `CC`, `ItemCodec`, `ConfigFile`, `CommandManager`, almacenamiento (H2) y paquetes firmados.

### Cambiado

- Los mensajes de `Command` (`sendMessage`, `sendUsage`, permiso, solo jugadores) usan el formato del `CommandManager`. Antes una plantilla MiniMessage se enviaba como texto literal.
- `CommandManager.unregister` solo retira las entradas del `CommandMap` que apuntan a sus propios comandos; un comando homónimo de otro plugin no se toca.
- `ButtonBuilder.parseMaterial` usa `Material.matchMaterial`, que también acepta `minecraft:stone`, y avisa con contexto si el material no existe.
- `Command.filterTabCompletion` compara con `Locale.ROOT`.
- `ConfigFile.getConfigurationSection` recurre a la copia del jar cuando la sección no está en el archivo.
- `FormatUtils.formatMoneyToString` es seguro entre hilos.

### Obsoleto (se eliminará en 3.0.0)

- `ItemSerialization`: sustituir por `ItemCodec`.
- `Tasks.execute*`/`executeScheduled*`: sustituir por `run`, `runAsync`, `runLater`, `runLaterAsync`, `timer` y `timerAsync`.
- `Command.setNoPermissionMessage`/`setPlayersOnlyMessage`: sustituir por `CommandManager.messages(...)` o `LanguageFile.applyTo(...)`.
- `FormatUtils.formatTimeToString`: sustituir por `DurationFormat`.

### Corregido

- `InventoryUtils.giveOrDrop(Player, ItemStack)` soltaba la pila completa en vez de solo lo que no cupo.

### Migración desde 1.x

1. Sustituir la dependencia por `com.github.7Str1kes.Nexus:nexus-core:v2.0.0` y quitar `mavenLocal()`.
2. Llamar a `LoggerUtils.sendEnable(this)` al final de un arranque correcto, y a `Nexus.shutdown(this)` en `onDisable`.
3. Sustituir `NexusAPI.get().getTasks()` y similares por las clases estáticas (`new Tasks(plugin)`, `DurationParser.parse(...)`...).
4. Recompilar.

## [1.1.13] — 2026-09-06
- `ConfigFile#getConfigurationSection` ya no devuelve una sección vacía.

## [1.1.12] — 2026-09-06
- `ButtonBuilder.parseMaterial(name, fallback, context)`.

## [1.1.11] — 2026-09-05
- `PaginatedMenu` ya no coloca el botón de "vacío" en todas las ranuras.

## [1.1.1] – [1.1.10] — 2026-09-05
- Ajustes del sistema de menús:
  - `Menu#getSize`, y `PaginatedMenu#page` protegido.
  - Protección de `openLater`/`update` ante un plugin desactivado o un jugador desconectado.
  - Atajos de `ButtonBuilder`/`Button`, y alias de `MenuManager`.
  - `Menu#open` y `Menu#shouldCancelDrag` sobrescribibles.
  - `ConfirmMenu.confirm()` y ranuras decorativas.

## [1.1.0] — 2026-09-05
- Sistema genérico de menús (`Menu`, `PaginatedMenu`, `ConfirmMenu`, `MenuManager`, `Button`).

## [1.0.6] — 2026-08-11
- Las recargas de `ConfigFile` se pueden leer con seguridad desde otros hilos.

## [1.0.4] – [1.0.5] — 2026-08-05
- Soporte de MiniMessage en `CC`, y `TextFormat` para elegir entre MiniMessage y legacy. MiniMessage acepta códigos `&`.

## [1.0.3] — 2026-08-04
- Constructores de componentes, corrección de `Tasks` y comprobación de permisos en comandos.

## [1.0.0] – [1.0.2] — 2026-06-21 / 2026-07-13
- Nexus deja de ser un plugin y pasa a ser una librería que se empaqueta en cada plugin. Utilidades de inventario, ubicaciones, configuración y comandos.
