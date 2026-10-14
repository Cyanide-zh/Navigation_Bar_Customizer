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

// 同时实现 IXposedHookZygoteInit（用于系统级资源替换）和 IXposedHookLoadPackage（用于 Hook SystemUI 行为）
public class XposedMain implements IXposedHookZygoteInit, IXposedHookLoadPackage {

    private static final String CONFIG_PATH = "/data/system/navbar_customizer_config.txt";

    private boolean alwaysDark = true;
    private float customNavBarHeightDp = -1f;
    private float customNavBarHeightLandscapeDp = -1f;
    private float customNavBarWidthDp = -1f;

    @Override
    public void initZygote(StartupParam startupParam) throws Throwable {
        // 在系统 Zygote 启动初始化时加载配置并替换系统资源
        loadConfig();

        try {
            // 获取当前系统的屏幕密度，用于将配置的 dp 精确转换为像素 px
            float density = android.content.res.Resources.getSystem().getDisplayMetrics().density;

            if (customNavBarHeightDp != -1f) {
                int px = (int) (customNavBarHeightDp * density + 0.5f);
                // 动态替换系统框架层资源（等同于你修改 framework-res.apk）
                XResources.setSystemWideReplacement("android", "dimen", "navigation_bar_height", px);
                XposedBridge.log("NavbarCustomizer: Replaced navigation_bar_height -> " + px + "px (" + customNavBarHeightDp + "dp)");
            }

            if (customNavBarHeightLandscapeDp != -1f) {
                int px = (int) (customNavBarHeightLandscapeDp * density + 0.5f);
                XResources.setSystemWideReplacement("android", "dimen", "navigation_bar_height_landscape", px);
                XposedBridge.log("NavbarCustomizer: Replaced navigation_bar_height_landscape -> " + px + "px");
            }

            if (customNavBarWidthDp != -1f) {
                int px = (int) (customNavBarWidthDp * density + 0.5f);
                XResources.setSystemWideReplacement("android", "dimen", "navigation_bar_width", px);
                XposedBridge.log("NavbarCustomizer: Replaced navigation_bar_width -> " + px + "px");
            }
        } catch (Throwable e) {
            XposedBridge.log("NavbarCustomizer: Failed to set system wide replacement -> " + e.getMessage());
        }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!"com.android.systemui".equals(lpparam.packageName)) {
            return;
        }

        XposedBridge.log("NavbarCustomizer: Loaded into SystemUI");

        // 保持三大金刚键暗色控制
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
                XposedBridge.log("NavbarCustomizer: Failed to hook -> " + e.getMessage());
            }
        }
    }

    private void loadConfig() {
        File file = new File(CONFIG_PATH);
        if (!file.exists()) {
            XposedBridge.log("NavbarCustomizer: Config file not found, using defaults.");
            return;
        }

        try (FileInputStream fis = new FileInputStream(file)) {
            Properties props = new Properties();
            props.load(fis);

            alwaysDark = Boolean.parseBoolean(props.getProperty("always_dark_intensity", "true"));
            
            String heightStr = props.getProperty("navigation_bar_height");
            if (heightStr != null) customNavBarHeightDp = Float.parseFloat(heightStr.trim());

            String heightLandStr = props.getProperty("navigation_bar_height_landscape");
            if (heightLandStr != null) customNavBarHeightLandscapeDp = Float.parseFloat(heightLandStr.trim());

            String widthStr = props.getProperty("navigation_bar_width");
            if (widthStr != null) customNavBarWidthDp = Float.parseFloat(widthStr.trim());

            XposedBridge.log("NavbarCustomizer: Config loaded successfully in Zygote.");
        } catch (Exception e) {
            XposedBridge.log("NavbarCustomizer: Failed to load config -> " + e.getMessage());
        }
    }
}
