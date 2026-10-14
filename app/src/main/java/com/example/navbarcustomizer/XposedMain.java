package com.example.navbarcustomizer;

import android.content.res.Resources;
import android.view.View;
import android.view.ViewGroup;
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

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!"com.android.systemui".equals(lpparam.packageName)) {
            return;
        }

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

        // 2. 直接 Hook 导航栏视图的测量逻辑 (NavigationBarView) 强制设定高度
        if (customNavBarHeightDp != -1f) {
            try {
                Class<?> navBarViewClass = XposedHelpers.findClassIfExists(
                        "com.android.systemui.navigationbar.NavigationBarView", 
                        lpparam.classLoader
                );
                
                if (navBarViewClass != null) {
                    XposedHelpers.findAndHookMethod(navBarViewClass, "updateLayoutters", int.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            View navBarView = (View) param.thisObject;
                            Context context = navBarView.getContext();
                            float density = context.getResources().getDisplayMetrics().density;
                            int targetPx = (int) (customNavBarHeightDp * density + 0.5f);

                            ViewGroup.LayoutParams layoutParams = navBarView.getLayoutParams();
                            if (layoutParams != null && layoutParams.height != targetPx) {
                                layoutParams.height = targetPx;
                                navBarView.setLayoutParams(layoutParams);
                                XposedBridge.log("NavbarCustomizer: Forced NavigationBarView height to " + targetPx + "px");
                            }
                        }
                    });
                    XposedBridge.log("NavbarCustomizer: Successfully hooked NavigationBarView layout");
                }
            } catch (Throwable e) {
                XposedBridge.log("NavbarCustomizer: Failed to hook NavigationBarView -> " + e.getMessage());
            }
        }
    }

    private void loadConfig() {
        File file = new File(CONFIG_PATH);
        if (!file.exists()) return;

        try (FileInputStream fis = new FileInputStream(file)) {
            Properties props = new Properties();
            props.load(fis);
            alwaysDark = Boolean.parseBoolean(props.getProperty("always_dark_intensity", "true"));
            String heightStr = props.getProperty("navigation_bar_height");
            if (heightStr != null) customNavBarHeightDp = Float.parseFloat(heightStr.trim());
        } catch (Exception ignored) {}
    }
}
