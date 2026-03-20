package server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import model.Hero;
import model.SuperMavi;
import model.decorators.*;
import service.CombatService;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;

public class Main {

    // ── In-memory hero store ──────────────────────────────────────────────────
    private static final Map<String, Hero> heroes = new LinkedHashMap<>();

    static {
        heroes.put("hero1", new SuperMavi("Kael", "warrior"));
        heroes.put("hero2", new SuperMavi("Lyra", "mage"));
    }

    // ── Main ──────────────────────────────────────────────────────────────────
    public static void main(String[] args) throws Exception {
        int port = 8080;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/",        new StaticHandler());
        server.createContext("/api/heroes",   new HeroesHandler());
        server.createContext("/api/equip",    new EquipHandler());
        server.createContext("/api/reset",    new ResetHandler());
        server.createContext("/api/combat",   new CombatHandler());

        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();
        System.out.println("RPG Combat Server running → http://localhost:" + port);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private static void send(HttpExchange ex, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    private static String readBody(HttpExchange ex) throws IOException {
        return new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    /** Simple JSON-like parser for flat {"key":"val"} objects */
    private static Map<String, String> parseJson(String json) {
        Map<String, String> map = new LinkedHashMap<>();
        json = json.trim().replaceAll("[{}\"]", "");
        for (String pair : json.split(",")) {
            String[] kv = pair.split(":", 2);
            if (kv.length == 2) map.put(kv[0].trim(), kv[1].trim());
        }
        return map;
    }

    private static String heroToJson(String id, Hero h) {
        return "{\"id\":\"" + id + "\","
                + "\"name\":\"" + h.getName() + "\","
                + "\"type\":\"" + h.getType() + "\","
                + "\"health\":" + h.getHealth() + ","
                + "\"attack\":" + h.getAttack() + ","
                + "\"defense\":" + h.getDefense() + ","
                + "\"speed\":" + h.getSpeed() + ","
                + "\"description\":\"" + h.getDescription().replace("\"", "'") + "\"}";
    }

    // ── Handlers ──────────────────────────────────────────────────────────────

    static class HeroesHandler implements HttpHandler {
        @Override public void handle(HttpExchange ex) throws IOException {
            if (!ex.getRequestMethod().equals("GET")) { send(ex, 405, "{}"); return; }
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Map.Entry<String, Hero> e : heroes.entrySet()) {
                if (!first) sb.append(",");
                sb.append(heroToJson(e.getKey(), e.getValue()));
                first = false;
            }
            sb.append("]");
            send(ex, 200, sb.toString());
        }
    }

    static class EquipHandler implements HttpHandler {
        @Override public void handle(HttpExchange ex) throws IOException {
            if (!ex.getRequestMethod().equals("POST")) { send(ex, 405, "{}"); return; }
            Map<String, String> body = parseJson(readBody(ex));
            String heroId   = body.getOrDefault("heroId", "");
            String category = body.getOrDefault("category", "");
            String itemName = body.getOrDefault("itemName", "");

            Hero hero = heroes.get(heroId);
            if (hero == null) { send(ex, 404, "{\"error\":\"Hero not found\"}"); return; }

            switch (category) {
                case "weapon":
                    int atk = Integer.parseInt(body.getOrDefault("attack", "15"));
                    hero = new SwordDecorator(hero, itemName, atk);
                    break;
                case "armor":
                    int def = Integer.parseInt(body.getOrDefault("defense", "10"));
                    int hp  = Integer.parseInt(body.getOrDefault("health", "20"));
                    hero = new ArmorDecorator(hero, itemName, def, hp);
                    break;
                case "power":
                    int patk = Integer.parseInt(body.getOrDefault("attack", "10"));
                    int spd  = Integer.parseInt(body.getOrDefault("speed", "5"));
                    hero = new PowerDecorator(hero, itemName, patk, spd);
                    break;
                case "buff":
                    int ba = Integer.parseInt(body.getOrDefault("attack", "8"));
                    int bd = Integer.parseInt(body.getOrDefault("defense", "5"));
                    int bh = Integer.parseInt(body.getOrDefault("health", "15"));
                    int bs = Integer.parseInt(body.getOrDefault("speed", "3"));
                    int br = Integer.parseInt(body.getOrDefault("rounds", "3"));
                    hero = new BuffDecorator(hero, itemName, ba, bd, bh, bs, br);
                    break;
                default:
                    send(ex, 400, "{\"error\":\"Unknown category\"}");
                    return;
            }
            heroes.put(heroId, hero);
            send(ex, 200, heroToJson(heroId, hero));
        }
    }

    static class ResetHandler implements HttpHandler {
        @Override public void handle(HttpExchange ex) throws IOException {
            if (!ex.getRequestMethod().equals("POST")) { send(ex, 405, "{}"); return; }
            Map<String, String> body = parseJson(readBody(ex));
            String heroId   = body.getOrDefault("heroId", "");
            String heroName = body.getOrDefault("name", "Hero");
            String heroType = body.getOrDefault("heroType", "warrior");

            if (heroId.isEmpty() || !heroes.containsKey(heroId)) {
                send(ex, 404, "{\"error\":\"Hero not found\"}"); return;
            }
            heroes.put(heroId, new SuperMavi(heroName, heroType));
            send(ex, 200, heroToJson(heroId, heroes.get(heroId)));
        }
    }

    static class CombatHandler implements HttpHandler {
        private final CombatService combatService = new CombatService();

        @Override public void handle(HttpExchange ex) throws IOException {
            if (!ex.getRequestMethod().equals("POST")) { send(ex, 405, "{}"); return; }
            Map<String, String> body = parseJson(readBody(ex));
            String idA = body.getOrDefault("heroAId", "hero1");
            String idB = body.getOrDefault("heroBId", "hero2");

            Hero a = heroes.get(idA);
            Hero b = heroes.get(idB);
            if (a == null || b == null) { send(ex, 404, "{\"error\":\"Hero not found\"}"); return; }

            CombatService.CombatResult result = combatService.simulate(a, b);

            StringBuilder logJson = new StringBuilder("[");
            for (int i = 0; i < result.log.size(); i++) {
                if (i > 0) logJson.append(",");
                logJson.append("\"").append(result.log.get(i).replace("\"", "'")).append("\"");
            }
            logJson.append("]");

            String json = "{\"winner\":\"" + result.winner + "\","
                    + "\"heroAFinalHp\":" + result.heroAFinalHp + ","
                    + "\"heroBFinalHp\":" + result.heroBFinalHp + ","
                    + "\"totalRounds\":" + result.totalRounds + ","
                    + "\"log\":" + logJson + "}";
            send(ex, 200, json);
        }
    }

    // ── Static HTML Handler ───────────────────────────────────────────────────
    static class StaticHandler implements HttpHandler {
        @Override public void handle(HttpExchange ex) throws IOException {
            String html = buildHtml();
            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            ex.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
        }

        private String buildHtml() {
            return """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8"/>
<meta name="viewport" content="width=device-width,initial-scale=1"/>
<title>RPG Combat System ⚔</title>
<link href="https://fonts.googleapis.com/css2?family=Cinzel+Decorative:wght@700;900&family=Cinzel:wght@400;600&family=Crimson+Text:ital,wght@0,400;0,600;1,400&display=swap" rel="stylesheet"/>
<style>
:root{
  --gold:#c9a84c;--gold-light:#f0d080;--gold-dark:#8a6a1a;
  --blood:#8b0000;--blood-light:#c0392b;
  --void:#0a0a12;--void2:#12121e;--void3:#1a1a2e;
  --stone:#2a2a3e;--stone2:#3a3a52;
  --text:#e8dcc8;--text-dim:#a09070;
  --warrior:#e67e22;--mage:#9b59b6;--archer:#27ae60;
  --glow:0 0 20px rgba(201,168,76,0.4);
}
*{box-sizing:border-box;margin:0;padding:0}
body{background:var(--void);color:var(--text);font-family:'Crimson Text',serif;min-height:100vh;overflow-x:hidden}
body::before{
  content:'';position:fixed;inset:0;pointer-events:none;z-index:0;
  background:
    radial-gradient(ellipse at 20% 50%,rgba(139,0,0,0.1) 0%,transparent 50%),
    radial-gradient(ellipse at 80% 50%,rgba(75,0,130,0.1) 0%,transparent 50%),
    url("data:image/svg+xml,%3Csvg width='60' height='60' viewBox='0 0 60 60' xmlns='http://www.w3.org/2000/svg'%3E%3Cg fill='%23c9a84c' fill-opacity='0.03'%3E%3Cpath d='M36 34v-4h-2v4h-4v2h4v4h2v-4h4v-2h-4zm0-30V0h-2v4h-4v2h4v4h2V6h4V4h-4zM6 34v-4H4v4H0v2h4v4h2v-4h4v-2H6zM6 4V0H4v4H0v2h4v4h2V6h4V4H6z'/%3E%3C/g%3E%3C/svg%3E");
}
.page{position:relative;z-index:1;max-width:1400px;margin:0 auto;padding:20px}
header{text-align:center;padding:28px 0 18px;border-bottom:1px solid rgba(201,168,76,.3);margin-bottom:28px}
header h1{
  font-family:'Cinzel Decorative',cursive;font-size:clamp(1.6rem,3.5vw,2.8rem);
  background:linear-gradient(135deg,var(--gold-dark),var(--gold-light),var(--gold-dark));
  -webkit-background-clip:text;-webkit-text-fill-color:transparent;background-clip:text;
  letter-spacing:3px;
}
header p{color:var(--text-dim);font-style:italic;margin-top:6px;font-size:1rem}

/* ── Arena grid ── */
.arena{display:grid;grid-template-columns:1fr auto 1fr;gap:18px;align-items:start;margin-bottom:24px}
@media(max-width:900px){.arena{grid-template-columns:1fr}}

/* ── Hero Card ── */
.hero-card{
  background:linear-gradient(160deg,var(--stone),var(--void3));
  border:1px solid rgba(201,168,76,.25);border-radius:14px;
  padding:18px;position:relative;overflow:hidden;transition:transform .3s,box-shadow .3s;
}
.hero-card::before{content:'';position:absolute;top:0;left:0;right:0;height:3px;
  background:linear-gradient(90deg,transparent,var(--gold),transparent)}
.hero-card:hover{transform:translateY(-3px);box-shadow:var(--glow)}
.hero-card.warrior::before{background:linear-gradient(90deg,transparent,var(--warrior),transparent)}
.hero-card.mage::before   {background:linear-gradient(90deg,transparent,var(--mage),transparent)}
.hero-card.archer::before {background:linear-gradient(90deg,transparent,var(--archer),transparent)}

/* ── Sprite Stage ── */
.sprite-stage{
  width:100%;height:200px;display:flex;align-items:flex-end;justify-content:center;
  position:relative;margin-bottom:12px;
  background:linear-gradient(180deg,rgba(0,0,0,0) 0%,rgba(0,0,0,.3) 100%);
  border-radius:10px;overflow:hidden;
}
.sprite-stage::after{
  content:'';position:absolute;bottom:0;left:10%;right:10%;height:12px;
  background:radial-gradient(ellipse,rgba(0,0,0,.5),transparent 70%);
  border-radius:50%;
}
.sprite-wrap{
  position:relative;width:110px;height:180px;
  transform-origin:bottom center;
  animation:idle 3s ease-in-out infinite;
}
.sprite-wrap.shake-sprite{animation:spriteHit .5s ease!important}
.sprite-wrap.attack-sprite{animation:spriteAtk .4s ease!important}
@keyframes idle{0%,100%{transform:translateY(0)}50%{transform:translateY(-5px)}}
@keyframes spriteHit{0%,100%{transform:translateX(0)}20%{transform:translateX(12px) rotate(5deg)}60%{transform:translateX(-8px) rotate(-3deg)}}
@keyframes spriteAtk{0%{transform:translateX(0) scaleX(1)}40%{transform:translateX(-20px) scaleX(1.1)}70%{transform:translateX(8px) scaleX(.95)}100%{transform:translateX(0) scaleX(1)}}

/* equipment glow overlays on sprite */
.sprite-wrap.has-weapon .weapon-glow{opacity:1}
.sprite-wrap.has-armor  .armor-glow {opacity:1}
.sprite-wrap.has-power  .power-glow {opacity:1}
.sprite-wrap.has-buff   .buff-aura  {opacity:1;animation:buffPulse 1s ease-in-out infinite}
.weapon-glow,.armor-glow,.power-glow,.buff-aura{opacity:0;transition:opacity .5s;pointer-events:none}
@keyframes buffPulse{0%,100%{opacity:.4}50%{opacity:.9}}

/* ── Hero Header row ── */
.hero-header{display:flex;align-items:center;gap:12px;margin-bottom:10px}
.hero-info h2{font-family:'Cinzel',serif;font-size:1.25rem;color:var(--gold-light)}
.hero-type-badge{
  font-size:.7rem;letter-spacing:2px;text-transform:uppercase;
  font-family:'Cinzel',serif;margin-top:2px;
}
.warrior .hero-type-badge{color:var(--warrior)}
.mage    .hero-type-badge{color:var(--mage)}
.archer  .hero-type-badge{color:var(--archer)}

/* ── HP Bar ── */
.hp-bar-wrap{margin:8px 0 10px}
.hp-bar-label{display:flex;justify-content:space-between;font-size:.72rem;color:var(--text-dim);margin-bottom:3px}
.hp-bar{height:9px;background:rgba(0,0,0,.5);border-radius:5px;overflow:hidden;border:1px solid rgba(201,168,76,.2)}
.hp-fill{height:100%;background:linear-gradient(90deg,#922b21,#e74c3c);border-radius:5px;transition:width .8s cubic-bezier(.4,0,.2,1)}

/* ── Stats ── */
.stats-grid{display:grid;grid-template-columns:1fr 1fr;gap:6px;margin:8px 0}
.stat{background:rgba(0,0,0,.3);border-radius:8px;padding:7px;text-align:center}
.stat-label{font-size:.6rem;letter-spacing:1px;text-transform:uppercase;color:var(--text-dim)}
.stat-value{font-family:'Cinzel',serif;font-size:1.2rem;font-weight:600}
.stat-value.bump{animation:statBump .4s ease}
@keyframes statBump{0%{transform:scale(1)}50%{transform:scale(1.4);color:var(--gold-light)}100%{transform:scale(1)}}

/* ── Tags ── */
.equip-tags{display:flex;flex-wrap:wrap;gap:4px;min-height:22px;margin-bottom:8px}
.equip-tag{font-size:.65rem;padding:2px 7px;border-radius:20px;font-family:'Cinzel',serif;animation:tagAppear .4s ease}
@keyframes tagAppear{from{opacity:0;transform:scale(.7)}to{opacity:1;transform:scale(1)}}
.tag-weapon{background:rgba(231,76,60,.2);border:1px solid rgba(231,76,60,.5);color:#e74c3c}
.tag-armor {background:rgba(52,152,219,.2);border:1px solid rgba(52,152,219,.5);color:#3498db}
.tag-power {background:rgba(155,89,182,.2);border:1px solid rgba(155,89,182,.5);color:#9b59b6}
.tag-buff  {background:rgba(46,204,113,.2);border:1px solid rgba(46,204,113,.5);color:#2ecc71}

/* ── Equip Panel ── */
.equip-panel{border-top:1px solid rgba(201,168,76,.15);padding-top:12px}
.equip-panel h4{font-family:'Cinzel',serif;font-size:.75rem;letter-spacing:2px;color:var(--text-dim);margin-bottom:8px;text-transform:uppercase}
.equip-grid{display:grid;grid-template-columns:1fr 1fr;gap:5px}
.equip-btn{
  padding:7px;border:1px solid;border-radius:8px;font-family:'Cinzel',serif;font-size:.68rem;
  cursor:pointer;background:rgba(0,0,0,.3);letter-spacing:.5px;transition:all .2s;text-transform:uppercase;
}
.equip-btn:hover{transform:translateY(-2px);filter:brightness(1.3)}
.equip-btn:active{transform:scale(.95)}
.btn-weapon{border-color:rgba(231,76,60,.5);color:#e74c3c}
.btn-weapon:hover{background:rgba(231,76,60,.15)}
.btn-armor {border-color:rgba(52,152,219,.5);color:#3498db}
.btn-armor:hover{background:rgba(52,152,219,.15)}
.btn-power {border-color:rgba(155,89,182,.5);color:#9b59b6}
.btn-power:hover{background:rgba(155,89,182,.15)}
.btn-buff  {border-color:rgba(46,204,113,.5);color:#2ecc71}
.btn-buff:hover{background:rgba(46,204,113,.15)}
.btn-reset {border-color:rgba(201,168,76,.4);color:var(--text-dim);grid-column:1/-1;margin-top:3px}
.btn-reset:hover{background:rgba(201,168,76,.1);color:var(--gold)}

/* ── VS Section ── */
.vs-section{display:flex;flex-direction:column;align-items:center;justify-content:center;gap:18px;padding:16px 8px}
.vs-badge{
  font-family:'Cinzel Decorative',cursive;font-size:2.4rem;
  background:linear-gradient(135deg,var(--blood),var(--gold),var(--blood));
  -webkit-background-clip:text;-webkit-text-fill-color:transparent;background-clip:text;
  animation:vsPulse 2s ease-in-out infinite;
}
@keyframes vsPulse{0%,100%{filter:drop-shadow(0 0 8px rgba(201,168,76,.3))}50%{filter:drop-shadow(0 0 22px rgba(201,168,76,.9))}}
.combat-btn{
  padding:13px 26px;
  background:linear-gradient(135deg,var(--blood),#500,var(--blood));
  border:2px solid var(--gold-dark);border-radius:8px;
  color:var(--gold-light);font-family:'Cinzel',serif;font-size:.95rem;
  cursor:pointer;letter-spacing:2px;text-transform:uppercase;
  transition:all .3s;box-shadow:0 0 15px rgba(139,0,0,.4);
}
.combat-btn:hover{background:linear-gradient(135deg,var(--blood-light),#800,var(--blood-light));box-shadow:0 0 30px rgba(139,0,0,.8);transform:scale(1.05)}
.combat-btn:active{transform:scale(.97)}
.combat-btn:disabled{opacity:.5;cursor:not-allowed;transform:none}

/* ── Combat Log ── */
.combat-section{
  background:linear-gradient(160deg,var(--stone),var(--void3));
  border:1px solid rgba(201,168,76,.25);border-radius:12px;padding:18px;
}
.combat-section h3{font-family:'Cinzel',serif;letter-spacing:3px;font-size:.95rem;color:var(--gold);text-transform:uppercase;margin-bottom:12px;border-bottom:1px solid rgba(201,168,76,.2);padding-bottom:8px}
.log-container{
  height:260px;overflow-y:auto;padding:10px;
  background:rgba(0,0,0,.4);border-radius:8px;border:1px solid rgba(201,168,76,.1);
  font-size:.88rem;line-height:1.8;scrollbar-width:thin;scrollbar-color:var(--gold-dark) transparent;
}
.log-entry{padding:2px 0;border-bottom:1px solid rgba(201,168,76,.05);opacity:0;transform:translateX(-10px);animation:logIn .3s ease forwards}
@keyframes logIn{to{opacity:1;transform:translateX(0)}}
.log-entry.round{color:var(--gold);font-family:'Cinzel',serif;font-size:.78rem;letter-spacing:1px;margin-top:5px}
.log-entry.win{color:#f1c40f;font-family:'Cinzel',serif;font-size:.98rem;text-align:center;margin-top:6px}
.log-entry.info{color:var(--text-dim);font-style:italic}
.log-entry.attack{color:var(--text)}
.log-container::-webkit-scrollbar{width:4px}
.log-container::-webkit-scrollbar-thumb{background:var(--gold-dark);border-radius:2px}

/* ── Winner Banner ── */
.winner-banner{display:none;text-align:center;margin-top:14px;padding:18px;
  background:linear-gradient(135deg,rgba(139,0,0,.3),rgba(201,168,76,.15),rgba(139,0,0,.3));
  border:2px solid var(--gold-dark);border-radius:12px;animation:bannerIn .6s ease}
@keyframes bannerIn{from{opacity:0;transform:scale(.9)}to{opacity:1;transform:scale(1)}}
.winner-banner h2{font-family:'Cinzel Decorative',cursive;font-size:1.7rem;color:var(--gold-light)}
.winner-banner p{color:var(--text-dim);margin-top:6px}

/* ── Toast ── */
.toast{
  position:fixed;bottom:28px;right:28px;z-index:999;
  background:linear-gradient(135deg,var(--stone),var(--void3));
  border:1px solid var(--gold-dark);border-radius:10px;
  padding:12px 18px;font-family:'Cinzel',serif;font-size:.82rem;
  color:var(--gold-light);box-shadow:var(--glow);
  transform:translateY(100px);opacity:0;transition:all .4s cubic-bezier(.34,1.56,.64,1);
  max-width:280px;pointer-events:none;
}
.toast.show{transform:translateY(0);opacity:1}

/* ── Flash / Particles ── */
.flash-overlay{position:fixed;inset:0;z-index:500;pointer-events:none;opacity:0;background:rgba(201,168,76,.15)}
.flash-overlay.active{animation:flash .6s ease}
@keyframes flash{0%{opacity:0}20%{opacity:1}100%{opacity:0}}
.particles-container{position:fixed;inset:0;pointer-events:none;z-index:400;overflow:hidden}
.particle{position:absolute;border-radius:50%;pointer-events:none;animation:particleFly var(--dur,.8s) ease forwards}
@keyframes particleFly{0%{opacity:1;transform:translate(0,0) scale(1)}100%{opacity:0;transform:translate(var(--tx,50px),var(--ty,-80px)) scale(0)}}
</style>
</head>
<body>
<div class="flash-overlay" id="flashOverlay"></div>
<div class="particles-container" id="particlesCont"></div>
<div class="toast" id="toast"><span id="toastMsg"></span></div>

<div class="page">
  <header>
    <h1>⚔ RPG COMBAT SYSTEM ⚔</h1>
    <p>Decorator Pattern — Equip your heroes and battle to the last breath</p>
  </header>

  <div class="arena">

    <!-- ══ HERO A (Protagonist) ══ -->
    <div class="hero-card" id="cardA">

      <!-- 2D Sprite -->
      <div class="sprite-stage">
        <div class="sprite-wrap" id="spriteA">
          <svg id="svgA" viewBox="0 0 110 180" xmlns="http://www.w3.org/2000/svg" width="110" height="180">
            <!-- Shadow -->
            <ellipse cx="55" cy="175" rx="22" ry="5" fill="rgba(0,0,0,.35)"/>

            <!-- ── BUFF AURA ── -->
            <g class="buff-aura">
              <circle cx="55" cy="90" r="52" fill="none" stroke="#2ecc71" stroke-width="2" opacity=".5">
                <animate attributeName="r" values="48;56;48" dur="1.2s" repeatCount="indefinite"/>
                <animate attributeName="opacity" values=".5;.15;.5" dur="1.2s" repeatCount="indefinite"/>
              </circle>
            </g>

            <!-- ── POWER GLOW ── -->
            <g class="power-glow">
              <circle cx="55" cy="75" r="30" fill="rgba(155,89,182,.18)">
                <animate attributeName="r" values="28;34;28" dur="1s" repeatCount="indefinite"/>
              </circle>
            </g>

            <!-- LEGS -->
            <line x1="45" y1="130" x2="38" y2="165" stroke="#c0845a" stroke-width="9" stroke-linecap="round" id="legL-A"/>
            <line x1="65" y1="130" x2="72" y2="165" stroke="#c0845a" stroke-width="9" stroke-linecap="round" id="legR-A"/>

            <!-- BOOTS -->
            <ellipse cx="37" cy="167" rx="9" ry="5" fill="#5a3010" id="bootL-A"/>
            <ellipse cx="73" cy="167" rx="9" ry="5" fill="#5a3010" id="bootR-A"/>

            <!-- BODY / TORSO -->
            <rect x="36" y="78" width="38" height="55" rx="8" fill="#c0845a" id="torso-A"/>

            <!-- ── ARMOR LAYER ── -->
            <g class="armor-glow" id="armorA">
              <rect x="34" y="76" width="42" height="57" rx="9" fill="none" stroke="#3498db" stroke-width="2.5" opacity=".9"/>
              <rect x="38" y="82" width="34" height="14" rx="4" fill="rgba(52,152,219,.25)"/>
              <!-- shoulder pads -->
              <rect x="28" y="78" width="12" height="10" rx="3" fill="#2980b9" opacity=".8"/>
              <rect x="70" y="78" width="12" height="10" rx="3" fill="#2980b9" opacity=".8"/>
            </g>

            <!-- BELT -->
            <rect x="36" y="122" width="38" height="8" rx="3" fill="#8B4513" id="belt-A"/>
            <rect x="51" y="122" width="8" height="8" rx="1" fill="#c9a84c"/>

            <!-- ARMS -->
            <line x1="36" y1="85" x2="18" y2="115" stroke="#c0845a" stroke-width="9" stroke-linecap="round" id="armL-A"/>
            <line x1="74" y1="85" x2="92" y2="115" stroke="#c0845a" stroke-width="9" stroke-linecap="round" id="armR-A"/>

            <!-- HANDS -->
            <circle cx="17" cy="117" r="6" fill="#c0845a" id="handL-A"/>
            <circle cx="93" cy="117" r="6" fill="#c0845a" id="handR-A"/>

            <!-- ── WEAPON ── -->
            <g class="weapon-glow" id="weaponA">
              <!-- Sword held in right hand -->
              <line x1="93" y1="108" x2="93" y2="60" stroke="#aaa" stroke-width="4" stroke-linecap="round"/>
              <line x1="87" y1="98" x2="99" y2="98" stroke="#888" stroke-width="3" stroke-linecap="round"/>
              <polygon points="93,55 90,65 96,65" fill="#e8d060"/>
              <!-- glow -->
              <line x1="93" y1="108" x2="93" y2="60" stroke="#e74c3c" stroke-width="2" opacity=".6">
                <animate attributeName="opacity" values=".3;.8;.3" dur=".8s" repeatCount="indefinite"/>
              </line>
            </g>

            <!-- NECK -->
            <rect x="49" y="58" width="12" height="22" rx="5" fill="#c0845a"/>

            <!-- HEAD -->
            <circle cx="55" cy="48" r="26" fill="#e8a878" id="head-A"/>

            <!-- HAIR — protagonist warm brown -->
            <path d="M30 40 Q32 18 55 18 Q78 18 80 40 Q72 24 55 26 Q38 24 30 40Z" fill="#6b3a1f"/>
            <path d="M30 42 Q26 36 28 28 Q34 16 55 16 Q62 16 62 20 Q55 18 48 20 Q34 22 30 42Z" fill="#7d4525"/>

            <!-- FACE -->
            <!-- eyes (determined look) -->
            <ellipse cx="46" cy="46" rx="4" ry="3.5" fill="#fff"/>
            <ellipse cx="64" cy="46" rx="4" ry="3.5" fill="#fff"/>
            <circle cx="47" cy="47" r="2.5" fill="#2c1a0e"/>
            <circle cx="65" cy="47" r="2.5" fill="#2c1a0e"/>
            <circle cx="48" cy="46" r="1" fill="#fff"/>
            <circle cx="66" cy="46" r="1" fill="#fff"/>
            <!-- eyebrows — furrowed -->
            <path d="M42 41 Q46 39 50 41" stroke="#4a2010" stroke-width="2" fill="none" stroke-linecap="round"/>
            <path d="M60 41 Q64 39 68 41" stroke="#4a2010" stroke-width="2" fill="none" stroke-linecap="round"/>
            <!-- nose -->
            <path d="M53 50 Q55 54 57 50" stroke="#b07850" stroke-width="1.5" fill="none"/>
            <!-- mouth — determined smirk -->
            <path d="M48 58 Q55 62 62 58" stroke="#b07850" stroke-width="1.8" fill="none" stroke-linecap="round"/>
            <!-- scar -->
            <line x1="44" y1="44" x2="40" y2="54" stroke="rgba(80,20,0,.4)" stroke-width="1.5"/>

            <!-- PROTAGONIST BADGE -->
            <text x="55" y="13" text-anchor="middle" fill="#c9a84c" font-size="9" font-family="serif" font-weight="bold">★ HERO</text>
          </svg>
        </div>
      </div>

      <div class="hero-header">
        <div class="hero-info">
          <h2 id="nameA">Loading…</h2>
          <div class="hero-type-badge" id="typeA">warrior</div>
        </div>
      </div>
      <div class="hp-bar-wrap">
        <div class="hp-bar-label"><span>HP</span><span id="hpLabelA">100</span></div>
        <div class="hp-bar"><div class="hp-fill" id="hpBarA" style="width:100%"></div></div>
      </div>
      <div class="stats-grid">
        <div class="stat"><div class="stat-label">Health</div><div class="stat-value" id="hpA">—</div></div>
        <div class="stat"><div class="stat-label">Attack</div><div class="stat-value" id="atkA">—</div></div>
        <div class="stat"><div class="stat-label">Defense</div><div class="stat-value" id="defA">—</div></div>
        <div class="stat"><div class="stat-label">Speed</div><div class="stat-value" id="spdA">—</div></div>
      </div>
      <div class="equip-tags" id="tagsA"></div>
      <div class="equip-panel">
        <h4>⚙ Equip</h4>
        <div class="equip-grid">
          <button class="equip-btn btn-weapon" onclick="equip('hero1','weapon')">⚔ Weapon</button>
          <button class="equip-btn btn-armor"  onclick="equip('hero1','armor')">🛡 Armor</button>
          <button class="equip-btn btn-power"  onclick="equip('hero1','power')">✨ Power</button>
          <button class="equip-btn btn-buff"   onclick="equip('hero1','buff')">💫 Buff</button>
          <button class="equip-btn btn-reset"  onclick="resetHero('hero1')">↺ Reset</button>
        </div>
      </div>
    </div>

    <!-- ══ VS + FIGHT ══ -->
    <div class="vs-section">
      <div class="vs-badge">VS</div>
      <button class="combat-btn" id="combatBtn" onclick="startCombat()">⚔ FIGHT</button>
    </div>

    <!-- ══ HERO B (Antagonist) ══ -->
    <div class="hero-card" id="cardB">

      <!-- 2D Sprite -->
      <div class="sprite-stage">
        <div class="sprite-wrap" id="spriteB" style="animation-delay:.8s">
          <svg id="svgB" viewBox="0 0 110 180" xmlns="http://www.w3.org/2000/svg" width="110" height="180">
            <!-- Shadow -->
            <ellipse cx="55" cy="175" rx="22" ry="5" fill="rgba(0,0,0,.35)"/>

            <!-- ── BUFF AURA ── -->
            <g class="buff-aura">
              <circle cx="55" cy="90" r="52" fill="none" stroke="#2ecc71" stroke-width="2" opacity=".5">
                <animate attributeName="r" values="48;56;48" dur="1.2s" repeatCount="indefinite"/>
                <animate attributeName="opacity" values=".5;.15;.5" dur="1.2s" repeatCount="indefinite"/>
              </circle>
            </g>

            <!-- ── POWER GLOW ── -->
            <g class="power-glow">
              <circle cx="55" cy="75" r="30" fill="rgba(155,89,182,.22)">
                <animate attributeName="r" values="28;34;28" dur="1s" repeatCount="indefinite"/>
              </circle>
            </g>

            <!-- LEGS (dark menacing) -->
            <line x1="45" y1="130" x2="36" y2="165" stroke="#4a2060" stroke-width="10" stroke-linecap="round"/>
            <line x1="65" y1="130" x2="74" y2="165" stroke="#4a2060" stroke-width="10" stroke-linecap="round"/>

            <!-- BOOTS -->
            <ellipse cx="35" cy="167" rx="10" ry="5" fill="#1a0a30" id="bootLB"/>
            <ellipse cx="75" cy="167" rx="10" ry="5" fill="#1a0a30" id="bootRB"/>
            <!-- boot spikes -->
            <polygon points="28,165 25,158 32,163" fill="#555"/>
            <polygon points="82,165 85,158 78,163" fill="#555"/>

            <!-- BODY / TORSO -->
            <rect x="34" y="76" width="42" height="57" rx="8" fill="#3d1a5c"/>

            <!-- ── ARMOR LAYER ── -->
            <g class="armor-glow" id="armorB">
              <rect x="32" y="74" width="46" height="59" rx="9" fill="none" stroke="#3498db" stroke-width="2.5" opacity=".9"/>
              <rect x="36" y="80" width="38" height="14" rx="4" fill="rgba(52,152,219,.2)"/>
              <rect x="26" y="76" width="13" height="11" rx="3" fill="#2471a3" opacity=".85"/>
              <rect x="71" y="76" width="13" height="11" rx="3" fill="#2471a3" opacity=".85"/>
            </g>

            <!-- CHEST EMBLEM -->
            <path d="M55 88 L51 98 L55 96 L59 98Z" fill="#c9a84c" opacity=".8"/>
            <!-- Shoulder spikes -->
            <polygon points="34,78 28,72 34,84" fill="#6a0dad"/>
            <polygon points="76,78 82,72 76,84" fill="#6a0dad"/>

            <!-- CAPE (flowing) -->
            <path d="M36 80 Q20 120 24 165 Q30 140 55 145 Q80 140 86 165 Q90 120 74 80Z" fill="#2d0a4e" opacity=".7"/>

            <!-- ARMS -->
            <line x1="34" y1="85" x2="14" y2="118" stroke="#3d1a5c" stroke-width="11" stroke-linecap="round"/>
            <line x1="76" y1="85" x2="96" y2="118" stroke="#3d1a5c" stroke-width="11" stroke-linecap="round"/>

            <!-- GAUNTLETS -->
            <rect x="8" y="112" width="13" height="14" rx="4" fill="#2a0840"/>
            <rect x="89" y="112" width="13" height="14" rx="4" fill="#2a0840"/>

            <!-- HANDS -->
            <circle cx="14" cy="120" r="6" fill="#2e1045"/>
            <circle cx="96" cy="120" r="6" fill="#2e1045"/>

            <!-- ── WEAPON (Staff/Orb) ── -->
            <g class="weapon-glow" id="weaponB">
              <line x1="14" y1="116" x2="14" y2="55" stroke="#5a2080" stroke-width="4" stroke-linecap="round"/>
              <circle cx="14" cy="50" r="10" fill="#6a0dad" opacity=".9"/>
              <circle cx="14" cy="50" r="10" fill="none" stroke="#c084fc" stroke-width="1.5">
                <animate attributeName="r" values="9;13;9" dur=".9s" repeatCount="indefinite"/>
                <animate attributeName="opacity" values=".8;.2;.8" dur=".9s" repeatCount="indefinite"/>
              </circle>
              <circle cx="14" cy="50" r="5" fill="#e879f9" opacity=".85"/>
            </g>

            <!-- NECK -->
            <rect x="49" y="58" width="12" height="20" rx="5" fill="#3d1a5c"/>

            <!-- HEAD -->
            <circle cx="55" cy="46" r="26" fill="#c8a0d8" id="head-B"/>

            <!-- HAIR — antagonist white/silver -->
            <path d="M29 38 Q30 14 55 14 Q80 14 81 38 Q74 20 55 22 Q36 20 29 38Z" fill="#ddd"/>
            <path d="M29 40 Q24 32 26 22 Q32 10 55 10 Q64 12 64 16 Q55 14 46 16 Q32 20 29 40Z" fill="#eee"/>
            <!-- flowing side strands -->
            <path d="M29 40 Q22 55 24 70" stroke="#ddd" stroke-width="5" fill="none" stroke-linecap="round"/>
            <path d="M81 40 Q88 55 86 70" stroke="#ddd" stroke-width="5" fill="none" stroke-linecap="round"/>

            <!-- FACE -->
            <!-- eyes (cold, menacing — purple glow) -->
            <ellipse cx="44" cy="45" rx="5" ry="4" fill="#1a0030"/>
            <ellipse cx="66" cy="45" rx="5" ry="4" fill="#1a0030"/>
            <ellipse cx="44" cy="45" rx="3" ry="3" fill="#8b00ff"/>
            <ellipse cx="66" cy="45" rx="3" ry="3" fill="#8b00ff"/>
            <ellipse cx="44" cy="45" rx="3" ry="3" fill="none" stroke="#c084fc" stroke-width="1">
              <animate attributeName="rx" values="3;4;3" dur="2s" repeatCount="indefinite"/>
            </ellipse>
            <ellipse cx="66" cy="45" rx="3" ry="3" fill="none" stroke="#c084fc" stroke-width="1">
              <animate attributeName="rx" values="3;4;3" dur="2s" repeatCount="indefinite"/>
            </ellipse>
            <!-- eyebrows — sharp arched -->
            <path d="M39 38 Q44 35 49 38" stroke="#888" stroke-width="2" fill="none" stroke-linecap="round"/>
            <path d="M61 38 Q66 35 71 38" stroke="#888" stroke-width="2" fill="none" stroke-linecap="round"/>
            <!-- nose — sharp -->
            <path d="M53 50 Q55 56 57 50" stroke="#9a6ab0" stroke-width="1.5" fill="none"/>
            <!-- mouth — thin cold smile -->
            <path d="M47 60 Q55 57 63 60" stroke="#9a6ab0" stroke-width="1.8" fill="none" stroke-linecap="round"/>
            <!-- dark mark under eye -->
            <path d="M40 50 Q43 53 46 51" stroke="rgba(100,0,150,.5)" stroke-width="1.5" fill="none"/>
            <path d="M64 50 Q67 53 70 51" stroke="rgba(100,0,150,.5)" stroke-width="1.5" fill="none"/>

            <!-- ANTAGONIST BADGE -->
            <text x="55" y="8" text-anchor="middle" fill="#9b59b6" font-size="9" font-family="serif" font-weight="bold">☠ VILLAIN</text>
          </svg>
        </div>
      </div>

      <div class="hero-header">
        <div class="hero-info">
          <h2 id="nameB">Loading…</h2>
          <div class="hero-type-badge" id="typeBEl">mage</div>
        </div>
      </div>
      <div class="hp-bar-wrap">
        <div class="hp-bar-label"><span>HP</span><span id="hpLabelB">100</span></div>
        <div class="hp-bar"><div class="hp-fill" id="hpBarB" style="width:100%"></div></div>
      </div>
      <div class="stats-grid">
        <div class="stat"><div class="stat-label">Health</div><div class="stat-value" id="hpB">—</div></div>
        <div class="stat"><div class="stat-label">Attack</div><div class="stat-value" id="atkB">—</div></div>
        <div class="stat"><div class="stat-label">Defense</div><div class="stat-value" id="defB">—</div></div>
        <div class="stat"><div class="stat-label">Speed</div><div class="stat-value" id="spdB">—</div></div>
      </div>
      <div class="equip-tags" id="tagsB"></div>
      <div class="equip-panel">
        <h4>⚙ Equip</h4>
        <div class="equip-grid">
          <button class="equip-btn btn-weapon" onclick="equip('hero2','weapon')">⚔ Weapon</button>
          <button class="equip-btn btn-armor"  onclick="equip('hero2','armor')">🛡 Armor</button>
          <button class="equip-btn btn-power"  onclick="equip('hero2','power')">✨ Power</button>
          <button class="equip-btn btn-buff"   onclick="equip('hero2','buff')">💫 Buff</button>
          <button class="equip-btn btn-reset"  onclick="resetHero('hero2')">↺ Reset</button>
        </div>
      </div>
    </div>
  </div>

  <!-- Combat Log -->
  <div class="combat-section">
    <h3>📜 Battle Chronicle</h3>
    <div class="log-container" id="logContainer">
      <div class="log-entry info">Awaiting battle… Equip your heroes and press FIGHT.</div>
    </div>
    <div class="winner-banner" id="winnerBanner">
      <h2 id="winnerText">Winner!</h2>
      <p id="winnerSub"></p>
    </div>
  </div>
</div>

<script>
const WEAPONS = [
  {name:'Iron Sword',attack:10},{name:'Flaming Blade',attack:18},
  {name:'Shadow Dagger',attack:14},{name:'Holy Claymore',attack:22},
  {name:'Venom Fang',attack:16},{name:'Storm Axe',attack:20}
];
const ARMORS = [
  {name:'Leather Vest',defense:8,health:15},{name:'Chain Mail',defense:12,health:25},
  {name:'Dragon Scale',defense:18,health:35},{name:'Void Plate',defense:22,health:45},
  {name:'Mithril Robes',defense:10,health:20}
];
const POWERS = [
  {name:'Fireball',attack:15,speed:3},{name:'Thunder Strike',attack:12,speed:6},
  {name:'Shadow Step',attack:8,speed:12},{name:'Arcane Surge',attack:20,speed:5},
  {name:'Berserker Rage',attack:18,speed:2},{name:'Eagle Eye',attack:10,speed:10}
];
const BUFFS = [
  {name:'Battle Cry',attack:10,defense:5,health:20,speed:3,rounds:3},
  {name:'Potion of Might',attack:15,defense:0,health:30,speed:0,rounds:2},
  {name:'Swift Elixir',attack:5,defense:3,health:10,speed:12,rounds:4},
  {name:'Shield Rune',attack:0,defense:15,health:40,speed:-2,rounds:3},
  {name:'Bloodlust',attack:20,defense:-5,health:0,speed:5,rounds:2}
];

let heroData = {}, baseHp = {};
// track what each hero has equipped (for sprite visual updates)
let heroEquip = { hero1:{weapon:false,armor:false,power:false,buff:false},
                  hero2:{weapon:false,armor:false,power:false,buff:false} };

async function init() {
  const res = await fetch('/api/heroes');
  const heroes = await res.json();
  heroes.forEach(h => { heroData[h.id] = h; baseHp[h.id] = h.health; });
  updateCard('hero1'); updateCard('hero2');
}

function updateCard(id, prevData) {
  const h = heroData[id];
  const s = id === 'hero1' ? 'A' : 'B';
  const card = document.getElementById('card' + s);
  card.className = 'hero-card ' + h.type;

  document.getElementById('name' + s).textContent = h.name;
  const typeEl = document.getElementById(s === 'A' ? 'typeA' : 'typeBEl');
  if (typeEl) typeEl.textContent = h.type.toUpperCase();

  // bump stats
  [['hp'+s,h.health,prevData?.health],['atk'+s,h.attack,prevData?.attack],
   ['def'+s,h.defense,prevData?.defense],['spd'+s,h.speed,prevData?.speed]]
  .forEach(([elId,val,prev]) => {
    const el = document.getElementById(elId);
    if (!el) return;
    el.textContent = val;
    if (prev !== undefined && prev !== val) {
      el.classList.remove('bump'); void el.offsetWidth; el.classList.add('bump');
      setTimeout(() => el.classList.remove('bump'), 400);
    }
  });

  // hp bar
  baseHp[id] = Math.max(h.health, baseHp[id] || h.health);
  const pct = Math.min(100, (h.health / baseHp[id]) * 100);
  document.getElementById('hpBar' + s).style.width = pct + '%';
  document.getElementById('hpLabel' + s).textContent = h.health;

  // tags
  const tagsEl = document.getElementById('tags' + s);
  tagsEl.innerHTML = '';
  (h.description || '').split('|').slice(1).forEach(p => {
    const tag = document.createElement('span');
    tag.className = 'equip-tag';
    const lower = p.toLowerCase();
    if      (lower.includes('weapon')) { tag.classList.add('tag-weapon'); tag.textContent = '⚔ ' + p.split(':')[1]?.split('(')[0]?.trim(); }
    else if (lower.includes('armor'))  { tag.classList.add('tag-armor');  tag.textContent = '🛡 ' + p.split(':')[1]?.split('(')[0]?.trim(); }
    else if (lower.includes('power'))  { tag.classList.add('tag-power');  tag.textContent = '✨ ' + p.split(':')[1]?.split('(')[0]?.trim(); }
    else if (lower.includes('buff'))   { tag.classList.add('tag-buff');   tag.textContent = '💫 ' + p.split(':')[1]?.split('(')[0]?.trim(); }
    if (tag.textContent.trim()) tagsEl.appendChild(tag);
  });

  // update sprite equip classes
  updateSpriteEquip(id);
}

function updateSpriteEquip(id) {
  const s = id === 'hero1' ? 'A' : 'B';
  const wrap = document.getElementById('sprite' + s);
  if (!wrap) return;
  const eq = heroEquip[id];
  wrap.classList.toggle('has-weapon', eq.weapon);
  wrap.classList.toggle('has-armor',  eq.armor);
  wrap.classList.toggle('has-power',  eq.power);
  wrap.classList.toggle('has-buff',   eq.buff);
}

async function equip(heroId, category) {
  let item, payload;
  switch(category) {
    case 'weapon': item = WEAPONS[Math.floor(Math.random()*WEAPONS.length)];
      payload = {heroId,category,itemName:item.name,attack:item.attack}; break;
    case 'armor':  item = ARMORS[Math.floor(Math.random()*ARMORS.length)];
      payload = {heroId,category,itemName:item.name,defense:item.defense,health:item.health}; break;
    case 'power':  item = POWERS[Math.floor(Math.random()*POWERS.length)];
      payload = {heroId,category,itemName:item.name,attack:item.attack,speed:item.speed}; break;
    case 'buff':   item = BUFFS[Math.floor(Math.random()*BUFFS.length)];
      payload = {heroId,category,itemName:item.name,
        attack:item.attack,defense:item.defense,health:item.health,speed:item.speed,rounds:item.rounds}; break;
  }

  const prev = {...heroData[heroId]};
  const res = await fetch('/api/equip',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(payload)});
  const updated = await res.json();
  heroData[heroId] = updated;
  heroEquip[heroId][category] = true;

  const s = heroId === 'hero1' ? 'A' : 'B';
  const wrap = document.getElementById('sprite' + s);
  // pulse animation on sprite
  wrap.style.animation = 'none'; void wrap.offsetWidth;
  wrap.style.animation = 'heroPulse .6s ease';
  setTimeout(() => { wrap.style.animation = ''; }, 700);

  updateCard(heroId, prev);
  burstParticles(wrap, category);
  const icons = {weapon:'⚔',armor:'🛡',power:'✨',buff:'💫'};
  showToast(icons[category] + ' ' + item.name + ' → ' + updated.name);
}

async function resetHero(heroId) {
  const current = heroData[heroId];
  const res = await fetch('/api/reset',{method:'POST',headers:{'Content-Type':'application/json'},
    body:JSON.stringify({heroId,name:current.name,heroType:current.type})});
  const updated = await res.json();
  heroData[heroId] = updated;
  baseHp[heroId] = updated.health;
  heroEquip[heroId] = {weapon:false,armor:false,power:false,buff:false};
  updateCard(heroId);
  showToast('↺ ' + updated.name + ' reset');
  document.getElementById('winnerBanner').style.display = 'none';
}

async function startCombat() {
  const btn = document.getElementById('combatBtn');
  btn.disabled = true; btn.textContent = '⚔ Fighting…';
  document.getElementById('logContainer').innerHTML = '';
  document.getElementById('winnerBanner').style.display = 'none';

  ['hero1','hero2'].forEach(id => {
    baseHp[id] = heroData[id].health;
    const s = id === 'hero1' ? 'A' : 'B';
    document.getElementById('hpBar'+s).style.width = '100%';
    document.getElementById('hpLabel'+s).textContent = heroData[id].health;
  });

  const res = await fetch('/api/combat',{method:'POST',headers:{'Content-Type':'application/json'},
    body:JSON.stringify({heroAId:'hero1',heroBId:'hero2'})});
  const result = await res.json();

  const log = document.getElementById('logContainer');
  let delay = 0;
  result.log.forEach((line) => {
    setTimeout(() => {
      const div = document.createElement('div');
      div.className = 'log-entry';
      if      (line.startsWith('──'))  div.classList.add('round');
      else if (line.startsWith('🏆')||line.startsWith('💀')||line.startsWith('⏱')) div.classList.add('win');
      else if (line.includes('Stats →')) div.classList.add('info');
      else    div.classList.add('attack');
      div.textContent = line;
      log.appendChild(div);
      log.scrollTop = log.scrollHeight;

      if (line.includes('attacks')) {
        const isA = line.startsWith(heroData['hero1'].name);
        const attackerS = isA ? 'A' : 'B';
        const targetS   = isA ? 'B' : 'A';
        // attacker lunges
        const atk = document.getElementById('sprite' + attackerS);
        atk.classList.remove('attack-sprite'); void atk.offsetWidth; atk.classList.add('attack-sprite');
        setTimeout(()=>atk.classList.remove('attack-sprite'),500);
        // target shakes
        const tgt = document.getElementById('sprite' + targetS);
        tgt.classList.remove('shake-sprite'); void tgt.offsetWidth; tgt.classList.add('shake-sprite');
        setTimeout(()=>tgt.classList.remove('shake-sprite'),550);
        // update hp bar
        const hpMatch = line.match(/HP: (\\d+)/);
        if (hpMatch) {
          const hpNow = parseInt(hpMatch[1]);
          const maxH = baseHp[targetS === 'A' ? 'hero1' : 'hero2'];
          document.getElementById('hpBar'+targetS).style.width = Math.max(0,(hpNow/maxH)*100) + '%';
          document.getElementById('hpLabel'+targetS).textContent = hpNow;
        }
      }
    }, delay);
    delay += line.startsWith('──') ? 220 : 160;
  });

  setTimeout(() => {
    flashScreen();
    const banner = document.getElementById('winnerBanner');
    document.getElementById('winnerText').textContent =
      result.winner === 'DRAW' ? '⚔ DRAW ⚔' : '🏆 ' + result.winner + ' WINS!';
    document.getElementById('winnerSub').textContent =
      result.totalRounds + ' rounds • ' +
      heroData['hero1'].name + ': ' + result.heroAFinalHp + 'HP  —  ' +
      heroData['hero2'].name + ': ' + result.heroBFinalHp + 'HP';
    banner.style.display = 'block';
    btn.disabled = false; btn.textContent = '⚔ FIGHT AGAIN';
  }, delay + 200);
}

function showToast(msg) {
  const t = document.getElementById('toast');
  document.getElementById('toastMsg').textContent = msg;
  t.classList.add('show');
  setTimeout(() => t.classList.remove('show'), 2600);
}
function flashScreen() {
  const f = document.getElementById('flashOverlay');
  f.classList.remove('active'); void f.offsetWidth; f.classList.add('active');
}
function burstParticles(el, category) {
  const colors = {weapon:'#e74c3c',armor:'#3498db',power:'#9b59b6',buff:'#2ecc71'};
  const color = colors[category] || '#c9a84c';
  const rect = el.getBoundingClientRect();
  const cx = rect.left + rect.width/2, cy = rect.top + rect.height/2;
  const cont = document.getElementById('particlesCont');
  for (let i = 0; i < 14; i++) {
    const p = document.createElement('div');
    p.className = 'particle';
    const sz = 4 + Math.random()*7;
    const angle = (i/14)*Math.PI*2;
    const dist = 50 + Math.random()*70;
    p.style.cssText = `left:${cx}px;top:${cy}px;width:${sz}px;height:${sz}px;background:${color};--tx:${Math.cos(angle)*dist}px;--ty:${Math.sin(angle)*dist-40}px;--dur:${.5+Math.random()*.5}s`;
    cont.appendChild(p);
    setTimeout(()=>p.remove(),1100);
  }
}

// CSS keyframe for sprite pulse (injected)
const style = document.createElement('style');
style.textContent = '@keyframes heroPulse{0%{transform:translateY(0) scale(1)}40%{transform:translateY(-12px) scale(1.1)}100%{transform:translateY(0) scale(1)}}';
document.head.appendChild(style);

init();
</script>
</body>
</html>
""";
        }
    }
}