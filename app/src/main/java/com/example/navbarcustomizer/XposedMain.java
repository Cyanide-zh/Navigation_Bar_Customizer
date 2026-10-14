package com.example.navbarcustomizer;

import android.content.res.XResources;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

public class XposedMain implements IXposedHookZygoteInit, IXposedHookLoadPackage {

    // 调整为更通用的临时或公共可读路径，避免部分 Android 版本沙盒权限拦截
    private static final String CONFIG_PATH = "/data/system/navbar_customizer_config.txt";

    private boolean alwaysDark = true;
    private float customNavBarHeightDp = 30f;       // 默认给出一个测试dp值
    private float customNavBarHeightLandscapeDp = 30f;
    private float customNavBarWidthDp = 30f;

    @Override
    public void initZygote(StartupParam startupParam) throws Throwable {
        loadConfig();

        try {
            // 获取系统全局屏幕密度
            float density = android.content.res.Resources.getSystem().getDisplayMetrics().density;

            int heightPx = (int) (customNavBarHeightDp * density + 0.5f);
            int heightLandPx = (int) (customNavBarHeightLandscapeDp * density + 0.5f);
            int widthPx = (int) (customNavBarWidthDp * density + 0.5f);

            // 全局替换系统资源（等同于修改 framework-res.apk）
            XResources.setSystemWideReplacement("android", "dimen", "navigation_bar_height", heightPx);
            XResources.setSystemWideReplacement("android", "dimen", "navigation_bar_height_landscape", heightLandPx);
            XResources.setSystemWideReplacement("android", "dimen", "navigation_bar_width", widthPx);

            XposedBridge.log("NavbarCustomizer: Zygote replaced dimensions -> H:" + heightPx + "px, LandH:" + heightLandPx + "px, W:" + widthPx + "px");
        } catch (Throwable e) {
            XposedBridge.log("NavbarCustomizer: Zygote replacement failed -> " + e.getMessage());
        }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!"com.android.systemui".equals(lpparam.packageName)) {
            return;
        }

        XposedBridge.log("NavbarCustomizer: Loaded into SystemUI");

        // 三大金刚键暗色控制
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
                    XposedBridge.log("NavbarCustomizer: Successfully hooked applyDarkIntensity");
                }
            } catch (Throwable e) {
                XposedBridge.log("NavbarCustomizer: Failed to hook applyDarkIntensity -> " + e.getMessage());
            }
        }
    }

    private void loadConfig() {
        File file = new File(CONFIG_PATH);
        if (!file.exists()) {
            XposedBridge.log("NavbarCustomizer: Config not found, using default 30dp.");
            return;
        }

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

            XposedBridge.log("NavbarCustomizer: Config loaded in Zygote successfully.");
        } catch (Exception e) {
            XposedBridge.log("NavbarCustomizer: Failed to load config -> " + e.getMessage());
        }
    }
}
