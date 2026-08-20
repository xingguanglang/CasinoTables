package io.github.xingguanglang.casinotables;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 所有面向玩家的文本都从语言文件读取，代码里不再写死任何一句话。
 *
 * <p>加载顺序：先把 jar 内对应语言的文件当作兜底，再叠加 plugin 数据目录下同名文件里管理员改过的条目。
 * 这样升级插件时新增的消息会自动出现，而管理员改过的措辞不会被覆盖。
 *
 * <p>取不到的 key 会原样返回 {@code <missing: key>} 而不是抛异常——少一句翻译不该让一局牌崩掉。
 */
public final class Messages {
    /**
     * 静态门面。文本取用点有一千多处，散布在没有 plugin 引用的类里，
     * 逐个穿参数得不偿失；这里在 onEnable 时绑定一次，重载时替换。
     */
    private static Messages active;

    static void bind(Messages instance) { active = instance; }

    /** 取一条消息；插件未启用时原样返回 key，不抛异常。 */
    public static String msg(String key, Object... placeholders) {
        return active == null ? key : active.get(key, placeholders);
    }

    private static final String DEFAULT_LANGUAGE = "en_US";
    /** jar 里内置的语言，缺失的 key 一律回落到这一份。 */
    private static final String FALLBACK_LANGUAGE = "en_US";
    /** jar 内自带的语言，启动时全部释放到数据目录供服主参考和修改。 */
    private static final List<String> BUNDLED_LANGUAGES = List.of("en_US", "zh_CN");

    /** 构建期自检用 bindStandalone() 时为 null，那时候没有服务器可以写日志。 */
    private final CasinoTablesPlugin plugin;
    private final Map<String, String> values = new HashMap<>();
    private final Map<String, String> fallback = new HashMap<>();
    private final Map<String, List<String>> lists = new HashMap<>();
    private final Map<String, List<String>> fallbackLists = new HashMap<>();
    /** 已经警告过的缺失键；同一个键只提醒一次，避免每 tick 刷屏。 */
    private final java.util.Set<String> warned = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private String language = DEFAULT_LANGUAGE;

    public Messages(CasinoTablesPlugin plugin) {
        this.plugin = plugin;
        reload();
        bind(this);
    }

    /** 没有插件实例时（构建期自检）退到标准错误，避免为了一句警告抛 NPE。 */
    private void warn(String message) {
        if (plugin != null) plugin.getLogger().warning(message);
        else System.err.println("[Messages] " + message);
    }

    private Messages() {
        this.plugin = null;
    }

    /**
     * 供构建期自检使用：不启动服务器，直接把一份语言文件绑定为全局文本来源。
     *
     * <p>这样自检跑的是真实的取文本路径——键写错、语言文件漏条目都会当场暴露，
     * 而不是等玩家在服务器上看见 {@code <missing: ...>}。
     */
    public static Messages bindStandalone(YamlConfiguration yaml) {
        Messages instance = new Messages();
        instance.loadInto(instance.fallback, instance.fallbackLists, yaml);
        instance.values.putAll(instance.fallback);
        instance.lists.putAll(instance.fallbackLists);
        bind(instance);
        return instance;
    }

    /** 供自检使用：本次运行中真正取不到的键，空集合才算通过。 */
    public static java.util.Set<String> missingKeys() {
        return active == null ? java.util.Set.of() : java.util.Set.copyOf(active.warned);
    }

    public void reload() {
        values.clear();
        fallback.clear();
        lists.clear();
        fallbackLists.clear();
        warned.clear();
        language = plugin.getConfig().getString("language", DEFAULT_LANGUAGE);
        if (language == null || language.isBlank()) language = DEFAULT_LANGUAGE;

        loadInto(fallback, fallbackLists, bundled(FALLBACK_LANGUAGE));
        if (!FALLBACK_LANGUAGE.equals(language)) loadInto(values, lists, bundled(language));
        else {
            values.putAll(fallback);
            lists.putAll(fallbackLists);
        }

        // 管理员改过的条目优先级最高。
        File external = new File(new File(plugin.getDataFolder(), "lang"), language + ".yml");
        if (external.isFile()) {
            loadInto(values, lists, YamlConfiguration.loadConfiguration(external));
        }
        // 把 jar 里所有语言都释放出来，不只是当前这一份：
        // 服主想照着另一种语言改措辞，不该被迫先解压 jar。
        for (String code : BUNDLED_LANGUAGES) saveBundled(code);
    }

    private YamlConfiguration bundled(String code) {
        InputStream stream = plugin.getResource("lang/" + code + ".yml");
        if (stream == null) return new YamlConfiguration();
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (IOException exception) {
            warn("Failed to read bundled language " + code + ": " + exception.getMessage());
            return new YamlConfiguration();
        }
    }

    /** 把 jar 内的语言文件释放到数据目录，方便管理员直接改措辞。 */
    private void saveBundled(String code) {
        File target = new File(new File(plugin.getDataFolder(), "lang"), code + ".yml");
        if (target.isFile()) return;
        if (plugin.getResource("lang/" + code + ".yml") == null) return;
        plugin.saveResource("lang/" + code + ".yml", false);
    }

    private void loadInto(Map<String, String> target, Map<String, List<String>> listTarget,
                          YamlConfiguration yaml) {
        for (String key : yaml.getKeys(true)) {
            if (yaml.isConfigurationSection(key)) continue;
            Object raw = yaml.get(key);
            if (raw == null) continue;
            if (raw instanceof List<?> list) {
                List<String> lines = new ArrayList<>(list.size());
                for (Object item : list) lines.add(String.valueOf(item));
                listTarget.put(key, lines);
            } else {
                target.put(key, String.valueOf(raw));
            }
        }
    }

    /** 取一组消息行，用于帮助这类多行文本；服主可以在语言文件里自由增删。 */
    public List<String> getList(String key, Object... placeholders) {
        List<String> template = lists.get(key);
        if (template == null) template = fallbackLists.get(key);
        if (template == null) return List.of();
        if (placeholders.length == 0) return List.copyOf(template);
        List<String> result = new ArrayList<>(template.size());
        for (String line : template) {
            String rendered = line;
            for (int i = 0; i + 1 < placeholders.length; i += 2) {
                rendered = rendered.replace("{" + placeholders[i] + "}", String.valueOf(placeholders[i + 1]));
            }
            result.add(rendered);
        }
        return result;
    }

    public static List<String> msgList(String key, Object... placeholders) {
        return active == null ? List.of() : active.getList(key, placeholders);
    }

    public String language() { return language; }

    /**
     * 取一条消息并替换占位符。
     *
     * @param key          语言文件里的键，例如 {@code poker.hand.start}
     * @param placeholders 交替给出的占位符名与值，例如 {@code "player", name, "amount", 20}
     */
    public String get(String key, Object... placeholders) {
        String template = values.get(key);
        if (template == null) template = fallback.get(key);
        if (template == null) {
            // 玩家看到的东西不该是调试文本，但服主必须知道翻译漏了，所以按键提醒一次。
            if (warned.add(key)) {
                warn("Missing message key \"" + key + "\" in language "
                        + language + " and in the bundled " + FALLBACK_LANGUAGE + " fallback.");
            }
            return "<missing: " + key + ">";
        }
        if (placeholders.length == 0) return template;
        if (placeholders.length % 2 != 0) {
            warn("Odd placeholder count for message key " + key);
            return template;
        }
        String result = template;
        for (int index = 0; index + 1 < placeholders.length; index += 2) {
            result = result.replace("{" + placeholders[index] + "}", String.valueOf(placeholders[index + 1]));
        }
        return result;
    }

    /** 该 key 是否存在，供需要「有就显示、没有就省略」的地方使用。 */
    public boolean has(String key) {
        return values.containsKey(key) || fallback.containsKey(key);
    }
}
