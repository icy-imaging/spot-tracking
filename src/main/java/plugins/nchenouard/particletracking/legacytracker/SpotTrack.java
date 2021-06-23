package plugins.nchenouard.particletracking.legacytracker;

import java.awt.Color;
import java.util.ArrayList;

import plugins.nchenouard.spot.Detection;
import plugins.nchenouard.spot.Point3D;
import plugins.nchenouard.spot.Spot;

/**
 * A Detection object that contains extra information about the detection that comes from the tracking process
 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
 * 
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com)
 * @version 3.1
 *          date 2013-11-13
 *          license gpl v3.0
 */

public class SpotTrack extends Detection
{
    public double minIntensity;
    public double maxIntensity;
    public double meanIntensity;

    /** List of point of the detection */
    ArrayList<Point3D> point3DList = new ArrayList<Point3D>();

    public SpotTrack()
    {
        super();

        point3DList = new ArrayList<Point3D>();
        setColor(Color.red);
    }

    public SpotTrack(Spot spot, int t, boolean real, double minIntensity, double maxIntensity, double meanIntensity,
            ArrayList<Point3D> point3DList)
    {
        super(spot.mass_center.x, spot.mass_center.y, spot.mass_center.z, t);

        if (real)
        {
            this.detectionType = DETECTIONTYPE_REAL_DETECTION;
        }
        else
        {
            this.detectionType = DETECTIONTYPE_VIRTUAL_DETECTION;
        }

        setColor(Color.red);

        this.minIntensity = minIntensity;
        this.maxIntensity = maxIntensity;
        this.meanIntensity = meanIntensity;

        if (point3DList == null)
        {
            // use the one from the spot
            if (spot.point3DList != null)
                this.point3DList = new ArrayList<Point3D>(spot.point3DList);
            else
                this.point3DList = new ArrayList<Point3D>();
        }
        else
        {
            this.point3DList = new ArrayList<Point3D>(point3DList);
        }
    }

    public ArrayList<Point3D> getPoint3DList()
    {
        return point3DList;
    }

    public void setPoint3DList(ArrayList<Point3D> point3dList)
    {
        point3DList = point3dList;
    }
}
