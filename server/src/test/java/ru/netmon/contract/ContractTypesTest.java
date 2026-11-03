package ru.netmon.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.slf4j.helpers.MessageFormatter;

/** T1.03: порядок статусов и нераскрытие секретов в toString, equals и логировании. */
class ContractTypesTest {

    private static final String COMMUNITY = "comm-s3cr3t-Q1";
    private static final String[] V3_VALUES = {"usr-s3cr3t-Q2", "SHA-s3cr3t-Q3", "auth-s3cr3t-Q4", "AES-s3cr3t-Q5", "priv-s3cr3t-Q6"};

    @Test
    void deviceStatusOrderWorstFirst() {
        assertThat(DeviceStatus.values()).containsExactly(
                DeviceStatus.CRITICAL, DeviceStatus.WARNING, DeviceStatus.NO_DATA, DeviceStatus.OK);
    }

    @Test
    void v2cToStringHidesValue() {
        SnmpCredentials c = new SnmpCredentials.V2c(COMMUNITY);
        assertThat(c.toString()).contains("***").doesNotContain(COMMUNITY);
        assertThat(String.valueOf(c)).doesNotContain(COMMUNITY);
        assertThat("creds=" + c).doesNotContain(COMMUNITY);
        assertThat(MessageFormatter.format("creds {}", c).getMessage()).doesNotContain(COMMUNITY);
        assertThat(String.valueOf(Map.of("k", c))).doesNotContain(COMMUNITY);
    }

    @Test
    void v3ToStringHidesEveryValue() {
        SnmpCredentials c = new SnmpCredentials.V3(V3_VALUES[0], V3_VALUES[1], V3_VALUES[2], V3_VALUES[3], V3_VALUES[4]);
        for (String text : new String[] {c.toString(), String.valueOf(c), "creds=" + c,
                MessageFormatter.format("creds {}", c).getMessage(), String.valueOf(Map.of("k", c))}) {
            assertThat(text).contains("***");
            for (String v : V3_VALUES) {
                assertThat(text).doesNotContain(v);
            }
        }
    }

    @Test
    void equalsWorksAndDoesNotLeak() {
        SnmpCredentials a = new SnmpCredentials.V2c(COMMUNITY);
        assertThat(a).isEqualTo(new SnmpCredentials.V2c(COMMUNITY)).isNotEqualTo(new SnmpCredentials.V2c("other"));
        // сообщение об ошибке сравнения строится через toString и не должно содержать значение
        Throwable t = org.assertj.core.api.Assertions.catchThrowable(
                () -> assertThat(a).isEqualTo(new SnmpCredentials.V2c("other")));
        assertThat(t).isNotNull();
        assertThat(String.valueOf(t.getMessage())).doesNotContain(COMMUNITY);
        SnmpCredentials v3 = new SnmpCredentials.V3(V3_VALUES[0], V3_VALUES[1], V3_VALUES[2], V3_VALUES[3], V3_VALUES[4]);
        assertThat(v3).isEqualTo(new SnmpCredentials.V3(V3_VALUES[0], V3_VALUES[1], V3_VALUES[2], V3_VALUES[3], V3_VALUES[4]));
    }
}
