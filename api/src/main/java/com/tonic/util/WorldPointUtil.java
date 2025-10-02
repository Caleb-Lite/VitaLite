package com.tonic.util;

import com.tonic.Static;
import net.runelite.api.Client;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static net.runelite.api.Constants.*;

/**
 * Utility methods for working with WorldPoints and WorldAreas
 */
public class WorldPointUtil {
    /**
     * Gets the coordinate of the tile that contains the passed world point,
     * accounting for instances.
     *
     * @param worldPoint the instance worldpoint
     * @return the tile coordinate containing the local point
     */
    public static WorldPoint get(WorldPoint worldPoint)
    {
        return fromInstance(worldPoint);
    }

    /**
     * Gets the coordinate of the tile that contains the passed world point,
     * accounting for instances.
     *
     * @param worldPoint the local worldpoint
     * @return the tile coordinate containing the local point
     */
    public static WorldPoint translate(WorldPoint worldPoint)
    {
        return toInstance(worldPoint).get(0);
    }

    /**
     * Gets the coordinate of the tile that contains the passed world point,
     * accounting for instances.
     *
     * @param worldPoint the instance worldpoint
     * @return the tile coordinate containing the local point
     */
    public static WorldPoint fromInstance(WorldPoint worldPoint)
    {
        //get local
        Client client = Static.getClient();
        WorldView worldView = client.getTopLevelWorldView();
        LocalPoint localPoint = LocalPoint.fromWorld(worldView, worldPoint);

        // if local point is null or not in an instanced region, return the world point as is
        if(localPoint == null || !worldView.isInstance())
            return worldPoint;

        // get position in the scene
        int sceneX = localPoint.getSceneX();
        int sceneY = localPoint.getSceneY();

        // get chunk from scene
        int chunkX = sceneX / CHUNK_SIZE;
        int chunkY = sceneY / CHUNK_SIZE;

        // get the template chunk for the chunk
        int[][][] instanceTemplateChunks = worldView.getInstanceTemplateChunks();
        int templateChunk = instanceTemplateChunks[worldPoint.getPlane()][chunkX][chunkY];

        int rotation = templateChunk >> 1 & 0x3;
        int templateChunkY = (templateChunk >> 3 & 0x7FF) * CHUNK_SIZE;
        int templateChunkX = (templateChunk >> 14 & 0x3FF) * CHUNK_SIZE;
        int templateChunkPlane = templateChunk >> 24 & 0x3;

        // calculate world point of the template
        int x = templateChunkX + (sceneX & (CHUNK_SIZE - 1));
        int y = templateChunkY + (sceneY & (CHUNK_SIZE - 1));

        // create and rotate point back to 0, to match with template
        return rotate(new WorldPoint(x, y, templateChunkPlane), 4 - rotation);
    }

    /**
     * Get all possible instance world points for the given world point.
     * @param worldPoint worldpoint
     * @return the list of possible instance world points
     */
    public static ArrayList<WorldPoint> toInstance(WorldPoint worldPoint)
    {
        Client client = Static.getClient();
        WorldView worldView = client.getTopLevelWorldView();

        // if not in an instanced region, return the world point as is
        if (!worldView.isInstance())
        {
            return new ArrayList<>(Collections.singletonList(worldPoint));
        }

        // find instance chunks using the template point. there might be more than one.
        ArrayList<WorldPoint> worldPoints = new ArrayList<>();
        int[][][] instanceTemplateChunks = worldView.getInstanceTemplateChunks();
        for (int z = 0; z < instanceTemplateChunks.length; z++)
        {
            for (int x = 0; x < instanceTemplateChunks[z].length; ++x)
            {
                for (int y = 0; y < instanceTemplateChunks[z][x].length; ++y)
                {
                    int chunkData = instanceTemplateChunks[z][x][y];
                    int rotation = chunkData >> 1 & 0x3;
                    int templateChunkY = (chunkData >> 3 & 0x7FF) * CHUNK_SIZE;
                    int templateChunkX = (chunkData >> 14 & 0x3FF) * CHUNK_SIZE;
                    int plane = chunkData >> 24 & 0x3;
                    if (worldPoint.getX() >= templateChunkX && worldPoint.getX() < templateChunkX + CHUNK_SIZE
                            && worldPoint.getY() >= templateChunkY && worldPoint.getY() < templateChunkY + CHUNK_SIZE
                            && plane == worldPoint.getPlane())
                    {
                        WorldPoint p = new WorldPoint(worldView.getBaseX() + x * CHUNK_SIZE + (worldPoint.getX() & (CHUNK_SIZE - 1)),
                                worldView.getBaseY() + y * CHUNK_SIZE + (worldPoint.getY() & (CHUNK_SIZE - 1)),
                                z);
                        p = rotate(p, rotation);
                        worldPoints.add(p);
                    }
                }
            }
        }
        if(worldPoints.isEmpty())
            worldPoints.add(worldPoint);
        return worldPoints;
    }

    /**
     * Rotate the coordinates in the chunk according to chunk rotation
     *
     * @param point    point
     * @param rotation rotation
     * @return world point
     */
    private static WorldPoint rotate(WorldPoint point, int rotation)
    {
        int chunkX = point.getX() & -CHUNK_SIZE;
        int chunkY = point.getY() & -CHUNK_SIZE;
        int x = point.getX() & (CHUNK_SIZE - 1);
        int y = point.getY() & (CHUNK_SIZE - 1);
        switch (rotation)
        {
            case 1:
                return new WorldPoint(chunkX + y, chunkY + (CHUNK_SIZE - 1 - x), point.getPlane());
            case 2:
                return new WorldPoint(chunkX + (CHUNK_SIZE - 1 - x), chunkY + (CHUNK_SIZE - 1 - y), point.getPlane());
            case 3:
                return new WorldPoint(chunkX + (CHUNK_SIZE - 1 - y), chunkY + x, point.getPlane());
        }
        return point;
    }

    /**
     * Compresses a WorldPoint into a single integer.
     * @param wp the WorldPoint to compress
     * @return the compressed WorldPoint
     */
    public static int compress(WorldPoint wp) {
        return wp.getX() | wp.getY() << 14 | wp.getPlane() << 29;
    }

    /**
     * Compresses x, y, z coordinates into a single integer.
     * @param x the x coordinate (0 - 16383)
     * @param y the y coordinate (0 - 32767)
     * @param z the z coordinate (0 - 7)
     * @return the compressed coordinates
     */
    public static int compress(int x, int y, int z) {
        return x | y << 14 | z << 29;
    }

    /**
     * Decompresses a compressed WorldPoint integer back into a WorldPoint.
     * @param compressed the compressed WorldPoint
     * @return the decompressed WorldPoint
     */
    public static WorldPoint fromCompressed(int compressed)
    {
        int x = compressed & 0x3FFF;
        int y = (compressed >>> 14) & 0x7FFF;
        int z = (compressed >>> 29) & 7;
        return new WorldPoint(x, y, z);
    }

    /**
     * Extracts the X coordinate from a compressed WorldPoint integer.
     * @param compressed the compressed WorldPoint
     * @return the X coordinate
     */
    public static short getCompressedX(int compressed)
    {
        return (short) (compressed & 0x3FFF);
    }

    /**
     * Extracts the Y coordinate from a compressed WorldPoint integer.
     * @param compressed the compressed WorldPoint
     * @return the Y coordinate
     */
    public static short getCompressedY(int compressed)
    {
        return (short) ((compressed >>> 14) & 0x7FFF);
    }

    /**
     * Extracts the plane from a compressed WorldPoint integer.
     * @param compressed the compressed WorldPoint
     * @return the plane
     */
    public static byte getCompressedPlane(int compressed)
    {
        return (byte)((compressed >>> 29) & 7);
    }

    /**
     * Offsets the compressed WorldPoint by the given amounts in each dimension.
     * @param compressed the compressed WorldPoint
     * @param n the amount to offset in the X direction
     * @return the new compressed WorldPoint
     */
    public static int dx(int compressed, int n)
    {
        return compressed + n;
    }

    /**
     * Offsets the compressed WorldPoint by the given amounts in each dimension.
     * @param compressed the compressed WorldPoint
     * @param n the amount to offset in the Y direction
     * @return the new compressed WorldPoint
     */
    public static int dy(int compressed, int n)
    {
        return compressed + (n << 14);
    }

    /**
     * Offsets the compressed WorldPoint by the given amounts in each dimension.
     * @param compressed the compressed WorldPoint
     * @param nx the amount to offset in the X direction
     * @param ny the amount to offset in the Y direction
     * @return the new compressed WorldPoint
     */
    public static int dxy(int compressed, int nx, int ny)
    {
        return compressed + nx + (ny << 14);
    }

    /**
     * compresses all the points in the given WorldAreas into a single array of compressed points
     * @param area the WorldAreas
     * @return the compressed points
     */
    public static int[] toCompressedPoints(WorldArea... area)
    {
        List<WorldPoint> points = new ArrayList<>();
        for(WorldArea a : area)
        {
            points.addAll(a.toWorldPointList());
        }
        int[] compressed = new int[points.size()];
        for(int i = 0; i < points.size(); i++)
        {
            compressed[i] = compress(points.get(i));
        }
        return compressed;
    }

    /**
     * Gets the center point of a WorldArea
     * @param area the WorldArea
     * @return the center point
     */
    public static WorldPoint getCenter(WorldArea area)
    {
        int x = area.getX();
        int y = area.getY();
        int width = area.getWidth();
        int height = area.getHeight();
        int plane = area.getPlane();
        return new WorldPoint(x + (width / 2), y + (height / 2), plane);
    }
}
