package infmap;

import arc.*;
import arc.struct.*;
import arc.util.*;
import java.util.*;
import arc.util.Timer;

import mindustry.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.mod.*;
import mindustry.Vars.*;
import mindustry.world.*;
import mindustry.world.blocks.*;
import mindustry.world.blocks.storage.*;

import infmap.Chunks;


public class Main extends Plugin{
    static final int TELEPORT_DISTANCE = 10;
    //Chunks chunks = new Chunks();
    private HashMap<Player, Chunks.WorldChunkPosition> playerInfmapData = new HashMap<>();

    private void log(Object text){
        Log.info("[INFMAP] " + text);
    }

    public Main() {
        log("Initialising...");
        log("WARNING!");
        log("This mod works ONLY on dedictated server and ONLY with one player.");
        log("Report all issues to Benry#5935");
    }

    public void init() {
        // Create a timer to check for players near the world border
        Timer.schedule(() -> {
            Groups.player.each(ply -> {
                // Check if the player is near the world border
                if (nearWorldBorder(ply)) {
                    // Teleport the player to the opposite side of the map
                    teleportPlayer(ply);
                }
            });
        }, 0, 0.02f);
        log("Registered chunk switcher");
        Events.on(EventType.BlockBuildEndEvent.class, event -> {
            playerInfmapData.putIfAbsent(event.unit.getPlayer(), new Chunks().new WorldChunkPosition(0, 0));
            Chunks.WorldChunkPosition chunkPos = playerInfmapData.get(event.unit.getPlayer());
            if(!event.breaking){
                Chunks.updateChunk(chunkPos, (int)event.tile.build.x/8, (int)event.tile.build.y/8, event.tile);
            }else{
                Chunks.updateChunk(chunkPos, (int)event.tile.build.x/8, (int)event.tile.build.y/8, null);
            }
        });
        Events.on(EventType.ConfigEvent.class, event -> {
            playerInfmapData.putIfAbsent(event.player, new Chunks().new WorldChunkPosition(0, 0));
            Chunks.WorldChunkPosition chunkPos = playerInfmapData.get(event.player);
            Chunks.updateChunk(chunkPos, (int)event.tile.tile.build.x/8, (int)event.tile.tile.build.y/8, event.tile.tile);
        });
        Events.on(EventType.PlayerJoin.class, event -> {
            Chunks.sendChunk(event.player, new Chunks().new WorldChunkPosition(0, 0));
        });
        log("Registered infmap block manager");
        log("Initialised!");
    }

    boolean nearWorldBorder(Player player) {
        return player.x/8 < TELEPORT_DISTANCE || player.x/8 > Vars.world.width() - TELEPORT_DISTANCE ||
               player.y/8 < TELEPORT_DISTANCE || player.y/8 > Vars.world.height() - TELEPORT_DISTANCE;
    }

    void teleportPlayer(Player player) {
        int newX = player.x/8 < TELEPORT_DISTANCE ? Vars.world.width() - TELEPORT_DISTANCE : TELEPORT_DISTANCE;
        int newY = player.y/8 < TELEPORT_DISTANCE ? Vars.world.height() - TELEPORT_DISTANCE : TELEPORT_DISTANCE;
        float newTPX = player.x/8 > TELEPORT_DISTANCE && player.x/8 < Vars.world.width() - TELEPORT_DISTANCE ? player.x/8 : newX;
        float newTPY = player.y/8 > TELEPORT_DISTANCE && player.y/8 < Vars.world.height() - TELEPORT_DISTANCE ? player.y/8 : newY;
        playerInfmapData.putIfAbsent(player, new Chunks().new WorldChunkPosition(0, 0));
        Chunks.WorldChunkPosition chunkPos = playerInfmapData.get(player);
        if (player.x/8 < TELEPORT_DISTANCE) {
            chunkPos.x = chunkPos.x - 1;
        }
        if (player.x/8 > Vars.world.width() - TELEPORT_DISTANCE) {
            chunkPos.x = chunkPos.x + 1;
        }
        if (player.y/8 < TELEPORT_DISTANCE) {
            chunkPos.y = chunkPos.y - 1;
        }
        if (player.y/8 > Vars.world.height() - TELEPORT_DISTANCE) {
            chunkPos.y = chunkPos.y + 1;
        }
        playerInfmapData.put(player, chunkPos);
        var unit = player.unit();
        var vel = unit.vel;
        player.clearUnit();
        unit.set(newTPX * 8, newTPY * 8);
        Call.setPosition(player.con, newTPX * 8, newTPY * 8);
        Call.setCameraPosition(player.con, newTPX * 8 - vel.x*8, newTPY * 8 - vel.y*8);
        player.unit(unit);
        player.unit().vel.add(vel);
        Chunks.sendChunk(player, chunkPos);
    }
}

