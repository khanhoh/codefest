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
import jsclub.codefest.sdk.model.weapon.Weapon;

import java.io.IOException;
import java.util.*;

public class Main {
    private static final String SERVER_URL = "https://cf25-server.jsclub.dev";
    private static final String GAME_ID = "130996";
    private static final String PLAYER_NAME = "CongChuaBuoiTo";
    private static final String PLAYER_KEY = "sk-5VTDWaBiRSqa2fTy2ZExNw:yj02fPcOBJV30UkGtIdRqmHuvbmpHdrQ-JTsXLyh_QuUZEcvh1OmXjccpXyq-qPUIFMOb8de4mLjt9-S9GH8Fh2tA";
    private static final List<Node> DIRECTIONS = Arrays.asList(new Node(0, 1), new Node(0, -1), new Node(1, 0),
            new Node(-1, 0));
    private static final List<Node> DIRECTIONS_REVERSE = Arrays.asList(new Node(0, -1), new Node(0, 1), new Node(-1, 0),
            new Node(1, 0));
    private static final List<String> DIRECTIONS_STR = Arrays.asList("u", "d", "r", "l");
    private static final List<Node> DIRECTIONS2 = Arrays.asList(new Node(1, 1), new Node(1, -1), new Node(-1, -1),
            new Node(-1, 1));
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
                    return;
                }
                action.setHero(hero);
                inventory = hero.getInventory();
                if (inventory == null) {
                    haveGun = false;
                    haveMelee = false;
                    haveThrow = false;
                    healingItems = new ArrayList<>();
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
                if (listChest != null) {
                    for (Obstacle mid : listChest) {
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
                if (me.getX() + 1 == target.getX()) {
                    action.attack("r");
                }
                if (me.getX() - 1 == target.getX()) {
                    action.attack("l");
                }
                if (me.getY() + 1 == target.getY()) {
                    action.attack("u");
                }
                if (me.getY() - 1 == target.getY()) {
                    action.attack("d");
                }
            }

            boolean tryHealth() {
                if (healingItems.isEmpty()) {
                    return false;
                }
                double timeToReach = Integer.MAX_VALUE;
                double maxDame = 0;
                if (!otherPlayers.isEmpty()) {
                    Player nearestPlayerReal = otherPlayers.getFirst();
                    for (Player p : otherPlayers) {
                        if (Utils.distance(p, me, gameMap) < Utils.distance(nearestPlayerReal, me, gameMap)) {
                            nearestPlayerReal = p;
                        }
                    }
                    int diffX = Math.abs(nearestPlayerReal.getX() - me.getX());
                    int diffY = Math.abs(nearestPlayerReal.getY() - me.getY());
                    int indexPlayer = trackPlayer.getIndexByName(nearestPlayerReal.getID());
                    Weapon playerMelee = trackPlayer.playerMelees.get(indexPlayer);
                    Weapon playerGun = trackPlayer.playerGuns.get(indexPlayer);
                    if (playerMelee != null) {
                        timeToReach = diffX + diffY - 1;
                    }
                    if (playerGun != null) {
                        timeToReach = Math.min(diffX, diffY) + Math.max(0, Math.max(diffX, diffY) - 3);
                    }
                    if (timeToReach <= 4) {
                        maxDame = Utils.getDame(playerGun) + Utils.getDame(playerMelee);
                    }
                }
                double maxTimeUsage = timeToReach;
                if (haveGun && haveMelee) {
                    maxTimeUsage = Math.max(maxTimeUsage, Math.min(gunCooldown, meleeCooldown));
                }
                if (haveGun && !haveMelee) {
                    maxTimeUsage = Math.max(maxTimeUsage, gunCooldown);
                }
                if (!haveGun && haveMelee) {
                    maxTimeUsage = Math.max(maxTimeUsage, meleeCooldown);
                }
                if (timeToReach > 1) {
                    int maxTimeSafe = 0;
                    for (int i = 1; i <= 4; ++i) {
                        if (enemyMap.isBlock(time + i, me, gameMap))
                            break;
                        maxTimeSafe = i;
                    }
                    maxTimeUsage = Math.min(maxTimeSafe, timeToReach);
                }
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
                        action.useItem(healingItems.getLast().getId());
                        return true;
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
                            mAttack(p);
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
                    if (type == 4) { // Vũ khí cận chiến
                        if (haveMelee && inventory.getMelee() != null && !inventory.getMelee().getId().equals("Null") && !inventory.getMelee().getId().equals("HAND")) {
                            if (pointFactory.getPointWeapon(weapon) > pointFactory.getPointWeapon(inventory.getMelee())) {
                                action.revokeItem(inventory.getMelee().getId());
                                action.pickupItem();
                            }
                        } else {
                            action.pickupItem();
                        }
                    } else if (type == 5) { // Súng
                        if (haveGun && inventory.getGun() != null) {
                            if (pointFactory.getPointWeapon(weapon) > pointFactory.getPointWeapon(inventory.getGun())) {
                                action.revokeItem(inventory.getGun().getId());
                                action.pickupItem();
                            }
                        } else {
                            action.pickupItem();
                        }
                    } else {
                        action.pickupItem();
                    }
                } else {
                    getItem(weapon);
                }
            }

            void getArmor(Armor armor) {
                if (armor == null) {
                    return;
                }
                getItem(armor);
            }

            void calculateOptimizedMove() {
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
                for (Player p : otherPlayers) {
                    if (p != null && Utils.isInsideSafeArea(p, gameMap)) {
                        for (int i = 0; i < 4; ++i) {
                            Node addNode = Utils.add(p, DIRECTIONS2.get(i));
                            if (Utils.isValid(addNode, gameMap) && distance(addNode) < minPlayerDistance) {
                                minPlayerDistance = distance(addNode);
                                nearestNextToPlayer = addNode;
                                nearestPlayer = p;
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

                // Kiểm tra trùng lặp vật phẩm
                String currentArmorId = inventory != null && inventory.getArmor() != null ? inventory.getArmor().getId() : null;
                String currentMeleeId = inventory != null && inventory.getMelee() != null && !inventory.getMelee().getId().equals("Null") ? inventory.getMelee().getId() : null;
                String currentGunId = inventory != null && inventory.getGun() != null ? inventory.getGun().getId() : null;
                String currentThrowId = inventory != null && inventory.getThrowable() != null ? inventory.getThrowable().getId() : null;

                // Thêm điểm và mục tiêu, bỏ qua vật phẩm trùng lặp
                pointItems.add(nearestNextToChest != null ? pointFactory.getPointChest(nearestNextToChest) : Double.NEGATIVE_INFINITY);
                target.add(nearestNextToChest);
                pointItems.add(nearestPlayer != null && nearestNextToPlayer != null ? pointFactory.getPointPlayer(nearestPlayer, nearestNextToPlayer) : Double.NEGATIVE_INFINITY);
                target.add(nearestNextToPlayer);
                pointItems.add(nearestHealth != null ? pointFactory.getPointHealth(nearestHealth) : Double.NEGATIVE_INFINITY);
                target.add(nearestHealth);
                pointItems.add(nearestArmor != null && (currentArmorId == null || !currentArmorId.equals(nearestArmor.getId())) ? pointFactory.getPointArmor(nearestArmor) : Double.NEGATIVE_INFINITY);
                target.add(nearestArmor);
                pointItems.add(nearestMelee != null && (currentMeleeId == null || !currentMeleeId.equals(nearestMelee.getId())) ? pointFactory.getPointWeapon(nearestMelee) : Double.NEGATIVE_INFINITY);
                target.add(nearestMelee);
                pointItems.add(nearestGun != null && (currentGunId == null || !currentGunId.equals(nearestGun.getId())) ? pointFactory.getPointWeapon(nearestGun) : Double.NEGATIVE_INFINITY);
                target.add(nearestGun);
                pointItems.add(nearestThrow != null && (currentThrowId == null || !currentThrowId.equals(nearestThrow.getId())) ? pointFactory.getPointWeapon(nearestThrow) : Double.NEGATIVE_INFINITY);
                target.add(nearestThrow);

                Double maxPoint = Collections.max(pointItems);
                if (maxPoint == Double.NEGATIVE_INFINITY) {
                    return;
                }

                for (int i = 0; i < pointItems.size(); ++i) {
                    if (Objects.equals(pointItems.get(i), maxPoint)) {
                        Node targetNode = target.get(i);
                        if (targetNode == null) {
                            continue;
                        }
                        if (i == 0) {
                            getChest(targetNode);
                        } else if (i == 1 && nearestPlayer != null) {
                            double myHp = me.getHealth() * (100 + pointFactory.getTotalDamageReduce()) / 100;
                            action.move(getPath(targetNode));
                        } else if (i == 2) {
                            getItem(targetNode);
                        } else if (i == 3) {
                            getArmor((Armor) targetNode);
                        } else if (i == 4) {
                            getWeapon((Weapon) targetNode, i);
                        } else if (i == 5) {
                            getWeapon((Weapon) targetNode, i);
                        } else if (i == 6) {
                            getWeapon((Weapon) targetNode, i);
                        }
                        return;
                    }
                }
            }

            @Override
            public void call(Object... args) {
                if (hero == null || args.length == 0) {
                    return;
                }
                gameMap = hero.getGameMap();
                if (gameMap == null) {
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
                if (haveMelee && meleeCooldown <= 0 && inventory != null && inventory.getMelee() != null) {
                    for (Node p : otherPlayers) {
                        if (p != null && Utils.distance(me, p, gameMap) == 1) {
                            mAttack(p);
                            return;
                        }
                    }
                }
                if (haveGun && gunCooldown <= 0 && inventory != null && inventory.getGun() != null) {
                    Weapon gun = inventory.getGun();
                    for (Node p : otherPlayers) {
                        if (p == null) continue;
                        if (Math.abs(p.x - me.x) == 0 && Math.abs(p.y - me.y) <= gun.getRange()) {
                            if (trapInMid(me, p, gameMap.getListTraps(), gameMap.getListChests())) continue;
                            if (p.y < me.getY()) {
                                action.shoot("d");
                            } else {
                                action.shoot("u");
                            }
                            return;
                        }
                        if (Math.abs(p.y - me.y) == 0 && Math.abs(p.x - me.x) <= gun.getRange()) {
                            if (trapInMid(me, p, gameMap.getListTraps(), gameMap.getListChests())) continue;
                            if (p.x < me.getX()) {
                                action.shoot("l");
                            } else {
                                action.shoot("r");
                            }
                            return;
                        }
                    }
                }
                if (tryHealth()) {
                    return;
                }
                if (inventory != null && inventory.getThrowable() != null && me != null && gameMap != null && action != null && otherPlayers != null) {
                    Weapon throwable = inventory.getThrowable();
                    int throwDistance = 6;
                    List<Node> targetThrow = new ArrayList<>();
                    for (int i = 0; i < 4; ++i) {
                        Node target = Utils.add(DIFF_NODE_THROW.get(i), me);
                        if (Utils.isValid(target, gameMap)) {
                            targetThrow.add(target);
                        }
                    }
                    List<Player> nearbyPlayers = new ArrayList<>();
                    for (Player p : otherPlayers) {
                        if (p != null && Utils.distance(p, me, gameMap) <= 7) {
                            nearbyPlayers.add(p);
                        }
                    }
                    Player nearestPlayer = null;
                    int nearestDirectionIndex = -1;
                    double minDistance = Double.MAX_VALUE;
                    for (Node p : nearbyPlayers) {
                        if (p == null) continue;
                        for (int i = 0; i < targetThrow.size(); ++i) {
                            double distance = Utils.distance(targetThrow.get(i), p, gameMap);
                            if (distance <= 1 && distance < minDistance) {
                                minDistance = distance;
                                nearestPlayer = (Player) p;
                                nearestDirectionIndex = i;
                            }
                        }
                    }
                    if (nearestPlayer != null && nearestDirectionIndex != -1) {
                        if (throwable.getCooldown() == 0) {
                            try {
                                action.throwAttack(DIRECTIONS_STR.get(nearestDirectionIndex), throwDistance);
                            } catch (Exception e) {
                            }
                            return;
                        }
                    }
                }
                if ((haveGun && gunCooldown <= 1 && inventory != null && inventory.getGun() != null) ||
                        (haveMelee && meleeCooldown <= 1 && inventory != null && inventory.getMelee() != null)) {
                    for (Node p : otherPlayers) {
                        if (p == null) continue;
                        if (Math.abs(p.x - me.x) == 1 && Math.abs(p.y - me.y) == 1) {
                            if (me.y + 1 == p.y && !contains(restrictedNodesWithoutPlayers, new Node(me.x, me.y + 1))) {
                                action.move("u");
                                return;
                            }
                            if (me.y - 1 == p.y && !contains(restrictedNodesWithoutPlayers, new Node(me.x, me.y - 1))) {
                                action.move("d");
                                return;
                            }
                            action.move(PathUtils.getShortestPath(gameMap, restrictedNodesWithoutPlayers, me, p, false));
                            return;
                        }
                    }
                }
                calculateOptimizedMove();
            }
        };

        hero.setOnMapUpdate(onMapUpdate);
        hero.start(SERVER_URL);
    }
}