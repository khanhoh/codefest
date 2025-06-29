
import jsclub.codefest.sdk.Hero;
import jsclub.codefest.sdk.algorithm.PathUtils;
import jsclub.codefest.sdk.base.Node;
import jsclub.codefest.sdk.model.GameMap;
import jsclub.codefest.sdk.model.weapon.Weapon;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;



public class Utils {
    static <T> List<List<T>> initializeList(int size, T value) {
        List<List<T>> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            result.add(new ArrayList<>(Collections.nCopies(size, value)));
        }
        return result;
    }

    static <T extends Node> boolean equal(T x, T y) {
        return x.getX() == y.getX() && x.getY() == y.getY();
    }

    static Node add(Node x, Node y) {
        return new Node(x.getX() + y.getX(), x.getY() + y.getY());
    }

    static Node multiply(Node x, int time) {
        Node ans = new Node(0, 0);
        for (int i = 1; i <= time; i++) {
            ans = add(ans, x);
        }
        return ans;
    }

    static boolean isValid(Node p, GameMap gameMap) {

        int mapSize = gameMap.getMapSize();
        return p.getX() >= 0 && p.getX() < mapSize && p.getY() >= 0 && p.getY() < mapSize;
    }

    static int distance(Node p1, Hero hero) {
        return distance(p1, hero.getGameMap().getCurrentPlayer(), hero.getGameMap());
    }

    static int distance(Node p1, Node p2, GameMap gameMap) {
        if (p1 == null || p2 == null || !isInsideSafeArea(p1, gameMap) || !isInsideSafeArea(p2, gameMap)) {
            return 123456;
        }
        return Math.abs(p1.getX() - p2.getX()) + Math.abs(p1.getY() - p2.getY());
    }

    static boolean isInsideSafeArea(Node p, GameMap gameMap) {
        return PathUtils.checkInsideSafeArea(p, gameMap.getSafeZone(), gameMap.getMapSize());
    }

    static int getDame(Weapon weapon) {
        if (weapon == null)
            return 0;
        return weapon.getDamage();
    }

    static double stepToKill(Weapon gun, Weapon melee, Float health) {
        if (gun == null && melee == null)
            return 123456;
        if (gun == null)
            return  (health + melee.getDamage() - 1) / melee.getDamage() * melee.getCooldown()
                                - (melee.getCooldown() - 1);
        if (melee == null)
            return (health + gun.getDamage() - 1) / gun.getDamage() * gun.getCooldown() - (gun.getCooldown() - 1);
        double diffCooldown = melee.getCooldown() - gun.getCooldown();
        health -= melee.getDamage();
        if (health <= 0)
            return 1;
        health -= gun.getDamage();
        if (health <= 0)
            return 2;
        health -= gun.getDamage();
        if (health <= 0)
            return 2 + gun.getCooldown();
        health -= melee.getDamage();
        return 1 + gun.getCooldown() + diffCooldown;
    }
}
