package model;

public class SuperMavi implements Hero {
    private final String name;
    private final String heroType;

    public SuperMavi(String name, String heroType) {
        this.name = name;
        this.heroType = heroType;
    }

    @Override
    public String getName() { return name; }

    @Override
    public int getHealth() {
        switch (heroType) {
            case "warrior": return 120;
            case "mage":    return 80;
            case "archer":  return 95;
            default:        return 100;
        }
    }

    @Override
    public int getAttack() {
        switch (heroType) {
            case "warrior": return 25;
            case "mage":    return 35;
            case "archer":  return 30;
            default:        return 20;
        }
    }

    @Override
    public int getDefense() {
        switch (heroType) {
            case "warrior": return 20;
            case "mage":    return 8;
            case "archer":  return 12;
            default:        return 10;
        }
    }

    @Override
    public int getSpeed() {
        switch (heroType) {
            case "warrior": return 10;
            case "mage":    return 15;
            case "archer":  return 20;
            default:        return 12;
        }
    }

    @Override
    public String getDescription() {
        return "Base hero: " + name + " [" + heroType + "]";
    }

    @Override
    public String getType() { return heroType; }
}