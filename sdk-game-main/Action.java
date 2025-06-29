import jsclub.codefest.sdk.Hero;

public class Action {
    Hero hero;

    public void setHero(Hero hero) {
        this.hero = hero;
    }

    void pickupItem() {
        System.out.println("Pickup item");
        try {
            hero.pickupItem();
        } catch (Exception ignore) {
        }
    }

    void attack(String x) {
        System.out.println("Attack");
        try {
            hero.attack(x);
        } catch (Exception ignore) {
        }
    }

    void shoot(String x) {
        System.out.println("Shoot");
        try {
            hero.shoot(x);
        } catch (Exception ignore) {
        }
    }

    void move(String x) {
        if (x == "") return;
        System.out.println("Move");
        try {
            hero.move(x);
        } catch (Exception ignore) {
        }
    }

    void useItem(String x) {
        System.out.println("Use item " + x);
        try {
            hero.useItem(x);
        } catch (Exception ignore) {
        }
    }

    void throwAttack(String x, int distance) {
        System.out.println("Throw attack");
        try {
            hero.throwItem(x, distance);
        } catch (Exception ignore) {
        }
    }

    void revokeItem(String x) {
        System.out.println("Revoke item " + x);
        try {
            hero.revokeItem(x);
        } catch (Exception ignore) {
        }
    }
}
