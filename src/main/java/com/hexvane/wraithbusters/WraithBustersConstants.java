package com.hexvane.wraithbusters;

import java.util.Set;

public final class WraithBustersConstants {
    public static final String INSTANCE_NAME = "WraithBusters_Mansion";
    public static final String DEFAULT_ARENA_ID = "mansion_v1";
    public static final String MANA_ORB_NPC_ROLE = "WraithBusters_Mana_Orb";
    public static final String MANA_PICKUP_SOUND_EVENT = "Ping_01";
    public static final String PUZZLE_SUCCESS_SOUND_EVENT = "Ping_01";
    public static final String PUZZLE_FAIL_SOUND_EVENT = "SFX_Candle_Off";
    public static final String OFFERING_REJECT_PARTICLE = "WraithBusters_Smoke_Black";
    public static final String OFFERING_SUCCESS_PARTICLE = "WraithBusters_Smoke_Tall_Round";
    public static final float OFFERING_FEEDBACK_PARTICLE_DURATION_SEC = 2.0f;
    public static final float OFFERING_INSERT_PARTICLE_DURATION_SEC = 1.0f;
    public static final String OFFERING_INSERT_SOUND_EVENT = "SFX_Player_Pickup_Item";
    public static final float OFFERING_DEFAULT_SOUND_VOLUME = 1.0f;
    public static final String PHASE_DOOR_SOUND_EVENT = "SFX_Portal_Neutral_Teleport_Local";
    public static final String POSSESSABLE_MARKER_ICON_NPC_ROLE = "WraithBusters_Possessable_Marker_Icon";
    public static final String PHASE_PORTAL_NPC_ROLE = "WraithBusters_Phase_Portal_1x2";
    public static final String PHASE_PORTAL_NPC_ROLE_1x2 = "WraithBusters_Phase_Portal_1x2";
    public static final String PHASE_PORTAL_NPC_ROLE_2x2 = "WraithBusters_Phase_Portal_2x2";
    public static final String PHASE_PORTAL_NPC_ROLE_3x3 = "WraithBusters_Phase_Portal_3x3";
    public static final String PHASE_PORTAL_NPC_ROLE_4x4 = "WraithBusters_Phase_Portal_4x4";
    public static final String PHASE_DOOR_TOOL_ITEM = "WraithBusters_Phase_Door_Tool";
    public static final String HUMAN_LOCKED_DOOR_NPC_ROLE = "WraithBusters_Human_Locked_Door";
    /** Room id for the attic door; always chained last when present in arena layout. */
    public static final String ATTIC_ROOM_ID = "Attic";
    /** Puzzle rooms eligible for random round selection when DebugForceRoomChain is empty. */
    public static final Set<String> FINISHED_ROOM_IDS = Set.of(
        "Dining_Room",
        "Garden",
        "Kitchen",
        "Laboratory",
        "Library"
    );
    public static final String EXORCISM_TABLE_BLOCK_ID = "WraithBusters_Exorcism_Table";
    public static final String EXORCISM_TABLE_DORMANT_STATE = "default";
    public static final String EXORCISM_TABLE_CHARGING_STATE = "Charging";
    public static final String EXORCISM_TABLE_ACTIVATED_STATE = "Activated";
    public static final String EXORCISM_BURST_PARTICLE = "WraithBusters_Exorcism_Burst";
    public static final float EXORCISM_BURST_SCALE = 2.0f;
    public static final float EXORCISM_BURST_DURATION_SEC = 2.0f;
    public static final String EXORCISM_ACTIVATE_SOUND_EVENT = "SFX_Flame_Ignite";
    public static final String ROUND_WIN_SOUND_EVENT = "SFX_Memories_Unlock_Local";
    public static final String POSSESSABLE_PLATE_BLOCK_ID = "WraithBusters_Possessable_Plate";
    public static final String POSSESSABLE_CANDLE_BLOCK_ID = "WraithBusters_Spooky_Temple_Dark_Candle";
    public static final String POSSESSABLE_STATUE_BLOCK_ID = "WraithBusters_Sword_Statue";
    public static final String POSSESSABLE_BUSH_BLOCK_ID = "WraithBusters_Possessable_Bush";
    public static final String POSSESSABLE_HIVE_BLOCK_ID = "WraithBusters_Possessable_Hive";
    public static final String POSSESSABLE_SKULL_WALL_BLOCK_ID = "WraithBusters_Possessable_Skull_Wall";
    public static final String POSSESSABLE_COCOON_BLOCK_ID = "WraithBusters_Possessable_Cocoon";
    public static final String POSSESSABLE_BARREL_BLOCK_ID = "WraithBusters_Possessable_Barrel";
    public static final String POSSESSABLE_WATCHER_STATUE_BLOCK_ID = "WraithBusters_Watcher_Statue";
    public static final String WATCHER_FEATHER_PROJECTILE_CONFIG_ID = "WraithBusters_Feather_Projectile";
    public static final String WATCHER_FEATHER_LAUNCH_SOUND_EVENT = "SFX_NPC_Unarmed_Swing";
    public static final String POSSESSABLE_BEE_NPC_ROLE = "WraithBusters_Possessable_Bee";
    public static final String POSSESSABLE_FLAMING_SKULL_NPC_ROLE = "WraithBusters_Possessable_Flaming_Skull";
    public static final String HIVE_POISON_EFFECT_ID = "WraithBusters_Possessable_Poison";
    public static final String SNAPDRAGON_POISON_EFFECT_ID = "WraithBusters_Possessable_Poison_Strong";
    public static final String VANILLA_SNAPDRAGON_POISON_EFFECT_ID = "Poison_T3";
    public static final String HIVE_ACTIVATE_SOUND_EVENT = "SFX_Z1_Emit_Swamp_Day_Insects";
    public static final String POSSESSABLE_SNAPDRAGON_NPC_ROLE = "WraithBusters_Possessable_Snapdragon";
    public static final String POSSESSABLE_SNAPDRAGON_POOF_PARTICLE = "WraithBusters_Possessable_Snapdragon_Poof";
    public static final float POSSESSABLE_SNAPDRAGON_POOF_DURATION_SEC = 1.0f;
    public static final String POSSESSABLE_FOOD_TORNADO_NPC_ROLE = "WraithBusters_Possessable_Food_Tornado";
    public static final String POSSESSABLE_FOOD_TORNADO_POOF_PARTICLE = "WraithBusters_Food_Tornado_Poof";
    public static final float POSSESSABLE_FOOD_TORNADO_POOF_DURATION_SEC = 1.0f;
    public static final String BARREL_CORN_PROJECTILE_CONFIG_ID = "WraithBusters_Corn_Projectile";
    public static final String BARREL_ACTIVATE_SOUND_EVENT = "SFX_Bush_Break";
    public static final String BARREL_CORN_LAUNCH_SOUND_EVENT = "SFX_NPC_Unarmed_Swing";
    public static final String BUSH_ACTIVATE_SOUND_EVENT = "SFX_Bush_Break";
    public static final String STATUE_SWING_STATE = "Swing";
    public static final String STATUE_SWING_SOUND_EVENT = "SFX_Light_Melee_T2_Swing";
    /** Vanilla sword trail along the swing arc. */
    public static final String STATUE_SWING_ARC_PARTICLE = "Sword_Charged_Trail_Blade";
    /** Vanilla sword impact burst on hit. */
    public static final String STATUE_SWING_HIT_PARTICLE = "Impact_Sword_Basic";
    public static final String CANDLE_FIRE_RING_PARTICLE = "WraithBusters_Candle_Fire_Ring";
    public static final String CANDLE_ACTIVATE_SOUND_EVENT = "SFX_Flame_Ignite";
    public static final float CANDLE_FIRE_RING_DURATION_SEC = 1.5f;
    public static final String COCOON_BURST_PARTICLE = "WraithBusters_Cocoon_Burst";
    public static final float COCOON_BURST_DURATION_SEC = 1.5f;
    public static final String COCOON_SLOW_EFFECT_ID = "WraithBusters_Cocoon_Slow";
    public static final String COCOON_ACTIVATE_SOUND_EVENT = "SFX_Cocoon_Hit";
    public static final String BURN_ENTITY_EFFECT_ID = "Burn";
    public static final String HUMAN_NO_SPRINT_EFFECT_ID = "WraithBusters_NoSprint";

    public static final String SLOTH_PORTRAIT_BLOCK_ID = "WraithBusters_Sloth_Portrait";
    public static final String SLOTH_PORTRAIT_NPC_ROLE = "WraithBusters_Sloth_Portrait";
    public static final String KLOPS_PORTRAIT_BLOCK_ID = "WraithBusters_Klops_Portrait";
    public static final String KLOPS_PORTRAIT_NPC_ROLE = "WraithBusters_Klops_Portrait";
    public static final String OUTLANDER_PORTRAIT_BLOCK_ID = "WraithBusters_Outlander_Portrait";
    public static final String OUTLANDER_PORTRAIT_NPC_ROLE = "WraithBusters_Outlander_Portrait";

    public static final String CHEESE_CHASE_PUZZLE_ID = "cheese_chase";
    public static final String LIBRARY_BOOKS_PUZZLE_ID = "library_books";
    public static final String BOOKSHELF_MISSING_STATE = "MissingBook";
    public static final String BOOKSHELF_COMPLETE_STATE = "default";
    public static final String CHEESE_ITEM_ID = "WraithBusters_Cheese";
    public static final String CHEESE_MOUSE_NPC_ROLE = "WraithBusters_Cheese_Mouse";
    public static final String CHUMBO_NPC_ROLE = "WraithBusters_Chumbo";
    public static final int CHEESE_REQUIRED = 5;
    public static final String CHEESE_CHASE_MOUSE_POOF_PARTICLE = "WraithBusters_CheeseChase_MousePoof";
    public static final float CHEESE_CHASE_MOUSE_POOF_DURATION_SEC = 0.75f;
    public static final String CHEESE_CHASE_CATCH_MOUSE_SOUND_EVENT = "SFX_Mouse_Flee";
    public static final String CHEESE_CHASE_FEED_CHUMBO_SOUND_EVENT = "SFX_Consume_Bread";
    public static final String PLATE_LAUNCH_SOUND_EVENT = "SFX_NPC_Unarmed_Swing";
    public static final String SKULL_SPAWN_SOUND_EVENT = "SFX_Staff_Flame_Fireball_Launch";
    public static final String SKULL_HIT_SOUND_EVENT = "SFX_Staff_Flame_Fireball_Impact";
    public static final String SKULL_HIT_PARTICLE = "WraithBusters_Exorcism_Burst";
    public static final float SKULL_HIT_PARTICLE_SCALE = 1.25f;
    public static final float SKULL_HIT_PARTICLE_DURATION_SEC = 1.0f;

    public static final String LOBBY_HUD_KEY = "WraithBusters_LobbyStatus";
    public static final String ROUND_TIMER_HUD_KEY = "WraithBusters_RoundTimer";
    public static final String GHOST_MANA_HUD_KEY = "WraithBusters_GhostMana";

    public static final String PERMISSION_ADMIN = "wraithbusters.admin";
    public static final String PERMISSION_SETUP = "wraithbusters.setup";

    private WraithBustersConstants() {}
}
