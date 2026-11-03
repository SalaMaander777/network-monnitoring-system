package ru.netmon.emulators.initprobe;

/** Тестовый вид: статический инициализатор бросает исключение, ожидается код 1. */
public final class Main {

    static {
        if (System.nanoTime() != 0) {
            throw new IllegalStateException("init boom");
        }
    }

    private Main() {
    }

    public static int run(String[] args) {
        return 0;
    }
}
