package com.example.navbarcustomizer;

import android.content.Context;
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

        XposedBridge.log("NavbarCustomizer: Loaded into SystemUI (Pure SystemUI Mode)");

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

        // 2. 纯 SystemUI 内部拦截：直接修改 NavigationBarView 的高度
        if (customNavBarHeightDp != -1f) {
            try {
                // 锁定 SystemUI 中的 NavigationBarView 类
                Class<?> navBarViewClass = XposedHelpers.findClassIfExists(
                        "com.android.systemui.navigationbar.NavigationBarView", 
                        lpparam.classLoader
                );

                if (navBarViewClass != null) {
                    // Hook 视图加载完成或尺寸更新的方法（不同 Android 版本通常在 onFinishInflate 或 updateLayoutters 中初始化布局）
                    XposedHelpers.findAndHookMethod(navBarViewClass, "onFinishInflate", new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            View navBarView = (View) param.thisObject;
                            Context context = navBarView.getContext();
                            float density = context.getResources().getDisplayMetrics().density;
                            int targetPx = (int) (customNavBarHeightDp * density + 0.5f);

                            // 强行修改其 LayoutParams 高度
                            ViewGroup.LayoutParams layoutParams = navBarView.getLayoutParams();
                            if (layoutParams != null) {
                                layoutParams.height = targetPx;
                                navBarView.setLayoutParams(layoutParams);
                                XposedBridge.log("NavbarCustomizer: [Pure SystemUI] Forced NavigationBarView height to " + targetPx + "px (" + customNavBarHeightDp + "dp)");
                            }
                        }
                    });
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
