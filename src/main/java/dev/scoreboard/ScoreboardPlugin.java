package dev.scoreboard;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Statistic;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.RenderType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ScoreboardPlugin extends JavaPlugin implements Listener {

    private static final int LINES = 11;

    private final Map<UUID, Board> boards = new HashMap<>();

    private String title;
    private TextColor gradientFrom;
    private TextColor gradientTo;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        title = getConfig().getString("title", "Scoreboard");
        gradientFrom = parseColor(getConfig().getString("gradient-from"), 0x55FFFF);
        gradientTo = parseColor(getConfig().getString("gradient-to"), 0xAA55FF);
        long interval = Math.max(1L, getConfig().getLong("update-interval", 5L));

        Bukkit.getPluginManager().registerEvents(this, this);

        for (Player player : Bukkit.getOnlinePlayers()) {
            createBoard(player);
        }

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                Board board = boards.get(player.getUniqueId());
                if (board == null) {
                    board = createBoard(player);
                }
                update(player, board);
            }
        }, 1L, interval);
    }

    @Override
    public void onDisable() {
        for (UUID id : boards.keySet()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            }
        }
        boards.clear();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        createBoard(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        boards.remove(event.getPlayer().getUniqueId());
    }

    private static TextColor parseColor(String hex, int fallback) {
        if (hex != null) {
            TextColor parsed = TextColor.fromHexString(hex.startsWith("#") ? hex : "#" + hex);
            if (parsed != null) {
                return parsed;
            }
        }
        return TextColor.color(fallback);
    }

    // ------------------------------------------------------------------ board

    private static final class Board {
        final Scoreboard scoreboard;
        final Team[] teams = new Team[LINES];
        final String[] cache = new String[LINES];

        Board(Scoreboard scoreboard) {
            this.scoreboard = scoreboard;
        }
    }

    private Board createBoard(Player player) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective(
                "sidebar", Criteria.DUMMY, gradientTitle(), RenderType.INTEGER);
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        // hides the red score numbers on the right
        objective.numberFormat(NumberFormat.blank());

        Board board = new Board(scoreboard);
        for (int i = 0; i < LINES; i++) {
            String entry = "§" + Integer.toHexString(i) + "§r";
            Team team = scoreboard.registerNewTeam("line" + i);
            team.addEntry(entry);
            board.teams[i] = team;
            objective.getScore(entry).setScore(LINES - i);
        }

        boards.put(player.getUniqueId(), board);
        player.setScoreboard(scoreboard);
        update(player, board);
        return board;
    }

    private Component gradientTitle() {
        Component result = Component.empty();
        int n = title.length();
        for (int i = 0; i < n; i++) {
            float ratio = n == 1 ? 0f : (float) i / (n - 1);
            TextColor color = TextColor.lerp(ratio, gradientFrom, gradientTo);
            result = result.append(Component.text(String.valueOf(title.charAt(i)), color)
                    .decorate(TextDecoration.BOLD));
        }
        return result;
    }

    // ----------------------------------------------------------------- update

    private void update(Player player, Board board) {
        Location loc = player.getLocation();

        AttributeInstance maxHealthAttr = player.getAttribute(Attribute.MAX_HEALTH);
        double maxHealth = maxHealthAttr != null ? maxHealthAttr.getValue() : 20.0;

        Runtime rt = Runtime.getRuntime();
        long usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);

        String ip = Bukkit.getIp();
        if (ip == null || ip.isBlank()) {
            ip = "0.0.0.0";
        }

        long totalSeconds = player.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20L;
        String playTime = String.format("%02d:%02d:%02d",
                totalSeconds / 3600, (totalSeconds / 60) % 60, totalSeconds % 60);

        Component[] lines = new Component[LINES];

        lines[0] = label("Player: ")
                .append(Component.text(player.getName(), NamedTextColor.RED))
                .append(sep())
                .append(label("Ping: "))
                .append(value(player.getPing() + " ms"));

        lines[1] = label("Health: ")
                .append(value((int) Math.ceil(player.getHealth()) + "/" + (int) Math.ceil(maxHealth)))
                .append(Component.text("❤", NamedTextColor.GOLD))
                .append(sep())
                .append(label("Food: "))
                .append(value(String.valueOf(player.getFoodLevel())));

        lines[2] = label("World: ")
                .append(value(loc.getWorld().getName()))
                .append(sep())
                .append(label("Time: "))
                .append(value(playTime));

        lines[3] = label("XP: ")
                .append(value(String.valueOf(player.getLevel())));

        lines[4] = Component.empty();

        lines[5] = label("Location: ")
                .append(value("X " + loc.getBlockX() + ", Y " + loc.getBlockY() + ", Z " + loc.getBlockZ()));

        lines[6] = label("Facing: ")
                .append(value("Yaw " + loc.getYaw() + " Pitch " + loc.getPitch()));

        lines[7] = Component.empty();

        lines[8] = label("IP: ")
                .append(value(ip))
                .append(sep())
                .append(label("Port: "))
                .append(value(String.valueOf(Bukkit.getPort())));

        lines[9] = label("Version: ")
                .append(value(Bukkit.getBukkitVersion()));

        lines[10] = label("Players: ")
                .append(value(Bukkit.getOnlinePlayers().size() + "/" + Bukkit.getMaxPlayers()))
                .append(sep())
                .append(label("RAM: "))
                .append(value(usedMb + "MB"));

        for (int i = 0; i < LINES; i++) {
            String key = lines[i].toString();
            if (!key.equals(board.cache[i])) {
                board.cache[i] = key;
                board.teams[i].prefix(lines[i]);
            }
        }
    }

    private static Component label(String text) {
        return Component.text(text, NamedTextColor.GRAY);
    }

    private static Component value(String text) {
        return Component.text(text, NamedTextColor.WHITE);
    }

    private static Component sep() {
        return Component.text(" | ", NamedTextColor.DARK_GRAY);
    }
}
