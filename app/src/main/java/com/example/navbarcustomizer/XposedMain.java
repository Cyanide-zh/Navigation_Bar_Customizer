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
        // 兼容处理：同时监听系统核心服务进程 ("android") 和系统界面进程 ("com.android.systemui")
        if (!"com.android.systemui".equals(lpparam.packageName) && !"android".equals(lpparam.packageName)) {
            return;
        }

        loadConfig();

        // 1. 如果在 SystemUI 进程，控制三大金刚键暗色
        if ("com.android.systemui".equals(lpparam.packageName)) {
            XposedBridge.log("NavbarCustomizer: Loaded into SystemUI");
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
        }

        // 2. 在“android” (system_server) 和 “com.android.systemui” 进程中同时拦截资源尺寸请求
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
