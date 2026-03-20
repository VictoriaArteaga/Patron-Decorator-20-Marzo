package model.decorators;

import model.Hero;

public class SwordDecorator extends HeroDecorator {
    private final String swordName;
    private final int bonusAttack;

    public SwordDecorator(Hero hero, String swordName, int bonusAttack) {
        super(hero);
        this.swordName = swordName;
        this.bonusAttack = bonusAttack;
    }

    @Override public int getAttack()  { return wrappedHero.getAttack() + bonusAttack; }
    @Override public String getDescription() {
        return wrappedHero.getDescription() + " | Weapon: " + swordName + " (+" + bonusAttack + " ATK)";
    }
    @Override public String getEquipmentName()     { return swordName; }
    @Override public String getEquipmentCategory() { return "weapon"; }
}