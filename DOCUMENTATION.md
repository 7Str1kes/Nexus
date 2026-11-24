# Nexus — Biblioteca compartida de los plugins GL

Nexus es la librería común de los plugins `GL-*` para Paper. No es un plugin: cada plugin la empaqueta (shade) dentro de su jar y usa su propia copia. Como Paper resuelve primero las clases del jar de cada plugin, el estado estático de Nexus (prefijo de consola, mensajes de comandos...) es independiente en cada plugin.

Versión actual: **2.2.0**. Cambios por versión en `CHANGELOG.md`.

## Índice

1. [Módulos y coordenadas](#1-módulos-y-coordenadas)
2. [Ciclo de vida del plugin](#2-ciclo-de-vida-del-plugin)
3. [Consola y diagnóstico](#3-consola-y-diagnóstico)
4. [Concurrencia](#4-concurrencia)
5. [Comandos y subcomandos](#5-comandos-y-subcomandos)
6. [Configuración](#6-configuración)
7. [Idioma y placeholders](#7-idioma-y-placeholders)
8. [Menús](#8-menús)
9. [Entrada por chat](#9-entrada-por-chat)
10. [Ítems e inventario](#10-ítems-e-inventario)
11. [Duraciones](#11-duraciones)
12. [Efectos y registros](#12-efectos-y-registros)
13. [Jugadores, eventos y acciones](#13-jugadores-eventos-y-acciones)
14. [Integraciones opcionales: Vault, PlaceholderAPI y WorldGuard](#14-integraciones-opcionales-vault-placeholderapi-y-worldguard)
15. [Proxy](#15-proxy)
16. [nexus-storage](#16-nexus-storage)
17. [nexus-network](#17-nexus-network)
18. [Geometría y otras utilidades](#18-geometría-y-otras-utilidades)
19. [Desarrollo de Nexus](#19-desarrollo-de-nexus)

---

## 1. Módulos y coordenadas

| Módulo | Contenido | Dependencias de ejecución que aporta el plugin |
|---|---|---|
| `nexus-core` | Todo lo que no necesita librerías externas | Ninguna (Vault y PlaceholderAPI son opcionales y se cargan solo si están instalados) |
| `nexus-storage` | Pools JDBC, dialectos, esquema, Mongo | HikariCP, el driver JDBC y/o el driver de Mongo (por `libraries:` en `plugin.yml`) |
| `nexus-network` | Redis, paquetes firmados, Streams | Jedis (empaquetado y relocado) y Gson (lo aporta Paper) |

Se publica en JitPack como build multimódulo:

```toml
# gradle/libs.versions.toml
[versions]
nexus = "v2.2.0"

[libraries]
nexus-core = { module = "com.github.7Str1kes.Nexus:nexus-core", version.ref = "nexus" }
nexus-storage = { module = "com.github.7Str1kes.Nexus:nexus-storage", version.ref = "nexus" }
nexus-network = { module = "com.github.7Str1kes.Nexus:nexus-network", version.ref = "nexus" }
```

```kotlin
// build.gradle.kts
repositories {
    maven("https://jitpack.io")
}

dependencies {
    implementation(libs.nexus.core)
    implementation(libs.nexus.storage)   // solo si usa base de datos
    implementation(libs.nexus.network)   // solo si usa Redis
}

tasks.shadowJar {
    // Nexus no se reloca. Jedis sí, junto con lo que trae:
    relocate("redis.clients", "org.glstudio.<id>.libs.jedis")
    relocate("org.apache.commons.pool2", "org.glstudio.<id>.libs.pool2")
    relocate("org.json", "org.glstudio.<id>.libs.json")
}
```

`nexus-storage` y `nexus-network` incluyen `nexus-core` como dependencia `api`.

**Desarrollo contra una copia local de Nexus**, sin tocar el build del plugin: el `group` de los módulos coincide con las coordenadas de JitPack, así que Gradle sustituye la dependencia por el proyecto local:

```bash
./gradlew build --include-build /ruta/a/Nexus
```

## 2. Ciclo de vida del plugin

```java
public final class GTeleports extends JavaPlugin {
    @Override
    public void onEnable() {
        Nexus.init(this);                       // prefijo de consola, idempotente
        if (!new StartupService(this).start()) {
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        new ShutdownService(this).stop();
        Nexus.shutdown(this);                   // banner de "disabled", una sola vez
    }
}
```

- `Nexus.init(plugin)` ya **no** imprime el banner. El plugin lo imprime al terminar un arranque correcto con `LoggerUtils.sendEnable(plugin)`. Tanto `sendEnable` como `sendDisable` solo se imprimen una vez por arranque.
- `NexusAPI.get().createCommandManager(plugin)` (y las demás factorías) llaman a `Nexus.init(plugin)` si nadie lo hizo.

Los pasos de `StartupService` y `ShutdownService` se protegen con `Steps`: cada paso registra su fallo como `Dueño#paso` con la causa. Al arrancar, el primer paso que falla detiene el arranque; al apagar, los demás siguen.

```java
private static final Steps STEPS = Steps.of("StartupService");

private boolean loadDatabase() {
    return STEPS.run("loadDatabase", () -> {
        plugin.getDatabaseManager().start();
        return true;
    });
}

// ShutdownService
STEPS.always("closeDatabase", () -> plugin.getDatabaseManager().stop());
```

## 3. Consola y diagnóstico

```java
LoggerUtils.logInfo("Database ready.");               // [GL-Teleports] [INFO] Database ready.
LoggerUtils.setDebugEnabled(config.getBoolean("debug", false));
LoggerUtils.logDebug("Cache", "Loaded 32 homes");

try { ... } catch (Exception e) {
    Diagnostics.logFailure("HomeManager#load", e);     // cadena de causas en una línea + traza de la raíz
}
String why = Diagnostics.describeRootCause(error);     // para un mensaje de estado
```

`Diagnostics.logFailure` desenvuelve `CompletionException`/`ExecutionException`, resume la cadena (`A: msg <- B: msg [SQLState=..]`) y muestra la traza de la causa raíz.

Fallos de futuros (`org.glstudio.nexus.concurrent.Futures`): ninguno traga el error.

```java
Futures.setFailureNotifier((sender, error) -> messages.send(sender, "errors.database-error"));

Futures.log(provider.save(home), "HomeManager#save");                   // solo registra
Futures.guard(provider.load(uuid), "HomeCommand#execute", sender);      // registra y avisa al sender
Futures.guard(future, "Ctx", error -> rollback());                      // callback propio
```

## 4. Concurrencia

```java
NexusExecutors executors = NexusExecutors.builder(getName())
        .ioThreads(4)            // pool para trabajo independiente
        .ordered("storage")      // escritor único: el orden de escritura importa
        .scheduler(true)         // reintentos y trabajos periódicos
        .build();

executors.io();                                // Executor
executors.ordered("storage");                  // Executor de un hilo
executors.supply("HomeRepo#load", () -> ...);  // cuenta y registra fallos
executors.pendingTasks(); executors.failureCount();
executors.shutdown(Duration.ofSeconds(10));    // drena y para
```

Vuelta al hilo principal con `Tasks`:

```java
Tasks tasks = new Tasks(plugin);
future.thenAcceptAsync(home -> player.teleport(home), tasks.mainThread());  // inline si ya está en el hilo principal
tasks.hop(future).thenAccept(...);                                          // completa en el hilo principal
BukkitTask task = tasks.timerAsync(20, 20 * 60, this::purge);               // todas las variantes devuelven BukkitTask
```

`PlayerCache<T>` guarda los datos de los jugadores conectados: se cargan al entrar, se mantienen mientras juegan y se sueltan al salir. Una carga que termina cuando el jugador ya se fue se descarta, así que nunca queda nadie en la caché:

```java
PlayerCache<Profile> profiles = new PlayerCache<>(tasks);

profiles.load(player, repository.load(player.getUniqueId()))      // completa en el hilo principal
        .thenAccept(kept -> kept.ifPresent(profile -> render(player)));
profiles.get(uuid);                                               // Optional, vacío mientras carga
profiles.update(uuid, stats -> stats.withWin(name));               // solo si está cargado
Profile left = profiles.unload(uuid);                              // al salir, para guardarlo
```

`PersistenceQueue` es un escritor único con métricas de salud (fallos consecutivos, escrituras lentas) y ejecución en línea durante el apagado:

```java
PersistenceQueue queue = PersistenceQueue.builder("GL-Clans-Persistence")
        .failureWarning(n -> "Clan persistence has failed " + n + " times in a row.")
        .build();
queue.submit("save clan " + id, () -> repository.save(clan));
queue.shutdownAndDrain();
```

## 5. Comandos y subcomandos

```java
CommandManager manager = NexusAPI.get().createCommandManager(this);
messages.applyTo(manager, "errors.no-permission", "errors.player-only"); // formato + textos

manager.registerAll(new HomeCommand(manager, "home", Permissions.HOME_USE, this),
                    new WarpCommand(manager, "warp", Permissions.WARP_USE, this));
// onDisable / recarga:
manager.unregisterAll();   // quita del CommandMap de Bukkit solo lo que registró este manager
```

- `register` usa `Bukkit.getCommandMap()` y devuelve `false` (con el error registrado) si falla. Si otro plugin ya usa la etiqueta, avisa y el comando queda accesible como `/<plugin>:<etiqueta>`.
- `bind(plugin.getCommand("x"), comando)` enlaza un comando declarado en `plugin.yml` con la misma política de permisos y textos.
- `Command.sendMessage`, `sendUsage`, `requirePlayer`, `requirePermission` y `requireArgs` usan el formato del manager (`TextFormat.LEGACY` por defecto, `MINIMESSAGE` si se configura).

Subcomandos:

```java
public class ClanCommand extends CommandGroup<Void> {
    public ClanCommand(CommandManager manager, GClans plugin) {
        super(manager, "clan", Permissions.USE);
        register(new CreateSubCommand(plugin));
        register(new InviteSubCommand(plugin));
    }

    @Override
    protected void onNoArgs(CommandSender sender, Void context) {
        // abrir el menú principal
    }
}

public class CreateSubCommand extends SubCommand<Void> {
    public CreateSubCommand(GClans plugin) {
        super("create", Permissions.CREATE, "new");
    }

    @Override
    public void execute(CommandSender sender, Void context, String[] args) { ... }
}
```

El grupo busca por nombre o alias, comprueba el permiso del subcomando (o el del grupo si el subcomando no tiene) y filtra el tab-complete por permiso. El contexto genérico permite pasar, por ejemplo, la economía a la que pertenece un comando de divisa.

Dentro de un `SubCommand`, `filter(opciones, escrito)` deja las opciones que empiezan por lo escrito (sin distinguir mayúsculas) y `join(args, desde)` une los argumentos restantes. Fuera de un subcomando están en `Completions.filter` y `Completions.join`.

La ayuda tiene dos formas en `HelpSubCommand`:

```java
// Líneas de language.yml; los administradores reciben además las suyas.
register(HelpSubCommand.lines(messages, "help.player", "help.admin", sender -> sender.hasPermission(Permissions.ADMIN)));

// Una línea por subcomando con permiso, a partir de su uso y su descripción.
register(HelpSubCommand.<Void>listing("", this::getSubCommands,
        sender -> messages.send(sender, "help.header"),
        (sender, sub) -> messages.send(sender, "help.entry", "{usage}", sub.getUsage(), "{description}", sub.getDescription()))
        .describedAs(() -> messages.raw("help.usage"), () -> messages.raw("help.description")));
```

`help.send(sender)` la muestra desde `onNoArgs` u `onUnknown`, y `HelpSubCommand.sendLines(...)` envía las líneas sin registrar el subcomando.

`extraCompletions(sender, context)` añade sugerencias al primer argumento, como los alias de los subcomandos o una palabra que no sea un subcomando. Se autocompletan los nombres, no los alias, salvo que se devuelvan aquí.

## 6. Configuración

`ConfigFile` (un YAML de la carpeta del plugin, copiado del jar si no existe) tiene tres formas de leer:

| Lectura | Archivo | Jar | Valor por defecto | Uso |
|---|---|---|---|---|
| `getInt(path)`, `getString(path)`... | sí | sí | 0 / null y aviso en consola | claves que siempre deben existir |
| `getInt(path, def)`... (Bukkit) | sí | no | `def` | cuando el llamador decide el valor |
| `resolved().getInt(path, def)`... | sí | sí | `def` | **ajustes** (lo habitual) |

```java
ResolvedConfig settings = configManager.getMainConfig().resolved();
int delay = settings.getInt("teleport-settings.home.delay", 3);
```

Validación, versión y migración:

```java
ConfigFile main = configManager.getMainConfig();
main.checkVersion(CONFIG_VERSION, LoggerUtils::logWarn);   // un archivo antiguo se actualiza solo: cambia la línea de versión y nada más
main.unknownKeys(1, List.of("home-limits"))
    .forEach(key -> LoggerUtils.logWarn("config.yml has an unknown key '" + key + "'."));

ConfigMigrator.of(main)
        .step(2, cfg -> ConfigMigrator.move(cfg, "MULTI_SERVER.REDIS.HOST", "multi-server.redis.host"))
        .migrate(2);   // solo escribe el archivo si se aplicó algún paso

ConfigProblems problems = new ConfigProblems();
problems.atLeast(main, "teleport-settings.tpa.request-timeout", 1);
problems.enumValue(main, "bossbar.color", BossBar.Color.class);
problems.report("config.yml");
```

- `saveAtomically()` escribe en un temporal y lo mueve, y `YamlFiles.writeAtomically(path, texto)` hace lo mismo para cualquier archivo.
- `validate()` parsea el archivo en disco sin aplicarlo; sirve para que una recarga no cambie nada si hay un error de sintaxis.
- `KeyStyle` (`KEBAB`, `UPPER_SNAKE`, `ANY`) resuelve claves escritas como `custom-model-data` o `CUSTOM_MODEL_DATA`.

El `ConfigManager` de un plugin extiende `ConfigSet` y registra cada archivo con `track`. `all()` los devuelve en orden y `reload()` los recarga; un plugin que recalcula algo al recargar sobrescribe `reload()` y llama a `super.reload()`.

```java
public class ConfigManager extends ConfigSet {
    public ConfigManager(JavaPlugin plugin) {
        this.mainConfig = track(new ConfigFile(plugin, "config.yml"));
        this.languageConfig = track(new ConfigFile(plugin, "language.yml"));
    }
}

ConfigFiles.checkVersions(configs.all(), CONFIG_VERSION);                 // checkVersion de cada uno
if (!ConfigFiles.validateAll(configs.all(), (file, error) ->              // antes de recargar nada
        messages.send(sender, "admin.reload-failed", "{file}", file.getFileName(), "{error}", error))) {
    return false;
}
```

Zonas horarias: `TimeZones.tryParse(raw)` (vacío = la del servidor; `Optional.empty()` si no es una zona) y `TimeZones.parse(raw, aviso)`, que avisa y usa la del servidor.

## 7. Idioma y placeholders

```java
LanguageFile messages = new LanguageFile(configManager.getLanguageConfig(), TextFormat.MINIMESSAGE)
        .prefixPath("prefix")
        .papi(true);

messages.send(player, "home.teleported", "{home}", home.getName());   // con prefijo
messages.sendLines(sender, "help");                                    // lista, sin prefijo
messages.actionBar(player, "teleport.countdown", "{seconds}", 3);
messages.broadcast("event.started");
Component title = messages.component("menu.title", "{page}", 2);
String raw = messages.raw("errors.no-permission");
```

- Claves ausentes en el archivo se resuelven contra la copia del jar. Un mensaje vacío no se envía (así un administrador puede silenciarlo).
- PlaceholderAPI se aplica a la plantilla **antes** de meter los valores. Un texto escrito por un jugador o un administrador que llegue como valor (un nombre, una condición) se muestra tal cual, sin que se resuelvan sus `%placeholders%`.
- Placeholders por pares, en una sola pasada: un valor escrito por un jugador nunca se vuelve a escanear. `"{player}"` y `"%player%"` coinciden literalmente, y `"player"` (sin delimitadores) coincide con las dos formas.

```java
Placeholders.apply("Hola {player}", "{player}", name);
Placeholders.apply("Página %page%", Map.of("page", "2"));
Placeholders.applyWithPapi(player, text, "{x}", 1);
```

## 8. Menús

Conceptos (`org.glstudio.nexus.modules.menu`):

- **`Menu`**: GUI de un jugador; `render()` reconstruye los botones en cada `open()`/`update()`. Antirrebote de clics configurable por menú (`getClickCooldownMillis()`, 200 ms por defecto).
- **`MenuManager`**: uno por plugin; `closeAll()` en `onDisable()`/recarga.
- **`Button` / `ButtonBuilder`**: ítem + acción. `name(text, TextFormat)` y `lore(lines, TextFormat)` aceptan MiniMessage (sin cursiva por defecto).
- **`PaginatedMenu<T>`**: lista en memoria repartida en páginas.
- **`AsyncPaginatedMenu<T>`**: cada página se consulta al cambiar (`loadPage(page, size)` devuelve un `CompletableFuture<Page<T>>`); muestra `loadingButton()` mientras carga y descarta resultados obsoletos.
- **`ConfirmMenu`**: diálogo confirmar/cancelar; cerrar sin decidir cuenta como cancelar.
- **`ButtonPresets`**: relleno, "cargando", cerrar, anterior/siguiente; los textos se pasan como parámetros.
- **`MenuConfig`**: lectura de un `menus/*.yml`.

```java
MenuConfig cfg = new MenuConfig(configManager.getHomesMenu(), KeyStyle.ANY, TextFormat.LEGACY);

public class HomesMenu extends PaginatedMenu<Home> {
    @Override
    protected Button toButton(Home home) {
        return cfg.builder("items.home", "%name%", home.getName(), "%world%", home.getWorld())
                .onClick(() -> teleport(home))
                .build();
    }

    @Override
    protected Map<Integer, Button> getStaticButtons(Player viewer, int page, int maxPage) {
        Map<Integer, Button> buttons = new HashMap<>(cfg.pageControls(this, "items.previous-page", "items.next-page"));
        cfg.put(buttons, "items.close", viewer::closeInventory);
        cfg.putGated(buttons, "items.admin", viewer.hasPermission("x.admin"), this::openAdmin);
        cfg.putExtras(buttons, "extra");     // decoración libre del administrador, nunca pisa botones
        return buttons;
    }
}
```

`MenuSupport` reúne lo que repetían los menús de los plugins: `config(file)` (un `MenuConfig` con `KeyStyle.ANY` y MiniMessage), `title(config, respaldo, pares...)`, `slots`/`contentSlots` (los huecos de un ítem o del contenido, dentro del tamaño), `icon(item, config, ruta, pares...)` (un ítem del juego con el nombre y la lore del archivo), `pairs(mapa)`/`append(pares, más...)` y `prompt(...)`, que pide un texto por chat con el mensaje de `key` y avisa con `cancelledKey` si se cancela.

Menús en los que el jugador mete ítems (reponer, entregar): `DepositMenu`.

```java
public class RestockMenu extends DepositMenu {
    public RestockMenu(...) {
        super(manager, viewer, MenuSupport.title(config, "Restock"), config.size(54),
                MenuSupport.contentSlots(config, config.size(54)));          // content-slots del YAML
    }

    @Override protected boolean accepts(ItemStack item) { return item.isSimilar(shop.getItem()); }
    @Override protected Map<Integer, Button> buttons() { ... }                // con deposited() para los números
    @Override protected List<ItemStack> deposit(List<ItemStack> items) {     // lo que devuelve vuelve al jugador
        int stored = store(items);
        return rest;
    }
}
```

- Solo entra lo que `accepts` admite: al colocar, con teclas numéricas, arrastrando o con shift-clic desde el inventario, que además solo llena los huecos de depósito. Los bundles y la clonación de creativo se rechazan.
- Al cerrar, o con `finish(true)` desde un botón, sale del menú todo lo que no sea un botón, una sola vez, y se entrega a `deposit`. `finish(false)` lo devuelve todo. Un jugador que se desconecta lo recupera todo, sin entrega.
- `addToDeposit(item)` mete ítems por código (un botón de «rellenar»), solo en los huecos de depósito.
- El menú se redibuja en el tick siguiente a cada cambio.

`Menu.onPlayerInventoryClick(event)` recibe los clics en el inventario del jugador mientras el menú está abierto.

Claves de un ítem: `material, amount, name, lore, custom-model-data, glow, unbreakable, item-flags, skull-owner, item-model, slot, slots` (`slots` admite enteros y rangos `"10-16"`). `putGated` sigue la clave `denied-behavior` (`HIDE`, `DISABLE`, `SHOW_DENIED` con `<path>.denied`). `flags` equivale a `item-flags`; ambas aceptan guiones y el valor `ALL`. Los materiales aceptan guiones (`gray-stained-glass-pane`).

## 9. Entrada por chat

```java
chatInput.request(player)
        .prompt(messages.component("input.prompt"))
        .cancelWords("cancel", "cancelar")
        .timeout(Duration.ofSeconds(60))
        .closeInventory(true)
        .onInput(text -> rename(text))                 // hilo principal, con trim
        .onCancel(() -> menu.openLater())              // palabra de cancelación o timeout
        .start();
```

Se lee `originalMessage()` (otro plugin de chat puede haber reescrito `message()`). Si el jugador sale, la petición se descarta sin llamar a ningún callback. `prompt(uuid, consumer[, onCancel])` sigue disponible.

## 10. Ítems e inventario

```java
InventoryUtils.GiveResult result = InventoryUtils.give(player, reward.clone());   // devuelve lo que no cupo
result.leftovers().forEach(item -> pending.store(player, item));
InventoryUtils.giveOrDrop(player, items);                                         // suelta lo que no cupo, en pilas válidas
```

`ItemCodec` (sustituye a `ItemSerialization`, que queda deprecado):

```java
ItemCodec codec = ItemCodec.paper();
String data = codec.encode(contents, true);            // "v2z:" + Base64(GZIP(...))
String legacy = codec.encodeLegacy(contents);          // formato de Nexus 1.x, legible por versiones antiguas

ItemCodec.Decoded<ItemStack[]> decoded = codec.decodeArray(data, 27);
if (!decoded.intact()) {
    // no sobrescribir lo guardado con un inventario vacío
}
```

`ItemText.encode(item)` / `decode(texto)` guardan un solo ítem en una columna de texto (`v2z:`), y `decode` devuelve `null` si no se puede leer.

Lee `v2:`/`v2z:`, el stream de objetos Bukkit en Base64 (Nexus 1.x, GL-AuctionHouse), bytes crudos (`decodeBytes`, GL-Sync) y el YAML antiguo de GL-InventoryRollback. Un payload vacío es un inventario vacío intacto; uno ilegible tiene `intact() == false`.

## 11. Duraciones

```java
Optional<Duration> ttl = DurationParser.tryParse("1h30m");      // estricto: s m h d w, sin desbordamiento
DurationParser.tryParse("90", true);                             // número solo = segundos
DurationParser.parse("abc");                                     // permisivo (compatibilidad): ZERO

DurationFormat.compact(d);                    // 1d 2h 5s
DurationFormat.compact(d, names, ", ", ChronoUnit.HOURS);
DurationFormat.leading(d, " ");               // 1h 0m 5s
DurationFormat.largestUnit(d);                // 20m
DurationFormat.clock(d, true);                // 05:30 / 1:01:01
DurationFormat.clockPadded(d);                // 00:20:34
DurationFormat.ago(d);                        // 3d 2h ago
DurationFormat.pattern(d, "{hours}h {minutes}m");
```

## 12. Efectos y registros

```java
SoundSpec click = SoundSpec.parse("block.note_block.pling:1:1.2");   // también ENUM_STYLE y minecraft:...
SoundSpec ride = SoundSpec.fromSection(section, "sound", SoundSpec.NONE, problems, "elevators.up");
ride.play(player); ride.play(location);

ParticleSpec sparks = ParticleSpec.parse("FLAME:20:0.5:0.5:0.5:0.01");
ParticleSpec.fromSection(section, ParticleSpec.NONE, problems, "effects.teleport.particle").spawn(location);

Registries.sound("ENTITY_PLAYER_LEVELUP"); Registries.particle("flame"); Registries.effect("speed");
Registries.enchantment("sharpness"); Registries.material("minecraft:stone"); Registries.attribute("generic.max_health");
```

Colores y fuegos artificiales:

```java
Colors.parse("#33ccff"); Colors.parse("aqua");  // Optional<Color>: #rrggbb o un nombre de MiniMessage
Colors.parse("fuchsia", Colors.BUKKIT);          // con los nombres de org.bukkit.Color
Colors.parse(raw, miPaleta);                    // paleta propia: nombre en minúsculas → Color
Colors.format(color);                           // "#33CCFF"

FireworkSpec defaults = new FireworkSpec(FireworkEffect.Type.BALL_LARGE, List.of(Color.AQUA), List.of(), true, true, 1);
FireworkSpec spec = FireworkSpec.read(config.getConfigurationSection("firework"), defaults, problems);
spec.launch(player.getLocation());
spec.launch(location, rocket -> rocket.setPersistent(false));   // antes de que aparezca
```

`FireworkSpec.read` lee `type`, `colors`, `fade-colors`, `flicker`, `trail` y `power`, y añade a `problems` lo que no entiende.

Los sonidos se guardan como clave, así que funcionan los sonidos de resource pack con su propio namespace. El tono se limita a 0,5-2 (lo que reproduce el cliente).

## 13. Jugadores, eventos y acciones

```java
Players.resolve(name, Players.bukkitLookup(executors.io()))           // online → caché → consulta asíncrona
        .thenAcceptAsync(ref -> ..., tasks.mainThread());
Players.isVanished(player); Players.canSee(sender, target); Players.visibleNames(sender);

if (!EventUtils.call(new HomeCreateEvent(player, home))) return;    // una excepción de un listener no veta

ActionRegistry actions = ActionRegistry.withDefaults(TextFormat.LEGACY)
        .register("firework", (player, value) -> launch(player, value));
actions.run(player, config.getStringList("rewards"), "%player%", player.getName());
```

Formato de las acciones: `[tipo] valor`; una línea sin tipo es un comando de consola. Tipos incluidos: `console`/`command`, `player`, `message`/`msg`, `broadcast`, `actionbar`, `title` (`título|subtítulo`) y `sound`.

`ActionRunner` ejecuta listas completas. Además de los tipos anteriores entiende `server <nombre>`, `perm-command <permisos> <comando>`, `wait <ticks>`, `random` y `block-until-done`, y comprobaciones que detienen la lista (con el mensaje tras `|`): `permission`, `chance`, `item`, `take`, `if <a> <operador> <b>`, `limit <veces> <periodo> [nombre]` y `cost <cantidad>`. Las comprobaciones seguidas forman un bloque: se comprueban todas antes de cobrar, quitar ítems o contar usos.

```java
ActionRunner actions = new ActionRunner(plugin, executors.ordered("files"));   // usos de [limit] en data/limits.yml
actions.register("voucher", (player, scope, value) -> give(player, value));     // un tipo propio
actions.setZone(settings.zone());                                                // para day/week/month
actions.run(player, "npc:" + id, lines, "{npc}", id);                            // CompletableFuture, termina con los [wait]
actions.close();                                                                 // en el apagado
```

- `new ActionRunner(plugin, limites)` acepta cualquier `UsageLimits`; `YamlUsageLimits` es el que se usa por defecto.
- `accepts(linea)` valida una línea al cargarla, y `ActionRunner.blocks(lineas)` dice si lleva `[block-until-done]`.
- `Condition.parse("%player_level% >= 10")` (operadores `>=`, `<=`, `>`, `<`, `==`, `!=`, y `=` para texto), `Period.parse("day" | "week" | "month" | "once" | "12h")` y `Schedule.parse(lineas, problems)` (`mon-fri 18:00-22:00`, `22:00-02:00`, `sat,sun`, `day`, `night`) son públicas para usarlas fuera de las acciones.

`Cooldowns` lleva las esperas por jugador y clave:

```java
Cooldowns cooldowns = new Cooldowns();
if (!cooldowns.tryUse(uuid, "spray", Duration.ofSeconds(30))) { ... }   // empieza una si no había
long left = cooldowns.remainingMillis(uuid, "spray");
cooldowns.forget(uuid);                                                   // al salir

Cooldowns throttle = new Cooldowns(Duration.ofSeconds(2));               // todas de la misma duración
if (throttle.tryUse(uuid, "combat.blocked")) messages.send(player, "combat.blocked");
```

## 14. Integraciones opcionales: Vault, PlaceholderAPI y WorldGuard

```java
VaultEconomy economy = VaultEconomy.get();
if (economy.isAvailable() && economy.has(player, cost)) {
    EconomyResult result = economy.withdraw(player, cost);    // redondeo, NaN/negativos rechazados
}
economy.format(amount);
```

El proveedor se busca en cada llamada (un plugin de economía que carga tarde se detecta) y las clases de Vault solo se tocan si Vault está instalado. Hay que declarar `softdepend: [Vault]`.

```java
public class TeleportsExpansion extends NexusExpansion {
    public TeleportsExpansion(GTeleports plugin) {
        super(plugin, "glteleports");
        global("warps_total", () -> String.valueOf(plugin.getWarpManager().count()));
        player("homes_used", player -> String.valueOf(plugin.getHomeManager().count(player)));
        prefixed("top_", (player, rest) -> top(rest));
    }
}

PlaceholderHooks.registerIfPresent(() -> new TeleportsExpansion(plugin));
```

`PlaceholderHooks` no toca clases de PlaceholderAPI si no está instalado.

`WorldGuardHook` funciona igual: solo toca clases de WorldGuard si está activo, así que el plugin no necesita WorldGuard para compilar. Los flags se nombran como en los comandos de WorldGuard, también los que registra otro plugin.

```java
WorldGuardHook.isPresent();
WorldGuardHook.denied(location, "pvp");                         // true solo si WorldGuard lo deniega ahí
WorldGuardHook.state(player, location, "my-flag");              // Optional<Boolean>: allow, deny o vacío
WorldGuardHook.canBuild(player, location, "use");               // como el build de WorldGuard, con bypass
WorldGuardHook.regionsAt(location); WorldGuardHook.inAny(player, List.of("spawn", "world_nether:arena"));
```

Sin WorldGuard, `canBuild` devuelve `true`, `denied` e `inAny` devuelven `false` y `state` vacío. Hay que declarar `softdepend: [WorldGuard]`.

## 15. Proxy

```java
ProxyMessenger proxy = new ProxyMessenger(plugin);
proxy.register();
proxy.connect(player, "survival");
proxy.servers(Duration.ofSeconds(5)).thenAccept(names -> ...);
proxy.close();
```

Usa el canal `BungeeCord`, que aceptan BungeeCord y Velocity en modo compatible.

## 16. nexus-storage

```java
HikariDataSource pool = DataSources.create(
        DataSources.mysqlUrl(host, port, database, useSsl), user, password,
        PoolOptions.named("GL-Teleports-MySQL").maximumPoolSize(10));

SqlDialect dialect = SqlDialect.of(DatabaseType.parse(config.getString("type"), DatabaseType.YAML));
String upsert = dialect.upsert("homes", List.of("owner", "name"), List.of("owner", "name", "world"), List.of("world"));

Jdbc.update(pool, upsert, statement -> { ... });
List<Home> homes = Jdbc.query(pool, "SELECT * FROM homes WHERE owner = ?", st -> Jdbc.setUuid(st, 1, owner), this::map);
Jdbc.inTransaction(pool, connection -> { ...; return null; });

try (Connection c = pool.getConnection()) {
    Schema.addColumnIfMissing(c, "homes", "server", "VARCHAR(64) NULL");
    Schema.createIndexIfMissing(c, "idx_homes_server", "homes", "server");
    new Schema.VersionTable("schema_version", dialect).write(c, 2);
}

catch (SQLException e) {
    LoggerUtils.logError("MySQL could not start. " + ConnectionHints.describe(e, "mysql"));
}

MongoConnector.Handle mongo = MongoConnector.connect(uri, "gl_teleports", Duration.ofSeconds(5));
```

`DataSources.create(url, user, password, options, Map.of("journal_mode", "WAL"))` pasa además propiedades al driver.

Un pool de H2 abierto con `DB_CLOSE_DELAY=-1` se cierra con `DataSources.closeH2(pool)`: ejecuta `SHUTDOWN` antes de cerrar el pool. Sin eso, la base de datos sigue abierta hasta el gancho de salida de H2, que corre con el classloader del plugin ya cerrado y puede perder los últimos cambios.

`DatabaseSettings` lee el `database.yml` común de los plugins GL (`type`, `table-prefix`, `timeout`, `sqlite`, `h2`, `mysql`/`mariadb`/`postgresql`, `mongo`) y abre el almacén:

```java
private static final StorageDefaults DEFAULTS = StorageDefaults.of("GL-Bounties", "bounties");
// = new StorageDefaults("GL-Bounties", "gl_bounties", "bounties", "glbounties_")

DatabaseSettings settings = DatabaseSettings.read(config, DEFAULTS);
try {
    if (settings.type() == DatabaseType.MONGO) {
        repository = new MongoRepository(settings.connectMongo(), settings.tablePrefix());
    } else {
        repository = new SqlRepository(settings.openPool(dataFolder), settings.type(), settings.tablePrefix());
    }
} catch (SQLException | RuntimeException e) {
    throw settings.failure(e);          // mensaje con la pista de conexión del motor
}
```

`DatabaseSettings.warnIfUnsupported(type, LoggerUtils::logWarn)` es el aviso de `ConfigValidator` para un `type` desconocido.

Solo aporta infraestructura: tablas, columnas y consultas siguen en cada plugin.

## 17. nexus-network

Formato de cable: `TIPO:base64(json):firma`, con la firma HMAC-SHA256 de `TIPO:base64(json)` usando el secreto compartido. Es el formato que ya usaban los plugins, así que servidores con versiones distintas se entienden.

```java
NetworkSettings settings = NetworkSettings.fromSection(config.getConfigurationSection("multi-server"),
        KeyStyle.ANY, "gl-teleports");                 // resuelve ${VARIABLE}
// o, con respaldo del jar para cada clave que falte en el archivo:
NetworkSettings settings = NetworkSettings.fromConfig(configFile.resolved(), "multi-server", KeyStyle.KEBAB, "gl-teleports");

RedisConnector redis = new RedisConnector(settings, 16);
redis.connect("move players");    // exige contraseña y secreto de 32+ caracteres salvo allow-insecure

PacketCodec<TeleportPacket> codec = new PacketCodec<>(new Gson(), PacketType::classOf);
PacketDeduplicator dedup = new PacketDeduplicator();
PacketChannel channel = new PacketChannel(redis, settings.channel(), "GL-Teleports");

channel.subscribe(message -> {
    TeleportPacket packet = codec.decode(message, settings.secret());    // SecurityException si la firma no cuadra
    if (dedup.accept(packet)) handler.handle(packet);
});
channel.consumeStream(settings.serverName(), "gl-teleports", message -> ...);   // entrega dirigida

channel.publish(codec.encode(packet, settings.secret()));
channel.sendTo("survival", codec.encode(packet, settings.secret()));

NetworkSupervisor supervisor = new NetworkSupervisor(executors.scheduler(), "Multi-server teleports");
supervisor.scheduleRetry(this::tryConnect);   // 30 s → 5 min
```

- Los paquetes implementan `NetworkPacket` (`type()`, `destinationServer()`), o `TrackedPacket`/`AbstractPacket` si llevan `packetId` y `sentAt`. Los métodos no se serializan; solo los campos.
- `redis.connect("forge events", false)` no rechaza un secreto corto o vacío: solo avisa. Sirve a los plugins que ya permitían secretos cortos y no pueden apagar la sincronización a quien la tiene funcionando.
- `new RedisConnector(settings, maxTotal, maxIdle, minIdle, timeoutMs)` para plugins que dejan configurar el pool. `withJedis(...)` y `resource()` dan acceso a Jedis para lo que no cubre el conector, como pipelines; `resource()` se cierra con `try`-with-resources.
- `codec.decodeUnverified(message)` lee un paquete sin comprobar la firma, para redes que han renunciado a firmar con `allow-insecure`.
- `RedisConnector` incluye las operaciones compartidas: `get/set/setIfAbsent/getAndDelete/deleteIfValueIs/deleteIfOwnedBy/remainingTtls/addToSet/membersOf...`.

## 18. Geometría y otras utilidades

- `Cuboid`, `Sphere`, `Cylinder`, `Ellipsoid`: iterables de bloques, contención de ubicaciones y entidades, caras, expansión y contracción.
- `CC`: legacy (`&`, `&#rrggbb`), MiniMessage (acepta códigos legacy), entrada de usuario segura (`componentOfUserInput`), `legacy(Component)` y `plain(Component)`.
- `ItemBuilder`, `LocationUtils`, `TitleUtils`, `BossBarBuilder`, `EffectUtils`, `FormatUtils`.
- `Position`: una ubicación guardada por nombre de mundo, que existe aunque el mundo no esté cargado. `Position.of(location)`, `read(section)`/`write(section, ruta)`, `toLocation()` (`null` sin mundo), `add`, `withRotation`, `chunkX`/`chunkZ`.
- `Amounts.parse("1.5k")` (`k`, `m`, `b`; `OptionalDouble`, vacío si no es un número positivo y finito) y `Amounts.parseCount("64")`.
- `EntityVisibility`: muestra u oculta una entidad a cada jugador solo cuando cambia, para quien la pinta con `showEntity`/`hideEntity` o con paquetes.

## 19. Desarrollo de Nexus

```bash
./gradlew build                     # compila, tests y Javadoc de los tres módulos
./gradlew compileJava -PlintDocs    # lista miembros públicos/protegidos sin Javadoc
./gradlew publishToMavenLocal       # lo que ejecuta JitPack
```

Reglas: Javadoc en todo lo público y protegido (con `@param`, `@return` y `@throws`), comentarios en inglés solo para explicar el porqué, y nada de API eliminada sin pasar antes por `@Deprecated` con la alternativa en el Javadoc.
