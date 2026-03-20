package model.decorators;
import model.Hero;

public class ArmorDecorator extends HeroDecorator {
    private final String armorName;
    private final int bonusDefense;
    private final int bonusHealth;

    public ArmorDecorator(Hero hero, String armorName, int bonusDefense, int bonusHealth) {
        super(hero);
        this.armorName = armorName;
        this.bonusDefense = bonusDefense;
        this.bonusHealth = bonusHealth;
    }

    @Override public int getDefense() { return wrappedHero.getDefense() + bonusDefense; }
    @Override public int getHealth()  { return wrappedHero.getHealth() + bonusHealth; }
    @Override public String getDescription() {
        return wrappedHero.getDescription() + " | Armor: " + armorName
                + " (+" + bonusDefense + " DEF, +" + bonusHealth + " HP)";
    }
    @Override public String getEquipmentName()     { return armorName; }
    @Override public String getEquipmentCategory() { return "armor"; }
}