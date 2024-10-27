package de.leidenheit.integration.extension;

import de.leidenheit.core.exception.ItarazzoIllegalStateException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ItarazzoExtensionTest {

    @Mock
    private ExtensionContext extensionContextMock;

    private ItarazzoExtension underTest;

    @BeforeEach
    void setUp() {
        underTest = new ItarazzoExtension();
    }

    @Test
    void shouldInitializeWithValidParameters() {
        // given
        System.setProperty("arazzo.file", "src/test/resources/test.arazzo.yaml");
        System.setProperty("arazzo-inputs.file", "hugo");

        // when & then
        Assertions.assertDoesNotThrow(() -> underTest.beforeAll(extensionContextMock));
    }

    @Test
    void shouldFailDueToMissingParameters() {
        // given is, that there are no system variables defined

        // when & then
        Assertions.assertThrowsExactly(ItarazzoIllegalStateException.class, () ->
                underTest.beforeAll(extensionContextMock));

    }

    @Test
    void shouldFailDueToInvalidParameters() {
        // given
        System.setProperty("arazzo.file", "not");
        System.setProperty("arazzo-inputs.file", "existing");

        // when & then
        Assertions.assertThrowsExactly(ItarazzoIllegalStateException.class, () ->
                underTest.beforeAll(extensionContextMock), "hi");

    }
}
