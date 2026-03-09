/*
 * Copyright (c) 2010-2026. Institut Pasteur.
 *
 * This file is part of Icy.
 * Icy is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Icy is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Icy. If not, see <https://www.gnu.org/licenses/>.
 */

package plugins.nchenouard.particletracking.filtering;

import org.jetbrains.annotations.Contract;

/**
 * A map of the likelihood for target position in space
 * <p>
 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
 *
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com)
 * @version 3.1
 * date 2013-11-13
 * license gpl v3.0
 */
public class LikelihoodMap {
    /**
     * likelihood values as a 1d array in the xyz convention
     */
    final double[] likelihoods;
    /**
     * width of the map
     */
    final int width;
    /**
     * height of the map
     */
    final int height;
    /**
     * depth of the map
     */
    final int depth;
    /**
     * number of pixels for a plan of the map
     */
    final int widthXheight;

    /**
     * offset of the map as compared to the original image in the x direction
     */
    final int offsetX;
    /**
     * offset of the map as compared to the original image in the y direction
     */
    final int offsetY;
    /**
     * offset of the map as compared to the original image in the z direction
     */
    final int offsetZ;

    /**
     * Build the likelihood map based on the specified likelihood values in a 1d array with xyz convention
     *
     * @param width       int
     * @param height      int
     * @param depth       int
     * @param offsetX     int
     * @param offsetY     int
     * @param offsetZ     int
     * @param likelihoods array of double
     */
    @Contract(pure = true)
    public LikelihoodMap(final int width, final int height, final int depth, final int offsetX, final int offsetY, final int offsetZ, final double[] likelihoods) {
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.offsetZ = offsetZ;
        this.likelihoods = likelihoods;
        this.widthXheight = height * width;
    }

    /**
     * Build an empty 3D likelihood map with given spatial offsets to the original images
     *
     * @param width   int
     * @param height  int
     * @param depth   int
     * @param offsetX int
     * @param offsetY int
     * @param offsetZ int
     */
    @Contract(pure = true)
    public LikelihoodMap(final int width, final int height, final int depth, final int offsetX, final int offsetY, final int offsetZ) {
        this(width, height, depth, offsetX, offsetY, offsetZ, new double[width * height * depth]);
    }

    /**
     * Build an empty 3D likelihood map
     *
     * @param width  int
     * @param height int
     * @param depth  int
     */
    @Contract(pure = true)
    public LikelihoodMap(final int width, final int height, final int depth) {
        this(width, height, depth, 0, 0, 0);
    }

    /**
     * Build an empty 2D likelihood map
     *
     * @param width  int
     * @param height int
     */
    @Contract(pure = true)
    public LikelihoodMap(final int width, final int height) {
        this(width, height, 1, 0, 0, 0);
    }

    /**
     * Build an empty 2D likelihood map with given spatial offsets to the original images
     *
     * @param width   int
     * @param height  int
     * @param offsetX int
     * @param offsetY int
     */
    @Contract(pure = true)
    public LikelihoodMap(final int width, final int height, final int offsetX, final int offsetY) {
        this(width, height, 1, offsetX, offsetY, 0);
    }

    /**
     * Get a copy of the likelihood map
     *
     * @return LikelihoodMap
     */
    public LikelihoodMap copy() {
        final double[] dataCopy = new double[likelihoods.length];
        System.arraycopy(likelihoods, 0, dataCopy, 0, dataCopy.length);
        return new LikelihoodMap(width, height, depth, offsetX, offsetY, offsetZ, dataCopy);
    }

    /**
     * Get the likelihood at a specified 2D location
     *
     * @param x int
     * @param y int
     * @param z int
     * @return double
     */
    public double getLikelihood(final int x, final int y, final int z) {
        return likelihoods[(x - offsetX) + (y - offsetY) * width + (z - offsetZ) * widthXheight];
    }

    /**
     * Get the likelihood at a specified 3D location
     *
     * @param i int
     * @param j int
     * @param k int
     * @return double
     */
    public double getLikelihoodLocalCoord(final int i, final int j, final int k) {
        return likelihoods[i + j * width + k * widthXheight];
    }

    /**
     * Set a likelihood value at the specified location in the original coordinate system
     *
     * @param x     int
     * @param y     int
     * @param z     int
     * @param value double
     */
    public void setLikelihood(final int x, final int y, final int z, final double value) {
        likelihoods[(x - offsetX) + (y - offsetY) * width + (z - offsetZ) * widthXheight] = value;
    }

    /**
     * Set a likelihood value at the specified 2D location in the local coordinate system
     *
     * @param i     int
     * @param j     int
     * @param k     int
     * @param value double
     */
    public void setLikelihoodLocalCoord(final int i, final int j, final int k, final double value) {
        likelihoods[i + j * width + k * widthXheight] = value;
    }

    /**
     * Add a value to the likelihood value at the specified 2D location in the original coordinate system
     *
     * @param x     int
     * @param y     int
     * @param z     int
     * @param value double
     */
    public void addLikelihood(final int x, final int y, final int z, final double value) {
        final int computation = (x - offsetX) + (y - offsetY) * width + (z - offsetZ) * widthXheight;
        likelihoods[computation] = value + likelihoods[computation];
    }

    /**
     * Add a value to the likelihood value at the specified 2D location in the local coordinate system
     *
     * @param i     int
     * @param j     int
     * @param k     int
     * @param value double
     */
    public void addLikelihoodLocalCoord(final int i, final int j, final int k, final double value) {
        final int computation = i + j * width + k * widthXheight;
        likelihoods[computation] = value + likelihoods[computation];
    }

    /**
     * Get the likelihood value at the given 2D location in the original coordinate system
     *
     * @param x int
     * @param y int
     * @return double
     */
    public double getLikelihood(final int x, final int y) {
        return likelihoods[(x - offsetX) + (y - offsetY) * width];
    }

    /**
     * Get the likelihood value at the given 2D location in the local coordinate system
     *
     * @param i int
     * @param j int
     * @return double
     */
    public double getLikelihoodLocalCoord(final int i, final int j) {
        return likelihoods[i + j * width];
    }

    /**
     * Set the likelihood value at the given 2D location in the global coordinate system
     *
     * @param x     int
     * @param y     int
     * @param value double
     */
    public void setLikelihood(final int x, final int y, final double value) {
        likelihoods[(x - offsetX) + (y - offsetY) * width] = value;
    }

    /**
     * Set the likelihood value at the given 2D location in the local coordinate system
     *
     * @param i     int
     * @param j     int
     * @param value double
     */
    public void setLikelihoodLocalCoord(final int i, final int j, final double value) {
        likelihoods[i + j * width] = value;
    }

    /**
     * Add a value to the likelihood value at the specified 2D location in the global coordinate system
     *
     * @param x     int
     * @param y     int
     * @param value double
     */
    public void addLikelihood(final int x, final int y, final double value) {
        final int computation = (x - offsetX) + (y - offsetY) * width;
        likelihoods[computation] = value + likelihoods[computation];
    }

    /**
     * Add a value to the likelihood value at the specified 2D location in the local coordinate system
     *
     * @param i     int
     * @param j     int
     * @param value double
     */
    public void addLikelihoodLocalCoord(final int i, final int j, final double value) {
        likelihoods[i + j * width] = value + likelihoods[i + j * width];
    }

    /**
     * Get the depth of the likelihood map
     * {@link LikelihoodMap#depth}
     *
     * @return int
     */
    public int getDepth() {
        return depth;
    }

    /**
     * Get the height of the likelihood map
     * {@link LikelihoodMap#height}
     *
     * @return int
     */
    public int getHeight() {
        return height;
    }

    /**
     * Get the width of the likelihood map
     * {@link LikelihoodMap#width}
     *
     * @return int
     */
    public int getWidth() {
        return width;
    }

    /**
     * Get the offset in the X direction of the likelihood map to the original image
     * {@link LikelihoodMap#offsetX}
     *
     * @return int
     */
    public int getOffsetX() {
        return offsetX;
    }

    /**
     * Get the offset in the Y direction of the likelihood map to the original image
     * {@link LikelihoodMap#offsetY}
     *
     * @return int
     */
    public int getOffsetY() {
        return offsetY;
    }

    /**
     * Get the offset in the Z direction of the likelihood map to the original image
     * {@link LikelihoodMap#offsetZ}
     *
     * @return int
     */
    public int getOffsetZ() {
        return offsetZ;
    }
}
