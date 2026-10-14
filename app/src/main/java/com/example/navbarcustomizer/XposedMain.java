package com.example.navbarcustomizer;

import android.content.res.XResources;
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

    // 配置文件路径（建议放在应用私有目录，或者具有全局读取权限的路径）
    private static final String CONFIG_PATH = "/data/system/navbar_customizer_config.txt";

    private boolean alwaysDark = true;
    private int customNavBarHeight = -1;
    private int customNavBarHeightLandscape = -1;
    private int customNavBarWidth = -1;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!"com.android.systemui".equals(lpparam.packageName)) {
            return;
        }

        XposedBridge.log("NavbarCustomizer: Loaded into SystemUI");

        // 加载配置文件
        loadConfig();

        // 1. Hook 导航栏暗色强度
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
                            // 强制设为 1.0f 保持暗色
                            param.args[0] = 1.0f;
                        }
                    });
                    XposedBridge.log("NavbarCustomizer: Successfully hooked applyDarkIntensity");
                }
            } catch (Throwable e) {
                XposedBridge.log("NavbarCustomizer: Failed to hook applyDarkIntensity -> " + e.getMessage());
            }
        }

        // 2. Hook 资源高度

        try {
            XposedHelpers.findAndHookMethod(Resources.class, "getDimensionPixelSize", int.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    int id = (int) param.args[0];
                    try {
                        Resources res = (Resources) param.thisObject;
                
                        // 检查资源是否属于 android 框架层（framework-res 中的 dimen）
                        String pkgName = res.getResourcePackageName(id);
                        if ("android".equals(pkgName)) {
                            String entryName = res.getResourceEntryName(id);
                            if (entryName != null) {
                                float density = res.getDisplayMetrics().density;
                                
                                if (entryName.equals("navigation_bar_height") && customNavBarHeight != -1) {
                                    // 如果你想填 dp，可以用 customNavBarHeight * density；如果直接填像素 px，则去掉 density
                                    int px = (int) (customNavBarHeight * density + 0.5f);
                                    param.setResult(px);
                                } else if (entryName.equals("navigation_bar_height_landscape") && customNavBarHeightLandscape != -1) {
                                    int px = (int) (customNavBarHeightLandscape * density + 0.5f);
                                    param.setResult(px);
                                } else if (entryName.equals("navigation_bar_width") && customNavBarWidth != -1) {
                                    int px = (int) (customNavBarWidth * density + 0.5f);
                                    param.setResult(px);
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }
            });
            XposedBridge.log("NavbarCustomizer: Successfully hooked getDimensionPixelSize");
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
            if (heightStr != null) customNavBarHeight = Integer.parseInt(heightStr.trim());

            String heightLandStr = props.getProperty("navigation_bar_height_landscape");
            if (heightLandStr != null) customNavBarHeightLandscape = Integer.parseInt(heightLandStr.trim());

            String widthStr = props.getProperty("navigation_bar_width");
            if (widthStr != null) customNavBarWidth = Integer.parseInt(widthStr.trim());

            XposedBridge.log("NavbarCustomizer: Config loaded successfully.");
        } catch (Exception e) {
            XposedBridge.log("NavbarCustomizer: Failed to load config -> " + e.getMessage());
        }
    }
}
