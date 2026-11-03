package ru.netmon.emulators;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EmulatorMainTest {

    @Test
    void emptyArgumentsGiveCode2() {
        assertThat(EmulatorMain.run(new String[0])).isEqualTo(2);
    }

    @Test
    void unknownKindGivesCode2() {
        assertThat(EmulatorMain.run(new String[] {"nosuchkind"})).isEqualTo(2);
    }

    @Test
    void dottedKindIsRejectedWithoutLoadingClass() {
        assertThat(EmulatorMain.run(new String[] {"a.b"})).isEqualTo(2);
    }

    @Test
    void uppercaseAndPathLikeKindsAreRejected() {
        assertThat(EmulatorMain.run(new String[] {"Tcp"})).isEqualTo(2);
        assertThat(EmulatorMain.run(new String[] {"../x"})).isEqualTo(2);
        assertThat(EmulatorMain.run(new String[] {""})).isEqualTo(2);
    }

    @Test
    void probeGetsArgumentsWithoutFirstAndItsCodeIsReturned() {
        assertThat(EmulatorMain.run(new String[] {"probe", "7", "--a=b"})).isEqualTo(7);
        assertThat(ru.netmon.emulators.probe.Main.lastArgs).containsExactly("7", "--a=b");
    }

    @Test
    void voidReturningKindGivesCode2() {
        assertThat(EmulatorMain.run(new String[] {"voidprobe"})).isEqualTo(2);
    }

    @Test
    void exceptionInsideKindGivesCode1() {
        assertThat(EmulatorMain.run(new String[] {"probe", "boom"})).isEqualTo(1);
    }

    @Test
    void nonPublicMainGivesCode2() {
        assertThat(EmulatorMain.run(new String[] {"hiddenprobe"})).isEqualTo(2);
    }

    @Test
    void throwingStaticInitializerGivesCode1() {
        assertThat(EmulatorMain.run(new String[] {"initprobe"})).isEqualTo(1);
    }
}
