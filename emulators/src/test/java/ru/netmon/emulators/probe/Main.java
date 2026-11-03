package ru.netmon.emulators.probe;

import java.util.List;

/** Тестовый вид: запоминает аргументы, возвращает код из первого аргумента (по умолчанию 0). */
public final class Main {

    public static volatile List<String> lastArgs;

    private Main() {
    }

    public static int run(String[] args) {
        lastArgs = List.of(args);
        if (args.length > 0 && args[0].equals("boom")) {
            throw new IllegalStateException("boom");
        }
        return args.length > 0 ? Integer.parseInt(args[0]) : 0;
    }
}
