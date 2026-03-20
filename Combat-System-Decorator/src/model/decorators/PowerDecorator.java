package model.decorators;

import model.Hero;

public class PowerDecorator extends HeroDecorator {
    private final String powerName;
    private final int bonusAttack;
    private final int bonusSpeed;

    public PowerDecorator(Hero hero, String powerName, int bonusAttack, int bonusSpeed) {
        super(hero);
        this.powerName = powerName;
        this.bonusAttack = bonusAttack;
        this.bonusSpeed = bonusSpeed;
    }

    @Override public int getAttack() { return wrappedHero.getAttack() + bonusAttack; }
    @Override public int getSpeed()  { return wrappedHero.getSpeed() + bonusSpeed; }
    @Override public String getDescription() {
        return wrappedHero.getDescription() + " | Power: " + powerName
                + " (+" + bonusAttack + " ATK, +" + bonusSpeed + " SPD)";
    }
    @Override public String getEquipmentName()     { return powerName; }
    @Override public String getEquipmentCategory() { return "power"; }
}