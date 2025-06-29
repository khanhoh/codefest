import jsclub.codefest.sdk.Hero;
import jsclub.codefest.sdk.base.Node;
import jsclub.codefest.sdk.model.ElementType;
import jsclub.codefest.sdk.model.Inventory;
import jsclub.codefest.sdk.model.equipments.Armor;
import jsclub.codefest.sdk.model.equipments.HealingItem;
import jsclub.codefest.sdk.model.players.Player;
import jsclub.codefest.sdk.model.weapon.Weapon;

import java.util.ArrayList;
import java.util.List;

public class PointFactory {
    Hero hero;
    List<HealingItem> healingItems;
    TrackPlayer trackPlayer = new TrackPlayer();
    Player me;
    Weapon gun, melee;
    double totalDamageReduce;

    public double getTotalDamageReduce() {
        return totalDamageReduce;
    }

    public void setHero(Hero hero) {
        this.hero = hero;
    }

    public PointFactory(Weapon gun, Weapon melee) {
        this.gun = gun;
        this.melee = melee;
    }

    double getPointArmor(Armor armor) {
        if (armor == null || hero == null || hero.getInventory() == null) {
            return 0;
        }
        Inventory inventory = hero.getInventory();

        List<Armor> listArmor = new ArrayList<>();
        if (inventory.getArmor() != null) {
            listArmor.add(inventory.getArmor());
        }
        if (inventory.getHelmet() != null) {
            listArmor.add(inventory.getHelmet());
        }

        totalDamageReduce = 0; // Reset để tính lại chính xác
        for (Armor p : listArmor) {
            totalDamageReduce += p.getDamageReduce();
        }

        int gainArmor = armor.getDamageReduce();
        for (Armor p : listArmor) {
            if (p.getType() == armor.getType()) {
                gainArmor = armor.getDamageReduce() - p.getDamageReduce();
            }
        }
        totalDamageReduce += gainArmor;

        return (double) (gainArmor * 800) / (Utils.distance(armor, hero) + 1);
    }

    double getPointHealth(int health) {
        if (healingItems == null || healingItems.size() == 4 || me == null) {
            return 0;
        }
        double urgencyFactor = 2 + (100.0 - me.getHealth()) / 15;
        return (int) (health * 100 * urgencyFactor);
    }

    double getPointHealth(HealingItem health) {
        if (health == null || hero == null) {
            return 0;
        }
        return (double) (health.getHealingHP() * 800) / (Utils.distance(health, hero) + health.getUsageTime() + 1);
    }

    double getPointPlayer(Player player, Node nextToPlayer) {
        if (player == null || nextToPlayer == null || me == null || hero == null || hero.getInventory() == null) {
            return 0;
        }
        gun = hero.getInventory().getGun();
        melee = hero.getInventory().getMelee();
        Float myHp = me.getHealth();
        Float playerHp = player.getHealth();
        double stepToKillMe = trackPlayer.getStepToKill(me.getID(), myHp);
        double stepToKillPlayer = (gun != null && melee != null) ? (int) Utils.stepToKill(gun, melee, playerHp) : 0;
        double factor = Math.min(stepToKillMe / stepToKillPlayer, 1);
        factor = factor * factor;
        return (int) (100 * (me.getHealth() + 35) * factor / (Utils.distance(nextToPlayer, hero) + 8));
    }

    double getPointWeapon(Weapon weapon) {
        if (weapon == null || hero == null || hero.getInventory() == null) {
            return 0;
        }
        gun = hero.getInventory().getGun();
        melee = hero.getInventory().getMelee();
        int pointWeapon = 0;

        if (weapon.getType() == ElementType.THROWABLE) {
            if (hero.getInventory().getThrowable() == null) {
                pointWeapon = weapon.getDamage();
            }
        }
        if (weapon.getType() == ElementType.MELEE) {
            pointWeapon = (weapon.getDamage() - (melee != null && !melee.getId().equals("HAND") && !melee.getId().equals("Null") ? Utils.getDame(melee) : 0)) * 4;
        }
        if (weapon.getType() == ElementType.GUN) {
            pointWeapon = (weapon.getDamage() - (gun != null ? Utils.getDame(gun) : 0)) * 4;
        }
        return (double) (pointWeapon * 100) / (Utils.distance(weapon, hero) + 1);
    }

    double getPointChest(Node chest) {
        if (chest == null || hero == null || hero.getInventory() == null) {
            return 0;
        }
        double pointChest = 0;
        gun = hero.getInventory().getGun();
        melee = hero.getInventory().getMelee();
        if (totalDamageReduce < 20) {
            pointChest += 20 * 800 * (1 - Math.pow(1 - 0.02, 4));
        }
        if (totalDamageReduce == 20 || totalDamageReduce == 0) {
            pointChest += 5 * 800 * (1 - Math.pow(1 - 0.03, 4)) + 10 * 800 * (1 - Math.pow(1 - 0.05, 4));
        }
        pointChest += getPointHealth(15);
        if (melee != null && Utils.getDame(melee) <= 45) {
            pointChest += (55 - Utils.getDame(melee)) * 400 * (1 - Math.pow(1 - 0.05, 4));
        }
        if (melee == null || Utils.getDame(melee) == 0) {
            pointChest += 45 * 400 * (1 - Math.pow(1 - 0.16, 4));
        }
        if (hero.getInventory().getThrowable() == null) {
            pointChest += 25 * 100 * (1 - Math.pow(1 - 0.40, 4));
        }
        return pointChest / (Utils.distance(chest, hero) + 4);
    }
}