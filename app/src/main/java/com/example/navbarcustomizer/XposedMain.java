package com.example.navbarcustomizer;

import android.content.res.Resources;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

public class XposedMain implements IXposedHookLoadPackage {

    private static final String CONFIG_PATH = "/data/system/navbar_customizer_config.txt";

    private boolean alwaysDark = true;
    private float customNavBarHeightDp = -1f;
    private float customNavBarHeightLandscapeDp = -1f;
    private float customNavBarWidthDp = -1f;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!"com.android.systemui".equals(lpparam.packageName)) {
            return;
        }

        XposedBridge.log("NavbarCustomizer: Loaded into SystemUI (Full Dimension Override Mode)");

        loadConfig();

        // 1. 保持三大金刚键暗色
        if (alwaysDark) {
            try {
                Class<?> taskbarDelegateClass = XposedHelpers.findClassIfExists(
                        "com.android.systemui.navigationbar.TaskbarDelegate$3", 
                        lpparam.classLoader
                );
                if (taskbarDelegateClass != null) {
                    XposedHelpers.findAndHookMethod(taskbarDelegateClass, "applyDarkIntensity", float.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            param.args[0] = 1.0f;
                        }
                    });
                }
            } catch (Throwable ignored) {}
        }

        // 2. 在 SystemUI 内部拦截 Resources.getDimensionPixelSize，支持竖屏高、横屏高、宽度
        try {
            XposedHelpers.findAndHookMethod(Resources.class, "getDimensionPixelSize", int.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    int id = (int) param.args[0];
                    try {
                        Resources res = (Resources) param.thisObject;
                        String entryName = res.getResourceEntryName(id);
                        if (entryName != null) {
                            float density = res.getDisplayMetrics().density;

                            if (entryName.equals("navigation_bar_height") && customNavBarHeightDp != -1f) {
                                int targetPx = (int) (customNavBarHeightDp * density + 0.5f);
                                param.setResult(targetPx);
                            } else if (entryName.equals("navigation_bar_height_landscape") && customNavBarHeightLandscapeDp != -1f) {
                                int targetPx = (int) (customNavBarHeightLandscapeDp * density + 0.5f);
                                param.setResult(targetPx);
                            } else if (entryName.equals("navigation_bar_width") && customNavBarWidthDp != -1f) {
                                int targetPx = (int) (customNavBarWidthDp * density + 0.5f);
                                param.setResult(targetPx);
                            }
                        }
                    } catch (Exception ignored) {}
                }
            });
            XposedBridge.log("NavbarCustomizer: Successfully hooked Resources.getDimensionPixelSize for all nav dimensions");
        } catch (Throwable e) {
            XposedBridge.log("NavbarCustomizer: Failed to hook Resources -> " + e.getMessage());
        }
    }

    private void loadConfig() {
        File file = new File(CONFIG_PATH);
        if (!file.exists()) return;

        try (FileInputStream fis = new FileInputStream(file)) {
            Properties props = new Properties();
            props.load(fis);
            
            alwaysDark = Boolean.parseBoolean(props.getProperty("always_dark_intensity", "true"));
            
            String h = props.getProperty("navigation_bar_height");
            if (h != null) customNavBarHeightDp = Float.parseFloat(h.trim());

            String hl = props.getProperty("navigation_bar_height_landscape");
            if (hl != null) customNavBarHeightLandscapeDp = Float.parseFloat(hl.trim());

            String w = props.getProperty("navigation_bar_width");
            if (w != null) customNavBarWidthDp = Float.parseFloat(w.trim());

        } catch (Exception ignored) {}
    }
}
