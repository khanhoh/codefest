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
        this.me = hero != null && hero.getGameMap() != null ? hero.getGameMap().getCurrentPlayer() : null;
    }

    public PointFactory(Weapon gun, Weapon melee) {
        this.gun = gun;
        this.melee = melee;
    }

    double getPointArmor(Armor armor) {
        if (armor == null || hero == null || hero.getInventory() == null) {
            System.out.println("DEBUG: getPointArmor - armor or hero or inventory is null");
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

        totalDamageReduce = 0;
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
            System.out.println("DEBUG: getPointHealth - healingItems or me is null or inventory full");
            return 0;
        }
        double urgencyFactor = 2 + (100.0 - me.getHealth()) / 15;
        return (int) (health * 100 * urgencyFactor);
    }

    double getPointHealth(HealingItem health) {
        if (health == null || hero == null) {
            System.out.println("DEBUG: getPointHealth - health or hero is null");
            return 0;
        }
        return (double) (health.getHealingHP() * 800) / (Utils.distance(health, hero) + health.getUsageTime() + 1);
    }

    double getPointPlayer(Player player, Node nextToPlayer) {
        if (player == null || nextToPlayer == null || me == null || hero == null || hero.getInventory() == null) {
            System.out.println("DEBUG: getPointPlayer - player, nextToPlayer, me, or inventory is null");
            return 0;
        }
        gun = hero.getInventory().getGun();
        melee = hero.getInventory().getMelee();
        Float myHp = me.getHealth();
        Float playerHp = player.getHealth();
        if (myHp == null || playerHp == null) {
            System.out.println("DEBUG: getPointPlayer - myHp or playerHp is null");
            return 0;
        }
        double stepToKillMe = trackPlayer.getStepToKill(me.getID(), myHp);
        double stepToKillPlayer = (gun != null || melee != null) ? Utils.stepToKill(gun, melee, playerHp) : 123456;
        // Tăng điểm nếu đối thủ trong tầm bắn hoặc máu thấp
        double rangeFactor = 1.0;
        if (gun != null) {
            int diffX = Math.abs(player.getX() - me.getX());
            int diffY = Math.abs(player.getY() - me.getY());
            if ((diffX == 0 && diffY <= gun.getRange()) || (diffY == 0 && diffX <= gun.getRange())) {
                rangeFactor = 2.0; // Ưu tiên đối thủ trong tầm bắn
            }
        }
        double healthFactor = (100.0 - playerHp) / 50.0; // Ưu tiên đối thủ máu thấp
        double factor = Math.min(stepToKillMe / (stepToKillPlayer + 1), 2.0) * rangeFactor * (1 + healthFactor);
        return (int) (100 * (me.getHealth() + 35) * factor / (Utils.distance(nextToPlayer, hero) + 4));
    }

    double getPointWeapon(Weapon weapon) {
        if (weapon == null || hero == null || hero.getInventory() == null) {
            System.out.println("DEBUG: getPointWeapon - weapon or hero or inventory is null");
            return 0;
        }
        gun = hero.getInventory().getGun();
        melee = hero.getInventory().getMelee();
        double pointWeapon = 0;

        if (weapon.getType() == ElementType.THROWABLE) {
            if (hero.getInventory().getThrowable() == null) {
                pointWeapon = weapon.getDamage() * 2; // Tăng điểm cho throwable
            }
        }
        if (weapon.getType() == ElementType.MELEE) {
            double currentDamage = (melee != null && !melee.getId().equals("HAND") && !melee.getId().equals("Null")) ? Utils.getDame(melee) : 0;
            pointWeapon = (weapon.getDamage() - currentDamage) * 4;
        }
        if (weapon.getType() == ElementType.GUN) {
            double currentDamage = gun != null ? Utils.getDame(gun) : 0;
            double rangeBonus = weapon.getRange() * 10; // Ưu tiên súng tầm xa
            double cooldownPenalty = weapon.getCooldown() > 0 ? weapon.getCooldown() * 5 : 0; // Phạt súng cooldown cao
            pointWeapon = ((weapon.getDamage() - currentDamage) * 4 + rangeBonus - cooldownPenalty);
        }
        return (double) (pointWeapon * 100) / (Utils.distance(weapon, hero) + 1);
    }

    double getPointChest(Node chest) {
        if (chest == null || hero == null || hero.getInventory() == null) {
            System.out.println("DEBUG: getPointChest - chest or hero or inventory is null");
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