import io.socket.emitter.Emitter;
import jsclub.codefest.sdk.Hero;
import jsclub.codefest.sdk.algorithm.PathUtils;
import jsclub.codefest.sdk.base.Node;
import jsclub.codefest.sdk.model.GameMap;
import jsclub.codefest.sdk.model.Inventory;
import jsclub.codefest.sdk.model.equipments.Armor;
import jsclub.codefest.sdk.model.equipments.HealingItem;
import jsclub.codefest.sdk.model.obstacles.Obstacle;
import jsclub.codefest.sdk.model.players.Player;
import jsclub.codefest.sdk.model.npcs.Ally;
import jsclub.codefest.sdk.model.weapon.Weapon;

import java.io.IOException;
import java.util.*;

public class Main {
    private static final String SERVER_URL = "https://cf25-server.jsclub.dev";
    private static final String GAME_ID = "182411";
    private static final String PLAYER_NAME = "CongChuaBuoiTo";
    private static final String PLAYER_KEY = "sk-5VTDWaBiRSqa2fTy2ZExNw:yj02fPcOBJV30UkGtIdRqmHuvbmpHdrQ-JTsXLyh_QuUZEcvh1OmXjccpXyq-qPUIFMOb8de4mLjt9-S9GH8Fh2tA";
    private static final List<Node> DIRECTIONS = Arrays.asList(new Node(0, 1), new Node(0, -1), new Node(1, 0),
            new Node(-1, 0));
    private static final List<Node> DIRECTIONS_REVERSE = Arrays.asList(new Node(0, -1), new Node(0, 1), new Node(-1, 0),
            new Node(1, 0));
    private static final List<String> DIRECTIONS_STR = Arrays.asList("u", "d", "r", "l");
    private static final List<Node> DIFF_NODE_THROW = Arrays.asList(new Node(0, 6), new Node(0, -6), new Node(6, 0),
            new Node(-6, 0));

    public static void main(String[] args) throws IOException {
        Hero hero = new Hero(GAME_ID, PLAYER_NAME, PLAYER_KEY);
        Emitter.Listener onMapUpdate = new Emitter.Listener() {
            GameMap gameMap;
            Player me;
            List<List<Integer>> g, trace;
            List<Node> restrictedNodes, restrictedNodesWithoutPlayers;
            List<Player> otherPlayers;
            int time = -1, previousDarkSide = 0;
            final EnemyMap enemyMap = new EnemyMap();
            final TrackPlayer trackPlayer = new TrackPlayer();
            Inventory inventory;
            final Action action = new Action();
            boolean haveGun = false;
            boolean haveMelee = false;
            boolean haveThrow = false;
            List<HealingItem> healingItems = new ArrayList<>();
            final PointFactory pointFactory = new PointFactory(null, null);
            double stepHealing = 0;
            double gunCooldown = 0;
            double meleeCooldown = 0;

            void init() {
                time += 1;
                if (hero == null || action == null) {
                    System.out.println("DEBUG: Hero or Action is null");
                    return;
                }
                action.setHero(hero);
                inventory = hero.getInventory();
                if (inventory == null) {
                    haveGun = false;
                    haveMelee = false;
                    haveThrow = false;
                    healingItems = new ArrayList<>();
                    System.out.println("DEBUG: Inventory is null");
                    return;
                }
                haveGun = inventory.getGun() != null;
                haveMelee = inventory.getMelee() != null && !inventory.getMelee().getId().equals("Null");
                haveThrow = inventory.getThrowable() != null;
                healingItems = inventory.getListHealingItem() != null ? inventory.getListHealingItem() : new ArrayList<>();
                gunCooldown = inventory.getGun() != null ? inventory.getGun().getCooldown() : 0;
                meleeCooldown = inventory.getMelee() != null && !inventory.getMelee().getId().equals("Null") ? inventory.getMelee().getCooldown() : 0;
                pointFactory.setHero(hero);
                if (time == 0) {
                    trackPlayer.init(gameMap.getOtherPlayerInfo() != null ? gameMap.getOtherPlayerInfo() : new ArrayList<>());
                }
                int currentSafeZone = gameMap.getSafeZone();
                if (currentSafeZone != previousDarkSide) {
                    gameMap.setSafeZone(currentSafeZone + 1);
                }
                previousDarkSide = currentSafeZone;
                trackPlayer.update(gameMap);
                me = gameMap.getCurrentPlayer();
                if (me == null) {
                    System.out.println("DEBUG: Current player is null");
                    return;
                }
                restrictedNodesWithoutPlayers = new ArrayList<>();
                restrictedNodes = new ArrayList<>();
                otherPlayers = new ArrayList<>();
                if (gameMap.getOtherPlayerInfo() != null) {
                    for (Player p : gameMap.getOtherPlayerInfo()) {
                        if (p != null && p.getHealth() > 0) {
                            otherPlayers.add(p);
                        }
                    }
                }
                enemyMap.calcEnemy(gameMap, time);
                for (int i = 0; i < gameMap.getMapSize(); ++i) {
                    for (int j = 0; j < gameMap.getMapSize(); ++j) {
                        if (enemyMap.isBlock(time + 1, new Node(i, j), gameMap)) {
                            restrictedNodesWithoutPlayers.add(new Node(i, j));
                        }
                    }
                }
                List<Obstacle> listConstruct = new ArrayList<>();
                if (gameMap.getListTraps() != null) listConstruct.addAll(gameMap.getListTraps());
                if (gameMap.getListIndestructibles() != null) listConstruct.addAll(gameMap.getListIndestructibles());
                if (gameMap.getListChests() != null) listConstruct.addAll(gameMap.getListChests());
                for (Node p : listConstruct) {
                    if (p != null) {
                        restrictedNodes.add(new Node(p.getX(), p.getY()));
                    }
                }
                restrictedNodesWithoutPlayers.addAll(restrictedNodes);
                for (Player p : otherPlayers) {
                    if (p == null) continue;
                    restrictedNodes.add(p);
                    int indexPlayer = trackPlayer.getIndexByName(p.getID());
                    if (trackPlayer.playerMelees.get(indexPlayer) == null && trackPlayer.playerGuns.get(indexPlayer) == null) {
                        continue;
                    }
                    for (int i = 0; i < 4; ++i) {
                        Node nearPlayer = new Node(p.getX(), p.getY());
                        for (int j = 0; j < 3; ++j) {
                            nearPlayer = Utils.add(nearPlayer, DIRECTIONS.get(i));
                            restrictedNodes.add(nearPlayer);
                            if (trackPlayer.playerGuns.get(indexPlayer) == null) break;
                        }
                    }
                }
            }

            void bfs() {
                int mapSize = gameMap.getMapSize();
                if (mapSize <= 0) {
                    System.out.println("DEBUG: Map size is invalid: " + mapSize);
                    return;
                }
                g = Utils.initializeList(mapSize, 99999999);
                List<List<Boolean>> isRestrictedNodes = Utils.initializeList(mapSize, false);
                trace = Utils.initializeList(mapSize, -1);
                for (Node point : restrictedNodes) {
                    if (Utils.isValid(point, gameMap)) {
                        isRestrictedNodes.get(point.x).set(point.y, true);
                    }
                }
                Queue<Node> queue = new LinkedList<>();
                queue.add(me);
                g.get(me.getX()).set(me.getY(), 0);
                while (!queue.isEmpty()) {
                    Node u = queue.poll();
                    for (int dir = 0; dir < 4; ++dir) {
                        Node v = Utils.add(u, DIRECTIONS.get(dir));
                        if (!Utils.isValid(v, gameMap))
                            continue;
                        int cost = g.get(u.x).get(u.y) + 1;
                        if (Utils.isInsideSafeArea(me, gameMap)) {
                            if (enemyMap.isBlock(time + cost, v, gameMap)
                                    || !Utils.isInsideSafeArea(v, gameMap))
                                continue;
                        }
                        if (isRestrictedNodes.get(v.x).get(v.y)) {
                            continue;
                        }
                        if (g.get(v.x).get(v.y) > cost) {
                            g.get(v.x).set(v.y, cost);
                            trace.get(v.x).set(v.y, dir);
                            queue.add(v);
                        }
                    }
                }
            }

            int distance(Node p) {
                if (p == null || !Utils.isValid(p, gameMap)) {
                    return 222222222;
                }
                return g.get(p.x).get(p.y);
            }

            boolean trapInMid(Node x, Node y, List<Obstacle> listTraps, List<Obstacle> listChest) {
                if (listTraps != null) {
                    for (Obstacle mid : listTraps) {
                        if (mid != null && Utils.distance(x, mid, gameMap) + Utils.distance(mid, y, gameMap) <= Utils.distance(x, y, gameMap)) {
                            return true;
                        }
                    }
                }
                return false;
            }

            String getPath(Node target) {
                if (Utils.equal(target, me)) {
                    return "";
                }
                while (true) {
                    int dir = trace.get(target.x).get(target.y);
                    String stringDir = DIRECTIONS_STR.get(dir);
                    target = Utils.add(target, DIRECTIONS_REVERSE.get(dir));
                    if (Utils.equal(target, me)) {
                        return stringDir;
                    }
                }
            }

            void mAttack(Node target) {
                if (me.getX() + 1 == target.getX() && me.getY() == target.getY()) {
                    action.attack("r");
                } else if (me.getX() - 1 == target.getX() && me.getY() == target.getY()) {
                    action.attack("l");
                } else if (me.getY() + 1 == target.getY() && me.getX() == target.getX()) {
                    action.attack("u");
                } else if (me.getY() - 1 == target.getY() && me.getX() == target.getX()) {
                    action.attack("d");
                }
            }

            boolean tryHealth() {
                if (healingItems.isEmpty()) {
                    return false;
                }
                double timeToReach = Integer.MAX_VALUE;
                double maxDame = 0;
                Player nearestEnemy = null;
                if (!otherPlayers.isEmpty()) {
                    nearestEnemy = otherPlayers.getFirst();
                    for (Player p : otherPlayers) {
                        if (Utils.distance(p, me, gameMap) < Utils.distance(nearestEnemy, me, gameMap)) {
                            nearestEnemy = p;
                        }
                    }
                    int diffX = Math.abs(nearestEnemy.getX() - me.getX());
                    int diffY = Math.abs(nearestEnemy.getY() - me.getY());
                    int indexPlayer = trackPlayer.getIndexByName(nearestEnemy.getID());
                    Weapon playerMelee = trackPlayer.playerMelees.get(indexPlayer);
                    Weapon playerGun = trackPlayer.playerGuns.get(indexPlayer);
                    if (playerMelee != null) {
                        timeToReach = diffX + diffY - 1;
                    }
                    if (playerGun != null) {
                        timeToReach = Math.min(diffX, diffY) + Math.max(0, Math.max

                                (diffX, diffY) - 3);
                    }
                    if (timeToReach <= 4) {
                        maxDame = Utils.getDame(playerGun) + Utils.getDame(playerMelee);
                    }
                }
                if (me.getHealth() <= 50 || (nearestEnemy != null && me.getHealth() <= maxDame)) {
                    double maxTimeUsage = timeToReach > 1 ? Math.min(timeToReach, 4) : 0;
                    healingItems.sort((a, b) -> b.getHealingHP() - a.getHealingHP());
                    for (HealingItem item : healingItems) {
                        if (me.getHealth() + item.getHealingHP() <= 100 && maxTimeUsage >= item.getUsageTime()) {
                            action.useItem(item.getId());
                            stepHealing = item.getUsageTime();
                            return true;
                        }
                    }
                    if (me.getHealth() <= maxDame) {
                        HealingItem item = healingItems.getLast();
                        if (maxTimeUsage >= item.getUsageTime()) {
                            action.useItem(item.getId());
                            stepHealing = item.getUsageTime();
                            return true;
                        }
                    }
                }
                return false;
            }

            boolean contains(List<Node> list, Node node) {
                for (Node p : list) {
                    if (Utils.equal(p, node))
                        return true;
                }
                return false;
            }

            <T extends Node> T nearestNode(List<T> nodes) {
                if (nodes == null || nodes.isEmpty()) {
                    return null;
                }
                T nearestNode = nodes.getFirst();
                for (T node : nodes) {
                    if (distance(node) < distance(nearestNode)) {
                        nearestNode = node;
                    }
                }
                return nearestNode;
            }

            void getChest(Node nextToChest) {
                if (nextToChest == null) {
                    return;
                }
                if (Utils.equal(nextToChest, me)) {
                    for (Node p : gameMap.getListChests()) {
                        if (Utils.distance(p, me, gameMap) == 1) {
                            if (haveMelee && inventory != null && inventory.getMelee() != null && !inventory.getMelee().getId().equals("Null")) {
                                if (me.getX() + 1 == p.getX()) {
                                    action.attack("r");
                                } else if (me.getX() - 1 == p.getX()) {
                                    action.attack("l");
                                } else if (me.getY() + 1 == p.getY()) {
                                    action.attack("u");
                                } else if (me.getY() - 1 == p.getY()) {
                                    action.attack("d");
                                }
                            } else {
                                mAttack(p);
                            }
                            return;
                        }
                    }
                } else {
                    action.move(getPath(nextToChest));
                }
            }

            void getItem(Node target) {
                if (target == null) {
                    return;
                }
                if (Utils.equal(me, target)) {
                    action.pickupItem();
                } else {
                    action.move(getPath(target));
                }
            }

            void getWeapon(Weapon weapon, int type) {
                if (weapon == null || me == null || inventory == null) {
                    return;
                }
                if (Utils.equal(weapon, me)) {
                    String currentWeaponId = null;
                    Weapon currentWeapon = null;
                    if (type == 4) { // Vũ khí cận chiến
                        currentWeapon = inventory.getMelee();
                        currentWeaponId = currentWeapon != null && !currentWeapon.getId().equals("Null") ? currentWeapon.getId() : null;
                        if (haveMelee && currentWeaponId != null && !currentWeaponId.equals("HAND")) {
                            if (pointFactory.getPointWeapon(weapon) > pointFactory.getPointWeapon(currentWeapon) && !weapon.getId().equals(currentWeaponId)) {
                                action.revokeItem(currentWeaponId);
                                action.pickupItem();
                                haveMelee = false;
                            }
                        } else {
                            action.pickupItem();
                            haveMelee = true;
                        }
                    } else if (type == 5) { // Súng
                        currentWeapon = inventory.getGun();
                        currentWeaponId = currentWeapon != null ? currentWeapon.getId() : null;
                        if (haveGun && currentWeaponId != null) {
                            if (pointFactory.getPointWeapon(weapon) > pointFactory.getPointWeapon(currentWeapon) && !weapon.getId().equals(currentWeaponId)) {
                                action.revokeItem(currentWeaponId);
                                action.pickupItem();
                                haveGun = false;
                            }
                        } else {
                            action.pickupItem();
                            haveGun = true;
                        }
                    } else if (type == 6) { // Vũ khí ném
                        currentWeapon = inventory.getThrowable();
                        currentWeaponId = currentWeapon != null ? currentWeapon.getId() : null;
                        if (haveThrow && currentWeaponId != null) {
                            if (pointFactory.getPointWeapon(weapon) > pointFactory.getPointWeapon(currentWeapon) && !weapon.getId().equals(currentWeaponId)) {
                                action.revokeItem(currentWeaponId);
                                action.pickupItem();
                                haveThrow = false;
                            }
                        } else {
                            action.pickupItem();
                            haveThrow = true;
                        }
                    }
                } else {
                    action.move(getPath(weapon));
                }
            }

            void getArmor(Armor armor) {
                if (armor == null) {
                    return;
                }
                getItem(armor);
            }

            void calculateOptimizedMove() {
                if (gameMap.getMapSize() <= 0) {
                    List<Node> itemsAtCurrentPos = new ArrayList<>();
                    if (gameMap.getListHealingItems() != null && !gameMap.getListHealingItems().isEmpty()) {
                        for (HealingItem item : gameMap.getListHealingItems()) {
                            if (Utils.equal(me, item)) itemsAtCurrentPos.add(item);
                        }
                    }
                    if (gameMap.getListArmors() != null && !gameMap.getListArmors().isEmpty()) {
                        for (Armor item : gameMap.getListArmors()) {
                            if (Utils.equal(me, item)) itemsAtCurrentPos.add(item);
                        }
                    }
                    if (gameMap.getAllMelee() != null && !gameMap.getAllMelee().isEmpty()) {
                        for (Weapon item : gameMap.getAllMelee()) {
                            if (Utils.equal(me, item)) itemsAtCurrentPos.add(item);
                        }
                    }
                    if (gameMap.getAllGun() != null && !gameMap.getAllGun().isEmpty()) {
                        for (Weapon item : gameMap.getAllGun()) {
                            if (Utils.equal(me, item)) itemsAtCurrentPos.add(item);
                        }
                    }
                    if (gameMap.getAllThrowable() != null && !gameMap.getAllThrowable().isEmpty()) {
                        for (Weapon item : gameMap.getAllThrowable()) {
                            if (Utils.equal(me, item)) itemsAtCurrentPos.add(item);
                        }
                    }
                    if (!itemsAtCurrentPos.isEmpty()) {
                        action.pickupItem();
                        return;
                    }
                    Random rand = new Random();
                    int dir = rand.nextInt(4);
                    action.move(DIRECTIONS_STR.get(dir));
                    return;
                }

                List<Node> nextToChest = new ArrayList<>();
                for (Obstacle p : gameMap.getListChests()) {
                    if (p != null && Utils.isInsideSafeArea(p, gameMap)) {
                        for (int i = 0; i < 4; ++i) {
                            Node next = Utils.add(p, DIRECTIONS.get(i));
                            if (Utils.isValid(next, gameMap) && !contains(restrictedNodes, next)) {
                                nextToChest.add(next);
                            }
                        }
                    }
                }
                Node nearestNextToChest = nearestNode(nextToChest);
                Player nearestPlayer = null;
                Node nearestNextToPlayer = null;
                double minPlayerDistance = Double.MAX_VALUE;
                int gunRange = haveGun && inventory.getGun() != null ? inventory.getGun().getRange() : 1;
                // Ưu tiên đối thủ có máu thấp
                for (Player p : otherPlayers) {
                    if (p == null || p.getHealth() <= 0) continue;
                    for (int dx = -gunRange; dx <= gunRange; dx++) {
                        for (int dy = -gunRange; dy <= gunRange; dy++) {
                            if ((dx == 0 || dy == 0) && (dx != 0 || dy != 0)) {
                                Node addNode = new Node(p.getX() + dx, p.getY() + dy);
                                if (Utils.isValid(addNode, gameMap) && !contains(restrictedNodes, addNode)) {
                                    int dist = distance(addNode);
                                    // Ưu tiên đối thủ máu thấp bằng cách giảm khoảng cách hiệu quả
                                    double effectiveDistance = dist / (1.0 + (100 - p.getHealth()) / 100.0);
                                    if (effectiveDistance < minPlayerDistance) {
                                        minPlayerDistance = effectiveDistance;
                                        nearestNextToPlayer = addNode;
                                        nearestPlayer = p;
                                    }
                                }
                            }
                        }
                    }
                }
                Ally nearestAlly = null;
                Node nearestNextToAlly = null;
                double minAllyDistance = Double.MAX_VALUE;
                List<Ally> allies = gameMap.getListAllies() != null ? gameMap.getListAllies() : new ArrayList<>();
                for (Ally ally : allies) {
                    if (ally != null && Utils.isInsideSafeArea(ally, gameMap)) {
                        for (int i = 0; i < 4; ++i) {
                            Node addNode = Utils.add(new Node(ally.getX(), ally.getY()), DIRECTIONS.get(i));
                            if (Utils.isValid(addNode, gameMap) && distance(addNode) < minAllyDistance) {
                                minAllyDistance = distance(addNode);
                                nearestNextToAlly = addNode;
                                nearestAlly = ally;
                            }
                        }
                    }
                }
                HealingItem nearestHealth = nearestNode(gameMap.getListHealingItems());
                Armor nearestArmor = nearestNode(gameMap.getListArmors());
                Weapon nearestMelee = nearestNode(gameMap.getAllMelee());
                Weapon nearestGun = nearestNode(gameMap.getAllGun());
                Weapon nearestThrow = nearestNode(gameMap.getAllThrowable());

                List<Double> pointItems = new ArrayList<>();
                List<Node> target = new ArrayList<>();
                List<Integer> targetTypes = new ArrayList<>();

                String currentArmorId = inventory != null && inventory.getArmor() != null ? inventory.getArmor().getId() : null;
                String currentMeleeId = inventory != null && inventory.getMelee() != null && !inventory.getMelee().getId().equals("Null") ? inventory.getMelee().getId() : null;
                String currentGunId = inventory != null && inventory.getGun() != null ? inventory.getGun().getId() : null;
                String currentThrowId = inventory != null && inventory.getThrowable() != null ? inventory.getThrowable().getId() : null;

                boolean hasFullGear = inventory != null &&
                        inventory.getArmor() != null &&
                        inventory.getMelee() != null && !inventory.getMelee().getId().equals("Null") &&
                        inventory.getGun() != null &&
                        inventory.getThrowable() != null;

                double chestDistance = nearestNextToChest != null ? distance(nearestNextToChest) : Double.MAX_VALUE;
                double playerDistance = nearestNextToPlayer != null ? distance(nearestNextToPlayer) : Double.MAX_VALUE;
                double allyDistance = nearestNextToAlly != null ? distance(nearestNextToAlly) : Double.MAX_VALUE;

                if (hasFullGear) {
                    if (playerDistance <= chestDistance && nearestPlayer != null && nearestNextToPlayer != null) {
                        pointItems.add(Double.NEGATIVE_INFINITY);
                        pointItems.add(Double.POSITIVE_INFINITY);
                    } else {
                        pointItems.add(nearestNextToChest != null ? pointFactory.getPointChest(nearestNextToChest) : Double.NEGATIVE_INFINITY);
                        pointItems.add(nearestPlayer != null && nearestNextToPlayer != null ? pointFactory.getPointPlayer(nearestPlayer, nearestNextToPlayer) : Double.NEGATIVE_INFINITY);
                    }
                } else {
                    if (chestDistance <= playerDistance) {
                        pointItems.add(nearestNextToChest != null ? pointFactory.getPointChest(nearestNextToChest) : Double.NEGATIVE_INFINITY);
                        pointItems.add(nearestPlayer != null && nearestNextToPlayer != null ? pointFactory.getPointPlayer(nearestPlayer, nearestNextToPlayer) : Double.NEGATIVE_INFINITY);
                    } else {
                        pointItems.add(Double.NEGATIVE_INFINITY);
                        pointItems.add(nearestPlayer != null && nearestNextToPlayer != null ? pointFactory.getPointPlayer(nearestPlayer, nearestNextToPlayer) : Double.NEGATIVE_INFINITY);
                    }
                }
                pointItems.add((me.getHealth() <= 50 && healingItems.isEmpty() && nearestAlly != null && nearestNextToAlly != null) ?
                        Double.POSITIVE_INFINITY :
                        (nearestAlly != null && nearestNextToAlly != null ? -minAllyDistance : Double.NEGATIVE_INFINITY));
                target.add(nearestNextToChest);
                target.add(nearestNextToPlayer);
                target.add(nearestNextToAlly);
                targetTypes.add(0);
                targetTypes.add(1);
                targetTypes.add(2);
                pointItems.add(nearestHealth != null ? pointFactory.getPointHealth(nearestHealth) : Double.NEGATIVE_INFINITY);
                target.add(nearestHealth);
                targetTypes.add(3);
                pointItems.add(nearestArmor != null && (currentArmorId == null || !currentArmorId.equals(nearestArmor.getId())) ? pointFactory.getPointArmor(nearestArmor) : Double.NEGATIVE_INFINITY);
                target.add(nearestArmor);
                targetTypes.add(4);
                pointItems.add(nearestMelee != null && (currentMeleeId == null || !currentMeleeId.equals(nearestMelee.getId())) ? pointFactory.getPointWeapon(nearestMelee) : Double.NEGATIVE_INFINITY);
                target.add(nearestMelee);
                targetTypes.add(5);
                pointItems.add(nearestGun != null && (currentGunId == null || !currentGunId.equals(nearestGun.getId())) ? pointFactory.getPointWeapon(nearestGun) : Double.NEGATIVE_INFINITY);
                target.add(nearestGun);
                targetTypes.add(6);
                pointItems.add(nearestThrow != null && (currentThrowId == null || !currentThrowId.equals(nearestThrow.getId())) ? pointFactory.getPointWeapon(nearestThrow) : Double.NEGATIVE_INFINITY);
                target.add(nearestThrow);
                targetTypes.add(7);

                List<Node> itemsAtCurrentPos = new ArrayList<>();
                if (nearestHealth != null && Utils.equal(me, nearestHealth)) itemsAtCurrentPos.add(nearestHealth);
                if (nearestArmor != null && Utils.equal(me, nearestArmor) && (currentArmorId == null || !currentArmorId.equals(nearestArmor.getId()))) itemsAtCurrentPos.add(nearestArmor);
                if (nearestMelee != null && Utils.equal(me, nearestMelee) && (currentMeleeId == null || !currentMeleeId.equals(nearestMelee.getId()))) itemsAtCurrentPos.add(nearestMelee);
                if (nearestGun != null && Utils.equal(me, nearestGun) && (currentGunId == null || !currentGunId.equals(nearestGun.getId()))) itemsAtCurrentPos.add(nearestGun);
                if (nearestThrow != null && Utils.equal(me, nearestThrow) && (currentThrowId == null || !currentThrowId.equals(nearestThrow.getId()))) itemsAtCurrentPos.add(nearestThrow);

                if (!itemsAtCurrentPos.isEmpty()) {
                    for (Node item : itemsAtCurrentPos) {
                        if (item == nearestHealth) {
                            action.pickupItem();
                            return;
                        } else if (item == nearestArmor) {
                            getArmor((Armor) item);
                            return;
                        } else if (item == nearestMelee) {
                            getWeapon((Weapon) item, 4);
                            return;
                        } else if (item == nearestGun) {
                            getWeapon((Weapon) item, 5);
                            return;
                        } else if (item == nearestThrow) {
                            getWeapon((Weapon) item, 6);
                            return;
                        }
                    }
                }

                Double maxPoint = Collections.max(pointItems);
                if (maxPoint == Double.NEGATIVE_INFINITY) {
                    Random rand = new Random();
                    int dir = rand.nextInt(4);
                    action.move(DIRECTIONS_STR.get(dir));
                    return;
                }

                for (int i = 0; i < pointItems.size(); ++i) {
                    if (Objects.equals(pointItems.get(i), maxPoint)) {
                        Node targetNode = target.get(i);
                        int targetType = targetTypes.get(i);
                        if (targetNode == null) {
                            continue;
                        }
                        if (targetType == 0) {
                            getChest(targetNode);
                        } else if (targetType == 1 && nearestPlayer != null) {
                            double myHp = me.getHealth() * (100 + pointFactory.getTotalDamageReduce()) / 100;
                            action.move(getPath(targetNode));
                        } else if (targetType == 2 && nearestAlly != null) {
                            action.move(getPath(targetNode));
                        } else if (targetType == 3) {
                            getItem(targetNode);
                        } else if (targetType == 4) {
                            getArmor((Armor) targetNode);
                        } else if (targetType == 5) {
                            getWeapon((Weapon) targetNode, 4);
                        } else if (targetType == 6) {
                            getWeapon((Weapon) targetNode, 5);
                        } else if (targetType == 7) {
                            getWeapon((Weapon) targetNode, 6);
                        }
                        return;
                    }
                }
            }

            @Override
            public void call(Object... args) {
                if (hero == null || args.length == 0) {
                    System.out.println("DEBUG: Hero or args is null");
                    return;
                }
                gameMap = hero.getGameMap();
                if (gameMap == null) {
                    System.out.println("DEBUG: GameMap is null");
                    return;
                }
                gameMap.updateOnUpdateMap(args[0]);
                init();
                if (stepHealing > 0) {
                    stepHealing--;
                    return;
                }
                bfs();
                if (me == null) {
                    System.out.println("DEBUG: Current player is null in call");
                    return;
                }
                if (!Utils.isInsideSafeArea(me, gameMap)) {
                    Node nearest = new Node(-1, -1);
                    for (int i = 0; i < gameMap.getMapSize(); ++i) {
                        for (int j = 0; j < gameMap.getMapSize(); ++j) {
                            Node addNode = new Node(i, j);
                            if (distance(addNode) < distance(nearest) && Utils.isInsideSafeArea(addNode, gameMap)) {
                                nearest = addNode;
                            }
                        }
                    }
                    action.move(getPath(nearest));
                    return;
                }
                // Tấn công cận chiến nếu đối thủ ở sát bên
                if (haveMelee && meleeCooldown <= 0 && inventory != null && inventory.getMelee() != null && !inventory.getMelee().getId().equals("Null")) {
                    for (Player p : otherPlayers) {
                        if (p != null && Utils.distance(me, p, gameMap) == 1) {
                            mAttack(p);
                            meleeCooldown = inventory.getMelee().getCooldown();
                            System.out.println("DEBUG: Melee attack executed on player at [" + p.getX() + "," + p.getY() + "]");
                            return;
                        }
                    }
                }
                // Bắn súng nếu đối thủ trong tầm
                if (haveGun && gunCooldown <= 0 && inventory != null && inventory.getGun() != null) {
                    Weapon gun = inventory.getGun();
                    System.out.println("DEBUG: Gun available, range: " + gun.getRange() + ", cooldown: " + gun.getCooldown());
                    for (Player p : otherPlayers) {
                        if (p == null || p.getHealth() <= 0) {
                            System.out.println("DEBUG: Skipping null or dead player");
                            continue;
                        }
                        int diffX = Math.abs(p.getX() - me.getX());
                        int diffY = Math.abs(p.getY() - me.getY());
                        if (diffX == 0 && diffY <= gun.getRange()) {
                            if (trapInMid(me, p, gameMap.getListTraps(), gameMap.getListChests())) {
                                System.out.println("DEBUG: Cannot shoot player at [" + p.getX() + "," + p.getY() + "] due to trap in the way");
                                continue;
                            }
                            String direction = p.getY() < me.getY() ? "d" : "u";
                            try {
                                action.shoot(direction);
                                gunCooldown = gun.getCooldown();
                                System.out.println("DEBUG: Shooting player at [" + p.getX() + "," + p.getY() + "] in direction " + direction);
                                return;
                            } catch (Exception e) {
                                System.out.println("DEBUG: Failed to shoot: " + e.getMessage());
                            }
                        }
                        if (diffY == 0 && diffX <= gun.getRange()) {
                            if (trapInMid(me, p, gameMap.getListTraps(), gameMap.getListChests())) {
                                System.out.println("DEBUG: Cannot shoot player at [" + p.getX() + "," + p.getY() + "] due to trap in the way");
                                continue;
                            }
                            String direction = p.getX() < me.getX() ? "l" : "r";
                            try {
                                action.shoot(direction);
                                gunCooldown = gun.getCooldown();
                                System.out.println("DEBUG: Shooting player at [" + p.getX() + "," + p.getY() + "] in direction " + direction);
                                return;
                            } catch (Exception e) {
                                System.out.println("DEBUG: Failed to shoot: " + e.getMessage());
                            }
                        }
                    }
                } else {
                    System.out.println("DEBUG: Cannot shoot - haveGun: " + haveGun + ", gunCooldown: " + gunCooldown + ", inventory.getGun(): " + (inventory != null ? inventory.getGun() : null));
                }
                // Ném vật phẩm nếu đối thủ trong tầm
                if (inventory != null && inventory.getThrowable() != null && me != null && gameMap != null && action != null && otherPlayers != null) {
                    Weapon throwable = inventory.getThrowable();
                    if (throwable.getCooldown() <= 0) {
                        int throwDistance = 6;
                        List<Node> targetThrow = new ArrayList<>();
                        for (int i = 0; i < 4; ++i) {
                            Node target = Utils.add(DIFF_NODE_THROW.get(i), me);
                            if (Utils.isValid(target, gameMap)) {
                                targetThrow.add(target);
                            }
                        }
                        Player nearestPlayer = null;
                        int nearestDirectionIndex = -1;
                        double minDistance = Double.MAX_VALUE;
                        for (Player p : otherPlayers) {
                            if (p == null || p.getHealth() <= 0) continue;
                            for (int i = 0; i < targetThrow.size(); ++i) {
                                double distance = Utils.distance(targetThrow.get(i), p, gameMap);
                                if (distance <= 1 && distance < minDistance) {
                                    minDistance = distance;
                                    nearestPlayer = p;
                                    nearestDirectionIndex = i;
                                }
                            }
                        }
                        if (nearestPlayer != null && nearestDirectionIndex != -1) {
                            try {
                                action.throwAttack(DIRECTIONS_STR.get(nearestDirectionIndex), throwDistance);
                                System.out.println("DEBUG: Throwing at player at [" + nearestPlayer.getX() + "," + nearestPlayer.getY() + "] in direction " + DIRECTIONS_STR.get(nearestDirectionIndex));
                                return;
                            } catch (Exception e) {
                                System.out.println("DEBUG: Failed to throwAttack: " + e.getMessage());
                            }
                        }
                    } else {
                        System.out.println("DEBUG: Throwable on cooldown: " + throwable.getCooldown());
                    }
                }
                // Di chuyển đến vị trí trong tầm bắn hoặc sát đối thủ
                if (haveGun || haveMelee) {
                    for (Player p : otherPlayers) {
                        if (p == null || p.getHealth() <= 0) continue;
                        int gunRange = haveGun && inventory.getGun() != null ? inventory.getGun().getRange() : 1;
                        for (int dx = -gunRange; dx <= gunRange; dx++) {
                            for (int dy = -gunRange; dy <= gunRange; dy++) {
                                if ((dx == 0 || dy == 0) && (dx != 0 || dy != 0)) {
                                    Node addNode = new Node(p.getX() + dx, p.getY() + dy);
                                    if (Utils.isValid(addNode, gameMap) && !contains(restrictedNodes, addNode)) {
                                        action.move(getPath(addNode));
                                        System.out.println("DEBUG: Moving to [" + addNode.x + "," + addNode.y + "] to attack player at [" + p.getX() + "," + p.getY() + "]");
                                        return;
                                    }
                                }
                            }
                        }
                    }
                }
                if (tryHealth()) {
                    return;
                }
                calculateOptimizedMove();
            }
        };

        hero.setOnMapUpdate(onMapUpdate);
        hero.start(SERVER_URL);
    }
}