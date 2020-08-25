package plugins.nchenouard.particletracking.filtering;

/**
 * A map of the likelihood for target position in space
 * 
 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
 * 
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com)
 * @version 3.1
 * @date 2013-11-13
 * @license gpl v3.0
*/

public class LikelihoodMap
{
	/** likelihood values as a 1d array in the xyz convention*/
	final double[]  likelihoods;
	/** width of the map*/
	final int width;
	/** height of the map*/
	final int  height;
	/** depth of the map*/
	final int depth;
	/** number of pixels for a plan of the map*/
	final int widthXheight;
	
	/** offset of the map as compared to the original image in the x direction*/
	final int offsetX;
	/** offset of the map as compared to the original image in the y direction*/
	final int offsetY;
	/** offset of the map as compared to the original image in the z direction*/
	final int offsetZ;
	
	/** build the likelihood map based on the specified likelihood values in a 1d array with xyz convention*/
	public LikelihoodMap(int width, int height, int depth, int offsetX, int offsetY, int offsetZ, double[] likelihoods)
	{
		this.width = width;
		this.height = height;
		this.depth = depth;
		this.offsetX = offsetX;
		this.offsetY = offsetY;
		this.offsetZ = offsetZ;
		this.likelihoods = likelihoods;
		this.widthXheight = height*width;
	}

	/** build an empty 3D likelihood map with given spatial offsets to the original images*/
	public LikelihoodMap(int width, int height, int depth, int offsetX, int offsetY, int offsetZ)
	{
		this(width, height, depth, offsetX, offsetY, offsetZ, new double[width*height*depth]);
	}
	
	/** build an empty 3D likelihood map*/
	public LikelihoodMap(int width, int height, int depth)
	{
		this(width, height, depth, 0, 0, 0);
	}
	
	/** build an empty 2D likelihood map*/	
	public LikelihoodMap(int width, int height)
	{
		this(width, height, 1, 0, 0, 0);
	}

	/** build an empty 2D likelihood map with given spatial offsets to the original images*/
	public LikelihoodMap(int width, int height, int offsetX, int offsetY)
	{
		this(width, height, 1, offsetX, offsetY, 0);
	}
	
	/** get a copy of the likelihood map*/
	public LikelihoodMap copy()
	{
		double[] dataCopy = new double[likelihoods.length];
		System.arraycopy(likelihoods, 0, dataCopy, 0, dataCopy.length);
		return new LikelihoodMap(width, height, depth, offsetX, offsetY, offsetZ, dataCopy);
	}
	
	/** get the likelihood at a specified 2D location*/
	public double getLikelihood(int x, int y, int z)
	{
		return likelihoods[(x-offsetX)+(y-offsetY)*width+(z-offsetZ)*widthXheight];
	}

	/** get the likelihood at a specified 3D location*/
	public double getLikelihoodLocalCoord(int i, int j, int k)
	{
		return likelihoods[i+j*width+k*widthXheight];
	}
	
	/** set a likelihood value at the specified location in the original coordinate system*/
	public void setLikelihood(int x, int y, int z, double value)
	{
		likelihoods[(x-offsetX)+(y-offsetY)*width+(z-offsetZ)*widthXheight] = value;
	}

	/** set a likelihood value at the specified 2D location in the local coordinate system*/
	public void setLikelihoodLocalCoord(int i, int j, int k, double value)
	{
		likelihoods[i+j*width+k*widthXheight] = value;
	}
	
	/** add a value to the likelihood value at the specified 2D location in the original coordinate system*/
	public void addLikelihood(int x, int y, int z, double value)
	{
		likelihoods[(x-offsetX)+(y-offsetY)*width+(z-offsetZ)*widthXheight] = value + likelihoods[(x-offsetX)+(y-offsetY)*width+(z-offsetZ)*widthXheight];
	}
	
	/** add a value to the likelihood value at the specified 2D location in the local coordinate system*/	
	public void addLikelihoodLocalCoord(int i, int j, int k, double value)
	{
		likelihoods[i+j*width+k*widthXheight] = value + likelihoods[i+j*width+k*widthXheight];
	}
	
	/** get the likelihood value at the given 2D location in the original coordinate system*/
	public double getLikelihood(int x, int y)
	{
		return likelihoods[(x-offsetX)+(y-offsetY)*width];
	}
	
	/** get the likelihood value at the given 2D location in the local coordinate system*/
	public double getLikelihoodLocalCoord(int i, int j)
	{
		return likelihoods[i+j*width];
	}

	/** set the likelihood value at the given 2D location in the global coordinate system*/
	public void setLikelihood(int x, int y, double value)
	{
		likelihoods[(x-offsetX)+(y-offsetY)*width] = value;
	}
	
	/** set the likelihood value at the given 2D location in the local coordinate system*/
	public void setLikelihoodLocalCoord(int i, int j, double value)
	{
		likelihoods[i+j*width] = value;
	}
	
	/** add a value to the likelihood value at the specified 2D location in the global coordinate system*/
	public void addLikelihood(int x, int y, double value)
	{
		likelihoods[(x-offsetX)+(y-offsetY)*width] = value + likelihoods[(x-offsetX)+(y-offsetY)*width];
	}
	
	/** add a value to the likelihood value at the specified 2D location in the local coordinate system*/
	public void addLikelihoodLocalCoord(int i, int j, double value)
	{
		likelihoods[i+j*width] = value + likelihoods[i+j*width];
	}

	/** get the depth of the likelihood map*/
	public int getDepth() {
		return depth;
	}

	/** get the height of the likelihood map*/
	public int getHeight() {
		return height;
	}
	
	/** get the width of the likelihood map*/
	public int getWidth() {
		return width;
	}

	/** get the offset in the X direction of the likelihood map to the original image*/
	public int getOffsetX() {
		return offsetX;
	}

	/** get the offset in the Y direction of the likelihood map to the original image*/
	public int getOffsetY() {
		return offsetY;
	}

	/** get the offset in the Z direction of the likelihood map to the original image*/
	public int getOffsetZ() {
		return offsetZ;
	}
}
