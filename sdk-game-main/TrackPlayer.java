import jsclub.codefest.sdk.model.GameMap;
import jsclub.codefest.sdk.model.players.Player;
import jsclub.codefest.sdk.model.weapon.Weapon;

import java.util.*;


public class TrackPlayer {
  List<Weapon> previousMelees, previousGuns;
  List<Weapon> playerGuns, playerMelees;
  boolean[][] isWeapon;
  List<String> playerName;

  void init(List<Player> players) {
    playerGuns = new ArrayList<>();
    playerMelees = new ArrayList<>();
    playerName = new ArrayList<>();
    for (Player player : players) {
      playerGuns.add(null);
      playerMelees.add(null);
      playerName.add(player.getID());
    }
    previousMelees = new ArrayList<>();
    previousGuns = new ArrayList<>();
  }

  public TrackPlayer() {
  }

  void update(GameMap gameMap) {
    isWeapon = new boolean[gameMap.getMapSize()][gameMap.getMapSize()];
    List<Player> players = gameMap.getOtherPlayerInfo();
    for (int i = 0; i < players.size(); i++) {
      if (players.get(i).getHealth() < 0) {
        playerGuns.set(i, null);
        playerMelees.set(i, null);
      }
    }
    List<Weapon> currentGuns = gameMap.getAllGun();
    List<Weapon> currentMelees = gameMap.getAllMelee();
    for (Weapon weapon : currentGuns) {
      isWeapon[weapon.x][weapon.y] = true;
    }
    for (Weapon weapon : currentMelees) {
      isWeapon[weapon.x][weapon.y] = true;
    }
    for (Weapon gun : previousGuns) {
      if (!isWeapon[gun.x][gun.y]) {
        for (int i = 0; i < players.size(); i++) {
          if (Utils.equal(players.get(i), gun)) {
            playerGuns.set(i, gun);
          }
        }
      }
    }
    for (Weapon melee : previousMelees) {
      if (!isWeapon[melee.x][melee.y]) {
        for (int i = 0; i < players.size(); i++) {
          if (Utils.equal(players.get(i), melee)) {
            playerMelees.set(i, melee);
          }
        }
      }
    }
    previousMelees = currentMelees;
    previousGuns = currentGuns;
  }

  int getIndexByName(String name) {
    for (int i = 0; i < playerName.size(); i++) {
      if (playerName.get(i).equals(name)) {
        return i;
      }
    }
    return -1;
  }

  double getStepToKill(String name, Float health) {
    int indexPlayer = getIndexByName(name);
    return Utils.stepToKill(playerGuns.get(indexPlayer), playerMelees.get(indexPlayer), health);
  }
}