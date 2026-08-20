package io.github.xingguanglang.casinotables;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 一句「为什么」——存的是语言文件里的键和占位符，什么时候要看文字，什么时候才翻译。
 *
 * <p>历史记录以前存的是已经渲染好的句子。服主中途把 language 从 en_US 改成 zh_CN 之后，
 * 老记录永远停在英文，新记录是中文，一张列表半英半中，而且重启也治不好——那是写进
 * poker-history.yml 的死文本。存键和参数就没有这个问题：语言换了，整个历史跟着换。
 *
 * <p>广播、日志这些当场就要文字的地方调 {@link #render()} 即可，行为和以前完全一样。
 */
public record Reason(String key, List<String> args) {
    public Reason {
        args = List.copyOf(args);
    }

    /**
     * @param key          语言文件里的键，例如 {@code poker.reason.showdown}
     * @param placeholders 交替给出的占位符名与值，和 {@link Messages#msg} 一致
     */
    public static Reason of(String key, Object... placeholders) {
        List<String> args = new ArrayList<>(placeholders.length);
        for (Object placeholder : placeholders) args.add(String.valueOf(placeholder));
        return new Reason(key, args);
    }

    /** 按当前语言渲染。 */
    public String render() {
        return Messages.msg(key, args.toArray());
    }

    @Override
    public String toString() {
        return render();
    }

    /** 落盘用的形状；和 YAML 的标量/列表对得上。 */
    public Map<String, Object> toStored() {
        return Map.of("key", key, "args", new ArrayList<>(args));
    }

    /**
     * 从历史条目里取回一句理由。
     *
     * @param stored 新格式，{@code {key: ..., args: [...]}}，没有就传 null
     * @param legacy 旧格式里那句已经渲染死的文本，用于兼容升级前写下的记录
     */
    public static String render(Object stored, String legacy) {
        if (stored instanceof Map<?, ?> map && map.get("key") instanceof String key) {
            List<String> args = new ArrayList<>();
            if (map.get("args") instanceof List<?> list) {
                for (Object item : list) args.add(String.valueOf(item));
            }
            return Messages.msg(key, args.toArray());
        }
        return legacy == null ? "" : legacy;
    }
}
