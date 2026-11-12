package com.riderword.plugin;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.StatusBarWidgetFactory;
import org.jetbrains.annotations.NotNull;

public final class RiderWordWidgetFactory implements StatusBarWidgetFactory {

    @Override
    public @NotNull String getId() {
        return RiderWordWidget.ID;
    }

    @Override
    public @NotNull String getDisplayName() {
        return "RWord";
    }

    @Override
    public boolean isAvailable(@NotNull Project project) {
        return true;
    }

    @Override
    public @NotNull StatusBarWidget createWidget(@NotNull Project project) {
        return new RiderWordWidget(project);
    }

    @Override
    public void disposeWidget(@NotNull StatusBarWidget widget) {
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }
}
