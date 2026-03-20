package service;

import model.Hero;
import java.util.ArrayList;
import java.util.List;

public class CombatService {

    public static class CombatResult {
        public final List<String> log = new ArrayList<>();
        public String winner;
        public int heroAFinalHp;
        public int heroBFinalHp;
        public int totalRounds;
    }

    public CombatResult simulate(Hero heroA, Hero heroB) {
        CombatResult result = new CombatResult();

        int hpA = heroA.getHealth();
        int hpB = heroB.getHealth();
        int round = 0;

        result.log.add("⚔ COMBAT START: " + heroA.getName() + " vs " + heroB.getName());
        result.log.add(heroA.getName() + " Stats → HP:" + hpA
                + " ATK:" + heroA.getAttack()
                + " DEF:" + heroA.getDefense()
                + " SPD:" + heroA.getSpeed());
        result.log.add(heroB.getName() + " Stats → HP:" + hpB
                + " ATK:" + heroB.getAttack()
                + " DEF:" + heroB.getDefense()
                + " SPD:" + heroB.getSpeed());

        // Determine who goes first based on speed
        Hero first  = heroA.getSpeed() >= heroB.getSpeed() ? heroA : heroB;
        Hero second = first == heroA ? heroB : heroA;
        int hpFirst  = first  == heroA ? hpA : hpB;
        int hpSecond = second == heroA ? hpA : hpB;

        result.log.add(first.getName() + " acts first (higher speed)!");

        while (hpFirst > 0 && hpSecond > 0 && round < 20) {
            round++;
            result.log.add("── Round " + round + " ──");

            // First attacks second
            int dmgToSecond = Math.max(1, first.getAttack() - second.getDefense() / 2);
            hpSecond -= dmgToSecond;
            result.log.add(first.getName() + " attacks " + second.getName()
                    + " for " + dmgToSecond + " damage! ("
                    + second.getName() + " HP: " + Math.max(0, hpSecond) + ")");

            if (hpSecond <= 0) break;

            // Second attacks first
            int dmgToFirst = Math.max(1, second.getAttack() - first.getDefense() / 2);
            hpFirst -= dmgToFirst;
            result.log.add(second.getName() + " attacks " + first.getName()
                    + " for " + dmgToFirst + " damage! ("
                    + first.getName() + " HP: " + Math.max(0, hpFirst) + ")");
        }

        result.totalRounds = round;

        if (hpFirst <= 0 && hpSecond <= 0) {
            result.winner = "DRAW";
            result.log.add("💀 Both heroes fell at the same time! It's a DRAW!");
        } else if (hpSecond <= 0) {
            result.winner = first.getName();
            result.log.add("🏆 " + first.getName() + " WINS after " + round + " rounds!");
        } else if (hpFirst <= 0) {
            result.winner = second.getName();
            result.log.add("🏆 " + second.getName() + " WINS after " + round + " rounds!");
        } else {
            result.winner = "DRAW";
            result.log.add("⏱ Time limit reached! Combat ends in a DRAW.");
        }

        result.heroAFinalHp = first  == heroA ? Math.max(0, hpFirst)  : Math.max(0, hpSecond);
        result.heroBFinalHp = second == heroB ? Math.max(0, hpSecond) : Math.max(0, hpFirst);

        return result;
    }
}