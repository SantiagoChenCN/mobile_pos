package com.espsa.mobilepos.ui.sync;

import com.espsa.mobilepos.app.sync.ComputerSyncException;
import com.espsa.mobilepos.app.sync.ComputerSyncFailureReason;
import com.espsa.mobilepos.ui.AppLanguage;

public final class ComputerSyncErrorPresenterSmokeTest {
    public static void main(String[] args) {
        assertPresentation(ComputerSyncFailureReason.CONNECTION_TIMEOUT, "同一 Wi-Fi", "misma Wi-Fi");
        assertPresentation(ComputerSyncFailureReason.CONNECTION_REFUSED, "HTTP 服务", "servicio HTTP");
        assertPresentation(ComputerSyncFailureReason.INVALID_TOKEN, "Token", "Token");
        assertPresentation(ComputerSyncFailureReason.UNKNOWN_HOST, "局域网 IPv4", "IPv4 local");
        assertPresentation(ComputerSyncFailureReason.INVALID_RESPONSE, "MobilePosSync", "MobilePosSync");
        assertPresentation(ComputerSyncFailureReason.CLEAR_TEXT_BLOCKED, "最新 APK", "APK mas reciente");
        assertPresentation(ComputerSyncFailureReason.INVALID_CONFIG, "IP、端口或 Token", "IP, el puerto o el Token");

        System.out.println("Computer sync error presenter smoke test passed");
    }

    private static void assertPresentation(
            ComputerSyncFailureReason reason,
            String expectedZhSuggestion,
            String expectedEsSuggestion
    ) {
        ComputerSyncException exception = new ComputerSyncException(reason, "internal error");
        ComputerSyncErrorPresentation zh = ComputerSyncErrorPresenter.presentError(exception, AppLanguage.ZH);
        ComputerSyncErrorPresentation es = ComputerSyncErrorPresenter.presentError(exception, AppLanguage.ES);
        assertTrue(reason + " is preserved", zh.failureReason() == reason);
        assertTrue(reason + " Chinese presentation", presentationText(zh).contains(expectedZhSuggestion));
        assertTrue(reason + " Spanish presentation", presentationText(es).contains(expectedEsSuggestion));
    }

    private static void assertTrue(String label, boolean condition) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }

    private static String presentationText(ComputerSyncErrorPresentation presentation) {
        return presentation.title() + "\n" + presentation.message() + "\n" + presentation.suggestion();
    }
}
