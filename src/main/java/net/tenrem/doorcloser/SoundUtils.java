package net.tenrem.doorcloser;

import org.bukkit.Sound;

@SuppressWarnings("unused")
public enum SoundUtils {

    DOOR_CLOSE("DOOR_CLOSE", "BLOCK_WOODEN_DOOR_CLOSE"),
    GATE_CLOSE("DOOR_CLOSE", "BLOCK_FENCE_GATE_CLOSE"),
    DOOR_OPEN("DOOR_OPEN", "BLOCK_WOODEN_DOOR_OPEN"),
    GATE_OPEN("DOOR_CLOSE", "BLOCK_FENCE_GATE_OPEN");

    private final String before1_9;
    private final String after1_9;
    private Sound resolvedSound = null;

    SoundUtils(String before1_9, String after1_9) {
        this.before1_9 = before1_9;
        this.after1_9 = after1_9;
    }

    public Sound getSound() {
        if (resolvedSound != null) return resolvedSound;
        try {
            return resolvedSound = Sound.valueOf(after1_9);
        } catch (IllegalArgumentException e) {
            return resolvedSound = Sound.valueOf(before1_9);
        }
    }
}
