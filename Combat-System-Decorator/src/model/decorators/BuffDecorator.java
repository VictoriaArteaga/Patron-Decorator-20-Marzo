package model.decorators;

import model.Hero;

public class BuffDecorator extends HeroDecorator {
    private final String buffName;
    private final int bonusAttack;
    private final int bonusDefense;
    private final int bonusHealth;
    private final int bonusSpeed;
    private final int durationRounds;

    public BuffDecorator(Hero hero, String buffName,
                         int bonusAttack, int bonusDefense,
                         int bonusHealth, int bonusSpeed,
                         int durationRounds) {
        super(hero);
        this.buffName      = buffName;
        this.bonusAttack   = bonusAttack;
        this.bonusDefense  = bonusDefense;
        this.bonusHealth   = bonusHealth;
        this.bonusSpeed    = bonusSpeed;
        this.durationRounds = durationRounds;
    }

    @Override public int getAttack()  { return wrappedHero.getAttack()  + bonusAttack; }
    @Override public int getDefense() { return wrappedHero.getDefense() + bonusDefense; }
    @Override public int getHealth()  { return wrappedHero.getHealth()  + bonusHealth; }
    @Override public int getSpeed()   { return wrappedHero.getSpeed()   + bonusSpeed; }

    public int getDurationRounds() { return durationRounds; }

    @Override public String getDescription() {
        return wrappedHero.getDescription() + " | Buff: " + buffName
                + " (+" + bonusAttack + " ATK, +" + bonusDefense + " DEF, +"
                + bonusHealth + " HP, +" + bonusSpeed + " SPD, " + durationRounds + " rounds)";
    }
    @Override public String getEquipmentName()     { return buffName; }
    @Override public String getEquipmentCategory() { return "buff"; }
}