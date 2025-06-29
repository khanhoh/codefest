import jsclub.codefest.sdk.base.Node;
import jsclub.codefest.sdk.model.GameMap;
import jsclub.codefest.sdk.model.npcs.Enemy;

import java.util.*;


public class EnemyMap {
  private static final List<Node> DIRECTIONS = Arrays.asList(new Node(0, 1), new Node(0, -1), new Node(1, 0),
      new Node(-1, 0));
  List<List<Integer>> startTime1;
  List<List<Integer>> startTime2;
  List<List<Integer>> cycle;
  List<List<Integer>> enemyCount;

  public EnemyMap() {
  }

  private void init(GameMap gameMap) {
    int mapSize = gameMap.getMapSize();
    startTime1 = Utils.initializeList(mapSize, 0);
    startTime2 = Utils.initializeList(mapSize, 0);
    cycle = Utils.initializeList(mapSize, 0);
    enemyCount = Utils.initializeList(mapSize, 0);
  }

  void dfs(Node u, int timeCycle, GameMap gameMap) {
    cycle.get(u.x).set(u.y, timeCycle);
    for (Node dir : DIRECTIONS) {
      Node next = Utils.add(dir, u);
      if (Utils.isValid(next, gameMap) && cycle.get(next.x).get(next.y) != timeCycle
          && enemyCount.get(next.x).get(next.y) != 0) {
        dfs(next, timeCycle, gameMap);
      }
    }
  }

  void calcEnemy(GameMap gameMap, int time) {
    if (time == 0)
      init(gameMap);
    List<Enemy> enemies = gameMap.getListEnemies();
    for (Node p : enemies) {
      int enemyCountCurrent = enemyCount.get(p.x).get(p.y) + 1;
      enemyCount.get(p.x).set(p.y, enemyCountCurrent);
      if (enemyCountCurrent % 2 == 1) {
        int newCycleTime = time - startTime1.get(p.x).get(p.y);
        if (enemyCountCurrent >= 3 && newCycleTime != cycle.get(p.x).get(p.y) && newCycleTime < 20
            && newCycleTime % 2 == 0) {
          dfs(p, newCycleTime, gameMap);
        }
        startTime1.get(p.x).set(p.y, time);
      }
      if (enemyCountCurrent % 2 == 0) {
        int newCycleTime = time - startTime2.get(p.x).get(p.y);
        if (enemyCountCurrent >= 4 && newCycleTime != cycle.get(p.x).get(p.y) && newCycleTime < 20
            && newCycleTime % 2 == 0) {
          dfs(p, newCycleTime, gameMap);
        }
        startTime2.get(p.x).set(p.y, time);
      }
    }
  }

  boolean isEnemy(int time, Node node) {
    int cycleTime = cycle.get(node.x).get(node.y);
    if (cycleTime != 0) {
      if ((time - startTime1.get(node.x).get(node.y)) % cycleTime == 0) {
        return true;
      }
      if (enemyCount.get(node.x).get(node.y) >= 2
          && (time - startTime2.get(node.x).get(node.y)) % cycleTime == 0) {
        return true;
      }
    }
    return false;
  }

  boolean isBlock(int time, Node node, GameMap gameMap) {
    for (int i = -1; i <= 1; ++i) {
      for (int j = -1; j <= 1; ++j) {
        Node p = Utils.add(new Node(i, j), node);
        if (Utils.isValid(p, gameMap) && (isEnemy(time, p) || isEnemy(time + 1, p))) {
          return true;
        }
      }
    }
    return false;
  }
}