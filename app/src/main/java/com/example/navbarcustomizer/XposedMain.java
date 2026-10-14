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

    // 配置文件路径
    private static final String CONFIG_PATH = "/data/system/navbar_customizer_config.txt";

    private boolean alwaysDark = true;
    
    // 显式声明全局变量，供各个方法调用
    private float customNavBarHeightDp = -1f;
    private float customNavBarHeightLandscapeDp = -1f;
    private float customNavBarWidthDp = -1f;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!"com.android.systemui".equals(lpparam.packageName)) {
            return;
        }

        XposedBridge.log("NavbarCustomizer: Loaded into SystemUI");

        // 每次进程加载时读取配置文件
        loadConfig();

        // 1. Hook 导航栏暗色强度 (applyDarkIntensity)
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
                            // 强制将暗色强度设为 1.0f
                            param.args[0] = 1.0f;
                        }
                    });
                    XposedBridge.log("NavbarCustomizer: Successfully hooked applyDarkIntensity");
                }
            } catch (Throwable e) {
                XposedBridge.log("NavbarCustomizer: Failed to hook applyDarkIntensity -> " + e.getMessage());
            }
        }

        // 2. Hook 资源高度 (支持 dp 自动换算为 px)
        try {
            XposedHelpers.findAndHookMethod(Resources.class, "getDimensionPixelSize", int.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    int id = (int) param.args[0];
                    try {
                        Resources res = (Resources) param.thisObject;
                        
                        // 确保资源属于 android 框架层（framework-res）
                        String pkgName = res.getResourcePackageName(id);
                        if ("android".equals(pkgName)) {
                            String entryName = res.getResourceEntryName(id);
                            if (entryName != null) {
                                // 获取当前设备的屏幕密度 (density)，用于 dp 转 px
                                float density = res.getDisplayMetrics().density;

                                if (entryName.equals("navigation_bar_height") && customNavBarHeightDp != -1f) {
                                    int px = (int) (customNavBarHeightDp * density + 0.5f);
                                    param.setResult(px);
                                } else if (entryName.equals("navigation_bar_height_landscape") && customNavBarHeightLandscapeDp != -1f) {
                                    int px = (int) (customNavBarHeightLandscapeDp * density + 0.5f);
                                    param.setResult(px);
                                } else if (entryName.equals("navigation_bar_width") && customNavBarWidthDp != -1f) {
                                    int px = (int) (customNavBarWidthDp * density + 0.5f);
                                    param.setResult(px);
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }
            });
            XposedBridge.log("NavbarCustomizer: Successfully hooked getDimensionPixelSize with dp conversion");
        } catch (Throwable e) {
            XposedBridge.log("NavbarCustomizer: Failed to hook getDimensionPixelSize -> " + e.getMessage());
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

            XposedBridge.log("NavbarCustomizer: Config loaded successfully (dp mode).");
        } catch (Exception e) {
            XposedBridge.log("NavbarCustomizer: Failed to load config -> " + e.getMessage());
        }
    }
}
