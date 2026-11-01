package com.synapmc;

import java.util.function.IntFunction;


public interface SidebarAccess {
    void synapmc$prepareSidebar(Object objective, Object title, Object[] entries, IntFunction<Object[]> factory);
}
