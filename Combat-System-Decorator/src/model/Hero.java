package model;

public interface Hero {
    String getName();
    int getHealth();
    int getAttack();
    int getDefense();
    int getSpeed();
    String getDescription();
    String getType(); // base type: "warrior", "mage", "archer"
}