package ru.netmon.emulators;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * Точка входа эмуляторов. Первый аргумент это вид эмулятора (только [a-z]+);
 * класс ru.netmon.emulators.<вид>.Main ищется через reflection (docs/architecture.md §12.5).
 */
public final class EmulatorMain {

    private static final Pattern KIND = Pattern.compile("[a-z]+");

    private EmulatorMain() {
    }

    public static void main(String[] args) {
        System.exit(run(args));
    }

    /** Возвращает код выхода: 2 при ошибке запуска, иначе код вида-эмулятора. */
    public static int run(String[] args) {
        if (args == null || args.length == 0) {
            System.err.println("Не указан вид эмулятора. Использование: <вид> [--ключ=значение]");
            return 2;
        }
        String kind = args[0];
        if (kind == null || !KIND.matcher(kind).matches()) {
            System.err.println("Недопустимый вид эмулятора (разрешены только буквы a-z)");
            return 2;
        }
        Class<?> type;
        try {
            type = Class.forName("ru.netmon.emulators." + kind + ".Main", false,
                    EmulatorMain.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            System.err.println("Неизвестный вид эмулятора: " + kind);
            return 2;
        } catch (LinkageError e) {
            System.err.println("Не удалось загрузить вид " + kind + ": " + e);
            return 2;
        }
        if (!Modifier.isPublic(type.getModifiers())) {
            System.err.println("Класс Main вида " + kind + " должен быть public");
            return 2;
        }
        Method run;
        try {
            run = type.getMethod("run", String[].class);
        } catch (NoSuchMethodException e) {
            System.err.println("У вида " + kind + " нет метода run(String[])");
            return 2;
        }
        if (run.getReturnType() != int.class || !Modifier.isStatic(run.getModifiers())) {
            System.err.println("Метод run вида " + kind + " должен быть static и возвращать int");
            return 2;
        }
        try {
            return (Integer) run.invoke(null, (Object) Arrays.copyOfRange(args, 1, args.length));
        } catch (InvocationTargetException e) {
            System.err.println("Ошибка внутри вида " + kind + ": " + e.getCause());
            return 1;
        } catch (IllegalAccessException e) {
            System.err.println("Нет доступа к run вида " + kind + ": " + e);
            return 2;
        } catch (ExceptionInInitializerError e) {
            System.err.println("Ошибка инициализации вида " + kind + ": " + e.getCause());
            return 1;
        } catch (LinkageError e) {
            System.err.println("Не удалось связать вид " + kind + ": " + e);
            return 2;
        }
    }
}
