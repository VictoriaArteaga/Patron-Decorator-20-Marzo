package model.decorators;

import model.Hero;

public abstract class HeroDecorator implements Hero {
    protected final Hero wrappedHero;

    public HeroDecorator(Hero hero) {
        this.wrappedHero = hero;
    }

    @Override
    public String getName()        { return wrappedHero.getName(); }
    @Override
    public int getHealth()         { return wrappedHero.getHealth(); }
    @Override
    public int getAttack()         { return wrappedHero.getAttack(); }
    @Override
    public int getDefense()        { return wrappedHero.getDefense(); }
    @Override
    public int getSpeed()          { return wrappedHero.getSpeed(); }
    @Override
    public String getType()        { return wrappedHero.getType(); }

    public abstract String getEquipmentName();
    public abstract String getEquipmentCategory(); // weapon, armor, power, buff
}