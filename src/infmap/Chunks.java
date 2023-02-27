package infmap;

import arc.struct.*;
import arc.util.*;
import java.util.*;

import mindustry.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.Vars.*;
import mindustry.world.*;
import mindustry.world.blocks.*;
import mindustry.world.blocks.storage.*;
import arc.*;
import arc.func.*;
import arc.math.geom.*;
import arc.util.io.*;
import mindustry.content.*;
import mindustry.content.TechTree.*;
import mindustry.core.*;
import mindustry.ctype.*;
import mindustry.entities.*;
import mindustry.game.*;
import mindustry.game.Teams.*;
import mindustry.gen.*;
import mindustry.maps.Map;
import mindustry.world.*;
import mindustry.io.SaveVersion.*;
import mindustry.io.SaveFileReader;
import mindustry.net.*;
import mindustry.net.Administration.*;
import mindustry.net.Packets.*;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.util.zip.*;


import arc.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.core.*;
import mindustry.ctype.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.io.*;
import mindustry.logic.*;
import mindustry.maps.Map;
import mindustry.net.Administration.*;

import static mindustry.Vars.*;

public class Chunks{
    
    public interface IORunner<T>{
        void accept(T stream) throws IOException;
    }
    
	private static int chunkW = 200; // width of chunk
    private static int chunkH = 200; // height of chunk
    private static HashMap<WorldChunkPosition, WorldChunk> chunks = new HashMap<>(); // map of chunks, keyed by chunkX, chunkY

    protected static final ReusableByteOutStream byteOutput = new ReusableByteOutStream(), byteOutput2 = new ReusableByteOutStream();
    protected static final DataOutputStream dataBytes = new DataOutputStream(byteOutput), dataBytes2 = new DataOutputStream(byteOutput2);
    protected static final ReusableByteOutStream byteOutputSmall = new ReusableByteOutStream();
    protected static final DataOutputStream dataBytesSmall = new DataOutputStream(byteOutputSmall);
    protected static boolean chunkNested = false;

    public static void writeChunk(DataOutput output, IORunner<DataOutput> runner) throws IOException{
        writeChunk(output, false, runner);
    }

    /** Write a chunk of input to the stream. An integer of some length is written first, followed by the data. */
    public static void writeChunk(DataOutput output, boolean isShort, IORunner<DataOutput> runner) throws IOException{

        //TODO awful
        boolean wasNested = chunkNested;
        if(!isShort){
            chunkNested = true;
        }
        ReusableByteOutStream dout =
            isShort ? byteOutputSmall :
            wasNested ? byteOutput2 :
            byteOutput;
        try{
            //reset output position
            dout.reset();
            //write the needed info
            runner.accept(
                isShort ? dataBytesSmall :
                wasNested ? dataBytes2 :
                dataBytes
            );

            int length = dout.size();
            //write length (either int or byte) followed by the output bytes
            if(!isShort){
                output.writeInt(length);
            }else{
                if(length > 65535){
                    throw new IOException("Byte write length exceeded: " + length + " > 65535");
                }
                output.writeShort(length);
            }
            output.write(dout.getBytes(), 0, length);
        }finally{
            chunkNested = wasNested;
        }
    }


    public static class ChunkBlock {
        private int x;
        private int y;
        private Block block;
        private Block floor;
        private Block overlay;
        private int rotation;
        private Team team;
        private byte data;
        private Building build;

        public ChunkBlock(Tile tile, WorldChunk chunk) {
            this.x = tile.x;
            this.y = tile.y;
            this.block = tile.block();
            this.floor = tile.floor();
            this.overlay = tile.overlay();
            if (tile.build != null) {
                this.build = tile.build;
                this.rotation = tile.build.rotation();
            } else {
                this.rotation = 0;
            }
            this.team = tile.team();
            this.data = tile.data;
            if (this.block.isMultiblock()) {
                Seq<Tile> linked = new Seq<>();
                linked = tile.getLinkedTiles(linked);
                linked.forEach(t -> {
                    if (t != tile) chunk.putBlock(t.x, t.y, new ChunkBlock(t, this.build));
                });
                linked.forEach(t -> {
                    log(t);
                });
            }
        }
        public ChunkBlock(Tile tile, Building build) {
            this.x = tile.x;
            this.y = tile.y;
            this.block = build.tile.block();
            this.floor = tile.floor();
            this.overlay = build.tile.overlay();
            this.build = build;
            this.rotation = build.rotation();
            this.team = build.tile.team();
            this.data = build.tile.data;
        }
        public ChunkBlock(Tile tile) {
            this.x = tile.x;
            this.y = tile.y;
            this.block = tile.block();
            this.floor = tile.floor();
            this.overlay = tile.overlay();
            if (tile.build != null) {
                this.build = tile.build;
                this.rotation = tile.build.rotation();
            } else {
                this.rotation = 0;
            }
            this.team = tile.team();
            this.data = tile.data;
        }
        public ChunkBlock(int x, int y) {
            this.x = x;
            this.y = y;
            this.block = Blocks.air;
            this.floor = Vars.world.tile(x, y).floor();
            this.overlay = Blocks.air;
            this.rotation = 0;
            this.team = Team.derelict;
        }

        // getters and setters
        public int getX() { return x; }
        public int getY() { return y; }
        public Block getBlock() { return block; }
        public int getRotation() { return rotation; }
        public Team getTeam() { return team; }
        public byte getData() { return data; }
        public Tile toTile() {
            Tile tile = new Tile(this.x, this.y, this.floor, this.overlay, this.block);
            tile.setTeam(this.team);
            tile.data = this.data;
            tile.build = this.build;
            if (tile.build != null) tile.build.rotation(this.rotation);
            return tile;
        }
    }

    public static class WorldChunk {
        private ChunkBlock[][] blocks;

        public WorldChunk(ChunkBlock[][] blocks) {
            this.blocks = blocks;
        }

        public WorldChunk() {
            this.blocks = new ChunkBlock[Vars.world.width()][Vars.world.height()];
            for (int x = 0; x < Vars.world.width(); x++) {
                for (int y = 0; y < Vars.world.height(); y++) {
                    Tile tile = new Tile(x, y, Vars.world.tile(x, y).floor(), Blocks.air, Blocks.air);
                    this.blocks[x][y] = new ChunkBlock(tile);
                }
            }
        }

        public void putBlock(int x, int y, ChunkBlock block){
            this.blocks[x][y] = block;
        }
        public ChunkBlock getBlock(int x, int y){
            return this.blocks[x][y];
        }

        public int width() {return Vars.world.width();}
        public int height() {return Vars.world.height();}
        public Tile rawTile(int x, int y) {
            if(x < 0 || x >= width() || y < 0 || y >= height()) throw new IllegalArgumentException(x + ", " + y + " out of bounds: width=" + width() + ", height=" + height());
            return blocks[x][y].toTile();
        }
    }


    public class WorldChunkPosition {
        public int x;
        public int y;

        public WorldChunkPosition(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof WorldChunkPosition) {
                WorldChunkPosition other = (WorldChunkPosition) obj;
                return this.x == other.x && this.y == other.y;
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.x, this.y);
        }
    }
    
    public static void updateChunk(WorldChunkPosition chunkpos, int x, int y, Tile tile) {
        log("tile "+tile);
        chunks.putIfAbsent(chunkpos, new WorldChunk());
        // get the chunk for the current player position
        WorldChunk chunk = chunks.get(chunkpos);

        // replace block in chunk with new block
        if (tile != null){
            chunk.putBlock(x, y, new ChunkBlock(tile, chunk));
        }else{
            chunk.putBlock(x, y, new ChunkBlock(x, y));
        }

        chunks.put(chunkpos, chunk);
    }
    public static void sendChunk(mindustry.gen.Player player, WorldChunkPosition chunkpos) {
        Call.worldDataBegin(player.con);
        sendWorldData(player, chunkpos);
    }

    private static void log(Object text){
        Log.info("[INFMAP] " + text);
    }

    public static void sendWorldData(Player player, WorldChunkPosition chunkpos){
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        DeflaterOutputStream def = new FastDeflaterOutputStream(stream);
        chunks.putIfAbsent(chunkpos, new WorldChunk());
        writeWorld(player, chunks.get(chunkpos), def);
        WorldStream data = new WorldStream();
        data.stream = new ByteArrayInputStream(stream.toByteArray());
        player.con.sendStream(data);

        log("Packed "+stream.size()+" bytes of data of world chunk at ("+chunkpos.x+", "+chunkpos.y+").");
    }

    public static void writeMap(Player player, WorldChunk world, DataOutput stream) throws IOException {
        stream.writeShort(world.width());
        stream.writeShort(world.height());
        //floor + overlay
        for(int i = 0; i < world.width() * world.height(); i++){
            Tile tile = world.rawTile(i % world.width(), i / world.width());
            stream.writeShort(tile.floorID());
            stream.writeShort(tile.overlayID());
            int consecutives = 0;
            for(int j = i + 1; j < world.width() * world.height() && consecutives < 255; j++){
                Tile nextTile = world.rawTile(j % world.width(), j / world.width());
                if(nextTile.floorID() != tile.floorID() || nextTile.overlayID() != tile.overlayID()){
                    break;
                }
                consecutives++;
            }
            stream.writeByte(consecutives);
            i += consecutives;
        }
        //blocks
        for(int i = 0; i < world.width() * world.height(); i++){
            Tile tile = world.rawTile(i % world.width(), i / world.width());
            stream.writeShort(tile.blockID());
            boolean savedata = tile.block().saveData;
            byte packed = (byte)((tile.build != null ? 1 : 0) | (savedata ? 2 : 0));
            //make note of whether there was an entity/rotation here
            stream.writeByte(packed);
            //only write the entity for multiblocks once - in the center
            if(tile.build != null){
                if(tile.isCenter()){
                    stream.writeBoolean(true);
                    writeChunk(stream, true, out -> {
                        out.writeByte(tile.build.version());
                        tile.build.writeAll(Writes.get(out));
                    });
                }else{
                    stream.writeBoolean(false);
                }
            }else if(savedata){
                stream.writeByte(tile.data);
            }else{
                //write consecutive non-entity blocks
                int consecutives = 0;
                for(int j = i + 1; j < world.width() * world.height() && consecutives < 255; j++){
                    Tile nextTile = world.rawTile(j % world.width(), j / world.width());
                    if(nextTile.blockID() != tile.blockID()){
                        break;
                    }
                    consecutives++;
                }
                stream.writeByte(consecutives);
                i += consecutives;
            }
        }
    }
    public static void writeWorld(Player player, WorldChunk world, OutputStream os){
        try(DataOutputStream stream = new DataOutputStream(os)){
            //write all researched content to rules if hosting
            if(state.isCampaign()){
                state.rules.researched.clear();
                for(ContentType type : ContentType.all){
                    for(Content c : content.getBy(type)){
                        if(c instanceof UnlockableContent u && u.unlocked() && u.techNode != null){
                            state.rules.researched.add(u.name);
                        }
                    }
                }
            }

            stream.writeUTF(JsonIO.write(state.rules));
            SaveIO.getSaveWriter().writeStringMap(stream, state.map.tags);

            stream.writeInt(state.wave);
            stream.writeFloat(state.wavetime);
            stream.writeDouble(state.tick);
            stream.writeLong(GlobalVars.rand.seed0);
            stream.writeLong(GlobalVars.rand.seed1);

            stream.writeInt(player.id);
            player.write(new Writes(stream));

            SaveIO.getSaveWriter().writeContentHeader(stream);
            writeMap(player, world, stream);
            SaveIO.getSaveWriter().writeTeamBlocks(stream);
            SaveIO.getSaveWriter().writeCustomChunks(stream, true);
        }catch(IOException e){
            throw new RuntimeException(e);
        }
    }
}