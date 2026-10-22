package com.blindtechnexus.openfire.model

/**
 * Every weapon in Open Fire.
 *
 * A weapon owns its damage power, its magazine size and the sound effects
 * that belong to it. Melee weapons (knife and sword) never reload, but they
 * can still be damaged by three consecutive point-blank hits, exactly like
 * firearms.
 *
 * Power table (percentage of the victim's maximum health at 90%-100%
 * proximity, before the zone multiplier): knife 8, sword 12, assault gun 14,
 * pistol 13, sniper rifle 16 and short gun 8. The short gun is the only
 * exception: at 90%-100% proximity it hits with 10 instead of 8, see
 * [Combat.SHORT_GUN_CLOSE_POWER_PERCENT]. The Robert (the enemy) uses exactly
 * the same table.
 *
 * Every weapon hit also plays the sound of the fighter that was hit: the
 * weapon's own hit sound (knife-hit, sword-hit or gun-hit) is always mixed
 * with enemy-hit or player-hit.
 */
enum class Weapon(
    val id: String,
    val nameKey: String,
    /** Damage in percent of the victim's health at 90%-100% proximity. */
    val powerPercent: Int,
    /** Rounds in a full magazine. Melee weapons use 0. */
    val magazine: Int,
    /** Shop price. 0 = never sold, the weapon is received by default. */
    val price: Int,
    val ownedByDefault: Boolean,
    val melee: Boolean,
    val fireSoundKey: String,
    val switchSoundKey: String,
    val hitSoundKey: String,
    val missSoundKey: String
) {
    SHORT_GUN(
        id = "shortGun",
        nameKey = "gunShotgun",
        powerPercent = 8,
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
        powerPercent = 14,
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
        powerPercent = 13,
        magazine = 6,
        price = 499,
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
        powerPercent = 16,
        magazine = 6,
        price = 549,
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
        powerPercent = 8,
        magazine = 0,
        price = 300,
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
        powerPercent = 12,
        magazine = 0,
        price = 405,
        ownedByDefault = false,
        melee = true,
        fireSoundKey = "swordGlide",
        switchSoundKey = "swordSwitch",
        hitSoundKey = "swordHit",
        missSoundKey = "swordMiss"
    );

    /** Melee weapons are always "loaded"; firearms need a reload after six shots. */
    val needsReload: Boolean
        get() = !melee

    companion object {
        /** The weapons every player owns without buying them. */
        fun defaults(): List<Weapon> = Weapon.entries.filter { it.ownedByDefault }

        /** The weapons that appear in the shop. */
        fun forSale(): List<Weapon> = Weapon.entries.filter { it.price > 0 }

        fun fromId(id: String?): Weapon? = Weapon.entries.firstOrNull { it.id == id }
    }
}

/** The three buyable, consumable defences of the game. */
enum class DefenceItem(
    val id: String,
    val nameKey: String,
    val pluralKey: String,
    val price: Int,
    val descriptionKey: String
) {
    MEDKIT(
        id = "medkit",
        nameKey = "medkit",
        pluralKey = "medkitsPlural",
        price = 200,
        descriptionKey = "defenceMedkitInfo"
    ),
    GLUE_WALL(
        id = "glueWall",
        nameKey = "glooWall",
        pluralKey = "glueWallsPlural",
        price = 400,
        descriptionKey = "defenceGlueWallInfo"
    ),
    COVER(
        id = "cover",
        nameKey = "cover",
        pluralKey = "coversPlural",
        price = 350,
        descriptionKey = "defenceCoverInfo"
    );

    companion object {
        fun fromId(id: String?): DefenceItem? = DefenceItem.entries.firstOrNull { it.id == id }
    }
}

/** Optional automatic helpers that can be bought, enabled and disabled. */
enum class Extension(
    val id: String,
    val nameKey: String,
    val price: Int,
    val descriptionKey: String
) {
    AUTO_RELOAD("autoReload", "extAutoReload", 50, "extAutoReloadInfo"),
    AUTO_MEDKIT("autoMedkit", "extAutoMedkit", 80, "extAutoMedkitInfo"),
    AUTO_COVER("autoCover", "extAutoCover", 100, "extAutoCoverInfo"),
    AUTO_GLUE_WALL("autoGlueWall", "extAutoGlueWall", 100, "extAutoGlueWallInfo");

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
    val extensions: Map<String, ExtensionState> = emptyMap(),
    val lastWeaponId: String = Weapon.SHORT_GUN.id
) {
    fun ownsWeapon(weapon: Weapon): Boolean =
        weapon.ownedByDefault || weaponIds.contains(weapon.id)

    fun ownedWeapons(): List<Weapon> = Weapon.entries.filter { ownsWeapon(it) }

    fun count(item: DefenceItem): Int = when (item) {
        DefenceItem.MEDKIT -> medikits
        DefenceItem.GLUE_WALL -> glueWalls
        DefenceItem.COVER -> covers
    }

    fun withCount(item: DefenceItem, value: Int): Inventory {
        val safe = value.coerceAtLeast(0)
        return when (item) {
            DefenceItem.MEDKIT -> copy(medikits = safe)
            DefenceItem.GLUE_WALL -> copy(glueWalls = safe)
            DefenceItem.COVER -> copy(covers = safe)
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
    fun buyWeapon(inventory: Inventory, coins: Int, weapon: Weapon): ShopResult {
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
    fun buyDefence(inventory: Inventory, coins: Int, item: DefenceItem): ShopResult {
        if (coins < item.price) {
            return ShopResult(false, coins, inventory, "notEnoughCoins")
        }
        return ShopResult(
            success = true,
            coins = coins - item.price,
            inventory = inventory.withCount(item, inventory.count(item) + 1)
        )
    }

    fun buyExtension(inventory: Inventory, coins: Int, extension: Extension): ShopResult {
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
