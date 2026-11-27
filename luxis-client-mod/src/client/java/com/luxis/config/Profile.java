package com.luxis.config;
import java.util.HashMap;
import java.util.Map;

public final class Profile {
    public String name;
    public Map<String, ModuleState> modules = new HashMap<>();
    public Profile() {}
    public Profile(String name) { this.name = name; }

    public static final class ModuleState {
        public boolean enabled;
        public String key = "", trigger = "TOGGLE";
        public int x = 40, y = 40, width = 170, height = 64;
        public float scale = 1.0F;
        public String anchor = "TOP_LEFT";
        public Map<String, Object> values = new HashMap<>();
    }
}
