package com.blindtechnexus.openfire.model

/**
 * Every weapon in Open Fire.
 *
 * A weapon owns its damage power, its magazine size and the sound effects
 * that belong to it. Melee weapons (knife and sword) never reload, but they
 * can still be damaged by three consecutive point-blank hits, exactly like
 * firearms.
 *
 * Every weapon has a base power and an explicit close-range power, measured
 * as a percentage of the victim's maximum health. Base / 90%-100% values are:
 * knife 10/10, shotgun 10/12, assault rifle 13/14, pistol 14/15,
 * sniper rifle 15/16 and sword 15/16. Robert's assault rifle and shotgun use
 * his own per-zone tables in [RobertRules]. The missile is the exception: it
 * does not use the zone tables at all, because a launched missile always
 * tracks its target and destroys it completely (see [MissileRules]).
 *
 * Every weapon hit also plays the sound of the fighter that was hit: the
 * weapon's own hit sound (knife-hit, sword-hit or gun-hit) is always mixed
 * with enemy-hit or player-hit.
 */
enum class Weapon(
    val id: String,
    val nameKey: String,
    /** Base damage in percent of the victim's maximum health. */
    val powerPercent: Int,
    /** Damage in percent at 90%-100% proximity (can differ from base power). */
    val closeRangePowerPercent: Int,
    /** Rounds in a full magazine. Melee weapons and the missile use 0. */
    val magazine: Int,
    /** Shop price. 0 = never sold, the weapon is received by default. */
    val price: Int,
    val ownedByDefault: Boolean,
    val melee: Boolean,
    val fireSoundKey: String,
    val switchSoundKey: String,
    val hitSoundKey: String,
    val missSoundKey: String,
    /**
     * True only for the missile launcher. It lives in the normal weapon
     * inventory and is switched to with the usual gun-switch sound, but it
     * has no magazine: every launch consumes one stackable missile power.
     */
    val missileLauncher: Boolean = false
) {
    SHORT_GUN(
        id = "shortGun",
        nameKey = "gunShotgun",
        powerPercent = 10,
        closeRangePowerPercent = 12,
        magazine = 6,
        price = 0,
        ownedByDefault = true,
        melee = false,
        fireSoundKey = "shortGun",
        switchSoundKey = "gunReload",
        hitSoundKey = "gunHit",
        missSoundKey = "fire"
    ),
    ASSAULT_GUN(
        id = "assaultGun",
        nameKey = "gunAssault",
        powerPercent = 13,
        closeRangePowerPercent = 14,
        magazine = 6,
        price = 0,
        ownedByDefault = true,
        melee = false,
        fireSoundKey = "assault",
        switchSoundKey = "gunReload",
        hitSoundKey = "gunHit",
        missSoundKey = "fire"
    ),
    PISTOL(
        id = "pistol",
        nameKey = "gunPistol",
        powerPercent = 14,
        closeRangePowerPercent = 15,
        magazine = 6,
        price = 750,
        ownedByDefault = false,
        melee = false,
        fireSoundKey = "pistol",
        switchSoundKey = "gunReload",
        hitSoundKey = "gunHit",
        missSoundKey = "fire"
    ),
    SNIPER(
        id = "sniper",
        nameKey = "gunSniper",
        powerPercent = 15,
        closeRangePowerPercent = 16,
        magazine = 6,
        price = 1200,
        ownedByDefault = false,
        melee = false,
        fireSoundKey = "sniperRifle",
        switchSoundKey = "gunReload",
        hitSoundKey = "gunHit",
        missSoundKey = "fire"
    ),
    KNIFE(
        id = "knife",
        nameKey = "gunKnife",
        powerPercent = 10,
        closeRangePowerPercent = 10,
        magazine = 0,
        price = 500,
        ownedByDefault = false,
        melee = true,
        fireSoundKey = "knifeSwish",
        switchSoundKey = "knifeSwitch",
        hitSoundKey = "knifeHit",
        missSoundKey = "knifeMiss"
    ),
    SWORD(
        id = "sword",
        nameKey = "gunSword",
        powerPercent = 15,
        closeRangePowerPercent = 16,
        magazine = 0,
        price = 950,
        ownedByDefault = false,
        melee = true,
        fireSoundKey = "swordGlide",
        switchSoundKey = "swordSwitch",
        hitSoundKey = "swordHit",
        missSoundKey = "swordMiss"
    ),
    MISSILE(
        id = "missile",
        nameKey = "gunMissile",
        // Display value only: a missile hit reduces the target directly to
        // zero health, so the normal zone damage tables never apply to it.
        powerPercent = 100,
        closeRangePowerPercent = 100,
        magazine = 0,
        price = MissileRules.MISSILE_PRICE,
        ownedByDefault = false,
        melee = false,
        fireSoundKey = "missile",
        // Switching to the missile plays the same gun-switch sound as every
        // other firearm.
        switchSoundKey = "gunReload",
        hitSoundKey = "explosion",
        missSoundKey = "missile",
        missileLauncher = true
    );

    /** Completed rounds required before this weapon can be bought (0 = none). */
    val requiredCompletedRounds: Int
        get() = if (missileLauncher) Progression.MISSILE_UNLOCK_COMPLETED_ROUNDS else 0

    /**
     * Melee weapons are always "loaded"; firearms need a reload after six
     * shots. The missile never reloads either: it is fed by missile powers.
     */
    val needsReload: Boolean
        get() = !melee && !missileLauncher

    companion object {
        /** The weapons every player owns without buying them. */
        fun defaults(): List<Weapon> = Weapon.entries.filter { it.ownedByDefault }

        /** The weapons that appear in the shop. */
        fun forSale(): List<Weapon> = Weapon.entries.filter { it.price > 0 }

        fun fromId(id: String?): Weapon? = Weapon.entries.firstOrNull { it.id == id }
    }
}

/** The four buyable, consumable defences of the game. */
enum class DefenceItem(
    val id: String,
    val nameKey: String,
    val pluralKey: String,
    val price: Int,
    val descriptionKey: String,
    val requiredCompletedRounds: Int = 0
) {
    MEDKIT(
        id = "medkit",
        nameKey = "medkit",
        pluralKey = "medkitsPlural",
        price = 500,
        descriptionKey = "defenceMedkitInfo"
    ),
    GLUE_WALL(
        id = "glueWall",
        nameKey = "glooWall",
        pluralKey = "glueWallsPlural",
        price = 850,
        descriptionKey = "defenceGlueWallInfo"
    ),
    COVER(
        id = "cover",
        nameKey = "cover",
        pluralKey = "coversPlural",
        price = 650,
        descriptionKey = "defenceCoverInfo"
    ),
    ANTI_MISSILE(
        id = "antiMissile",
        nameKey = "antiMissile",
        pluralKey = "antiMissilesPlural",
        price = MissileRules.ANTI_MISSILE_PRICE,
        descriptionKey = "defenceAntiMissileInfo",
        requiredCompletedRounds = Progression.MISSILE_UNLOCK_COMPLETED_ROUNDS
    );

    companion object {
        fun fromId(id: String?): DefenceItem? = DefenceItem.entries.firstOrNull { it.id == id }
    }
}

/**
 * Optional automatic helpers that can be bought, enabled and disabled.
 *
 * Missile power is deliberately NOT an extension any more: it is a
 * stackable, consumable item (like a defence), so it is counted directly in
 * [Inventory.missilePowers] and bought through [Shop.buyMissilePower].
 */
enum class Extension(
    val id: String,
    val nameKey: String,
    val price: Int,
    val descriptionKey: String
) {
    AUTO_RELOAD("autoReload", "extAutoReload", 150, "extAutoReloadInfo"),
    AUTO_MEDKIT("autoMedkit", "extAutoMedkit", 240, "extAutoMedkitInfo"),
    AUTO_COVER("autoCover", "extAutoCover", 300, "extAutoCoverInfo"),
    AUTO_GLUE_WALL("autoGlueWall", "extAutoGlueWall", 300, "extAutoGlueWallInfo");

    companion object {
        fun fromId(id: String?): Extension? = Extension.entries.firstOrNull { it.id == id }
    }
}

enum class ExtensionState {
    NOT_OWNED,
    /** Bought but switched off: it must not trigger while disabled. */
    DISABLED,
    ENABLED
}

/**
 * Everything the player owns and carries: bought weapons, defence counts,
 * extension states and the weapon that was used in the previous game.
 */
data class Inventory(
    val weaponIds: Set<String> = Weapon.defaults().map { it.id }.toSet(),
    val medikits: Int = 0,
    val glueWalls: Int = 0,
    val covers: Int = 0,
    val antiMissiles: Int = 0,
    val extensions: Map<String, ExtensionState> = emptyMap(),
    val lastWeaponId: String = Weapon.SHORT_GUN.id,
    /**
     * Stacked missile powers. Like a defence, every missile launch consumes
     * exactly one, after which another must be bought for the next launch.
     */
    val missilePowers: Int = 0
) {
    fun ownsWeapon(weapon: Weapon): Boolean =
        weapon.ownedByDefault || weaponIds.contains(weapon.id)

    /** All owned battle weapons in switch order, including the missile. */
    fun ownedWeapons(): List<Weapon> = Weapon.entries.filter { ownsWeapon(it) }

    val hasMissilePower: Boolean
        get() = missilePowers > 0

    fun withMissilePowers(value: Int): Inventory =
        copy(missilePowers = value.coerceAtLeast(0))

    fun count(item: DefenceItem): Int = when (item) {
        DefenceItem.MEDKIT -> medikits
        DefenceItem.GLUE_WALL -> glueWalls
        DefenceItem.COVER -> covers
        DefenceItem.ANTI_MISSILE -> antiMissiles
    }

    fun withCount(item: DefenceItem, value: Int): Inventory {
        val safe = value.coerceAtLeast(0)
        return when (item) {
            DefenceItem.MEDKIT -> copy(medikits = safe)
            DefenceItem.GLUE_WALL -> copy(glueWalls = safe)
            DefenceItem.COVER -> copy(covers = safe)
            DefenceItem.ANTI_MISSILE -> copy(antiMissiles = safe)
        }
    }

    fun extensionState(extension: Extension): ExtensionState =
        extensions[extension.id] ?: ExtensionState.NOT_OWNED

    fun extensionOwned(extension: Extension): Boolean =
        extensionState(extension) != ExtensionState.NOT_OWNED

    fun extensionEnabled(extension: Extension): Boolean =
        extensionState(extension) == ExtensionState.ENABLED

    fun withExtension(extension: Extension, state: ExtensionState): Inventory =
        copy(extensions = extensions.toMutableMap().also { it[extension.id] = state })

    /** The weapon the player used last game, if they still own it. */
    fun rememberedWeapon(): Weapon? {
        val remembered = Weapon.fromId(lastWeaponId) ?: return null
        return if (ownsWeapon(remembered)) remembered else null
    }

    /** The weapon the next battle starts with. */
    fun startingWeapon(): Weapon = rememberedWeapon() ?: Weapon.SHORT_GUN
}

/**
 * Result of a shop transaction. [inventory] and [coins] are the new values and
 * [reasonKey] is a translation key describing why a transaction was refused.
 */
data class ShopResult(
    val success: Boolean,
    val coins: Int,
    val inventory: Inventory,
    val reasonKey: String? = null
)

/**
 * Pure shop rules. Keeping the money and inventory arithmetic here makes every
 * purchase testable and guarantees the shop, the battle and the automatic
 * extensions all use exactly the same prices.
 */
object Shop {
    /**
     * Buys a weapon. [completedRounds] is the profile's lifetime completed
     * round count; only the missile checks it (it unlocks after round two).
     * The app always passes the real value; the permissive default only keeps
     * pure price tests of the unrestricted weapons free of progression setup.
     */
    fun buyWeapon(
        inventory: Inventory,
        coins: Int,
        weapon: Weapon,
        completedRounds: Int = Int.MAX_VALUE
    ): ShopResult {
        if (completedRounds < weapon.requiredCompletedRounds) {
            return ShopResult(false, coins, inventory, "missileLocked")
        }
        if (inventory.ownsWeapon(weapon)) {
            return ShopResult(false, coins, inventory, "alreadyOwned")
        }
        if (coins < weapon.price) {
            return ShopResult(false, coins, inventory, "notEnoughCoins")
        }
        return ShopResult(
            success = true,
            coins = coins - weapon.price,
            inventory = inventory.copy(weaponIds = inventory.weaponIds + weapon.id)
        )
    }

    /**
     * Buys exactly one missile power. Powers stack like defences: the player
     * may buy several in a row and every missile launch consumes only one.
     */
    fun buyMissilePower(
        inventory: Inventory,
        coins: Int,
        completedRounds: Int
    ): ShopResult {
        if (completedRounds < Progression.MISSILE_UNLOCK_COMPLETED_ROUNDS) {
            return ShopResult(false, coins, inventory, "missileLocked")
        }
        if (coins < MissileRules.MISSILE_POWER_PRICE) {
            return ShopResult(false, coins, inventory, "notEnoughCoins")
        }
        return ShopResult(
            success = true,
            coins = coins - MissileRules.MISSILE_POWER_PRICE,
            inventory = inventory.withMissilePowers(inventory.missilePowers + 1)
        )
    }

    /** Removes exactly one missile power (a launch, or a shop removal). */
    fun consumeMissilePower(inventory: Inventory): Inventory =
        inventory.withMissilePowers(inventory.missilePowers - 1)

    fun removeWeapon(inventory: Inventory, coins: Int, weapon: Weapon): ShopResult {
        if (weapon.ownedByDefault || !inventory.ownsWeapon(weapon)) {
            return ShopResult(false, coins, inventory, "cannotRemoveWeapon")
        }
        return ShopResult(
            success = true,
            coins = coins,
            inventory = inventory.copy(
                weaponIds = inventory.weaponIds - weapon.id,
                lastWeaponId = if (inventory.lastWeaponId == weapon.id) {
                    Weapon.SHORT_GUN.id
                } else {
                    inventory.lastWeaponId
                }
            )
        )
    }

    /** Defences are repeatable: every purchase adds exactly one item. */
    fun buyDefence(
        inventory: Inventory,
        coins: Int,
        item: DefenceItem,
        completedRounds: Int = Int.MAX_VALUE
    ): ShopResult {
        if (completedRounds < item.requiredCompletedRounds) {
            return ShopResult(false, coins, inventory, "antiMissileLocked")
        }
        if (coins < item.price) {
            return ShopResult(false, coins, inventory, "notEnoughCoins")
        }
        return ShopResult(
            success = true,
            coins = coins - item.price,
            inventory = inventory.withCount(item, inventory.count(item) + 1)
        )
    }

    fun buyExtension(
        inventory: Inventory,
        coins: Int,
        extension: Extension
    ): ShopResult {
        if (inventory.extensionOwned(extension)) {
            return ShopResult(false, coins, inventory, "alreadyOwned")
        }
        if (coins < extension.price) {
            return ShopResult(false, coins, inventory, "notEnoughCoins")
        }
        return ShopResult(
            success = true,
            coins = coins - extension.price,
            inventory = inventory.withExtension(extension, ExtensionState.ENABLED)
        )
    }

    fun removeExtension(inventory: Inventory, coins: Int, extension: Extension): ShopResult {
        if (!inventory.extensionOwned(extension)) {
            return ShopResult(false, coins, inventory, "notOwned")
        }
        return ShopResult(
            success = true,
            coins = coins,
            inventory = inventory.withExtension(extension, ExtensionState.NOT_OWNED)
        )
    }

    /** Switches an owned extension between enabled and disabled. */
    fun toggleExtension(inventory: Inventory, coins: Int, extension: Extension): ShopResult {
        val current = inventory.extensionState(extension)
        if (current == ExtensionState.NOT_OWNED) {
            return ShopResult(false, coins, inventory, "notOwned")
        }
        val next = if (current == ExtensionState.ENABLED) {
            ExtensionState.DISABLED
        } else {
            ExtensionState.ENABLED
        }
        return ShopResult(
            success = true,
            coins = coins,
            inventory = inventory.withExtension(extension, next)
        )
    }

    /** Removes one used defence. */
    fun consume(inventory: Inventory, item: DefenceItem): Inventory =
        inventory.withCount(item, inventory.count(item) - 1)

    /** Adds a defence without charging for it (used by rewards and migration). */
    fun grant(inventory: Inventory, item: DefenceItem, amount: Int = 1): Inventory =
        inventory.withCount(item, inventory.count(item) + amount.coerceAtLeast(0))
}
