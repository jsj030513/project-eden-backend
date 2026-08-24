package com.projecteden.world.npc;

import com.projecteden.world.ecology.WorldAssetType;

public enum CanonicalNpcKey {
    NPC_MAYOR(
            WorldAssetType.DEFAULT_NPC_GUIDE,
            "npc.mayor.name",
            "npc-mayor",
            "portrait-mayor",
            "dialogue.mayor.default",
            "schedule.mayor",
            NpcWorldAnchor.MAYOR_HOME),
    NPC_GARDENER(
            WorldAssetType.DEFAULT_NPC_GARDENER,
            "npc.gardener.name",
            "npc-gardener",
            "portrait-gardener",
            "dialogue.gardener.default",
            "schedule.gardener",
            NpcWorldAnchor.GARDENER_HOME),
    NPC_RESEARCHER(
            WorldAssetType.DEFAULT_NPC_MEMORY_KEEPER,
            "npc.researcher.name",
            "npc-researcher",
            "portrait-researcher",
            "dialogue.researcher.default",
            "schedule.researcher",
            NpcWorldAnchor.ARCHIVE),
    NPC_CARETAKER(
            WorldAssetType.DEFAULT_NPC_ANIMAL_CARETAKER,
            "npc.caretaker.name",
            "npc-caretaker",
            "portrait-caretaker",
            "dialogue.caretaker.default",
            "schedule.caretaker",
            NpcWorldAnchor.CARETAKER_HOME);

    private final WorldAssetType assetType;
    private final String displayNameKey;
    private final String spriteKey;
    private final String portraitKey;
    private final String defaultDialogueKey;
    private final String scheduleKey;
    private final NpcWorldAnchor homeAnchor;

    CanonicalNpcKey(
            WorldAssetType assetType,
            String displayNameKey,
            String spriteKey,
            String portraitKey,
            String defaultDialogueKey,
            String scheduleKey,
            NpcWorldAnchor homeAnchor) {
        this.assetType = assetType;
        this.displayNameKey = displayNameKey;
        this.spriteKey = spriteKey;
        this.portraitKey = portraitKey;
        this.defaultDialogueKey = defaultDialogueKey;
        this.scheduleKey = scheduleKey;
        this.homeAnchor = homeAnchor;
    }

    public WorldAssetType assetType() { return assetType; }
    public String displayNameKey() { return displayNameKey; }
    public String spriteKey() { return spriteKey; }
    public String portraitKey() { return portraitKey; }
    public String defaultDialogueKey() { return defaultDialogueKey; }
    public String scheduleKey() { return scheduleKey; }
    public NpcWorldAnchor homeAnchor() { return homeAnchor; }
    public int interactionRange() { return 1; }
    public boolean enabled() { return true; }

    public static CanonicalNpcKey from(WorldAssetType assetType) {
        for (CanonicalNpcKey key : values()) {
            if (key.assetType == assetType) return key;
        }
        return null;
    }
}
