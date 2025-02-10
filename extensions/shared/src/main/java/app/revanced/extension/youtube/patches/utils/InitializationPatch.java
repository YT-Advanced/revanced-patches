package app.revanced.extension.youtube.patches.utils;

import static app.revanced.extension.shared.settings.preference.AbstractPreferenceFragment.showRestartDialog;
import static app.revanced.extension.shared.utils.StringRef.str;
import static app.revanced.extension.shared.utils.Utils.runOnBackgroundThread;
import static app.revanced.extension.shared.utils.Utils.runOnMainThreadDelayed;

import android.app.Activity;

import androidx.annotation.NonNull;

import app.revanced.extension.shared.patches.spoof.potoken.PoTokenGenerator;
import app.revanced.extension.shared.settings.BaseSettings;
import app.revanced.extension.shared.settings.BooleanSetting;
import app.revanced.extension.shared.utils.Logger;
import app.revanced.extension.youtube.utils.ExtendedUtils;

@SuppressWarnings("unused")
public class InitializationPatch {
    private static final BooleanSetting SETTINGS_INITIALIZED = BaseSettings.SETTINGS_INITIALIZED;

    public static void initializePoTokenWebView(@NonNull Activity mActivity) {
        if (!BaseSettings.SPOOF_STREAMING_DATA.get()) return;

        runOnBackgroundThread(() -> {
            try {
                new PoTokenGenerator().getPoTokenResult(mActivity);
            } catch (Exception ex) {
                Logger.printException(() -> "Failed to initialize PoToken WebView", ex);
            }
        });
    }

    /**
     * Some layouts that depend on litho do not load when the app is first installed.
     * (Also reproduced on unPatched YouTube)
     * <p>
     * To fix this, show the restart dialog when the app is installed for the first time.
     */
    public static void onCreate(@NonNull Activity mActivity) {
        if (SETTINGS_INITIALIZED.get()) {
            return;
        }
        runOnMainThreadDelayed(() -> showRestartDialog(mActivity, str("revanced_extended_restart_first_run"), 3500), 500);
        runOnMainThreadDelayed(() -> SETTINGS_INITIALIZED.save(true), 1000);
    }

    public static void setExtendedUtils(@NonNull Activity mActivity) {
        ExtendedUtils.setPlayerFlyoutMenuAdditionalSettings();
    }
}