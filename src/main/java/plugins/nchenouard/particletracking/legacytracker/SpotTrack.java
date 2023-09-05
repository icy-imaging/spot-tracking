/*
 * Copyright (c) 2010-2023. Institut Pasteur.
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

package plugins.nchenouard.particletracking.legacytracker;

import plugins.nchenouard.spot.Detection;
import plugins.nchenouard.spot.Point3D;
import plugins.nchenouard.spot.Spot;

import java.awt.*;
import java.util.ArrayList;

/**
 * A Detection object that contains extra information about the detection that comes from the tracking process
 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
 *
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com)
 * @version 3.1
 * date 2013-11-13
 * license gpl v3.0
 */

public class SpotTrack extends Detection {
    public double minIntensity;
    public double maxIntensity;
    public double meanIntensity;

    /**
     * List of point of the detection
     */
    ArrayList<Point3D> point3DList;

    public SpotTrack() {
        super();

        point3DList = new ArrayList<>();
        setColor(Color.red);
    }

    public SpotTrack(final Spot spot, final int t, final boolean real, final double minIntensity, final double maxIntensity, final double meanIntensity, final ArrayList<Point3D> point3DList) {
        super(spot.mass_center.x, spot.mass_center.y, spot.mass_center.z, t);

        if (real) {
            this.detectionType = DETECTIONTYPE_REAL_DETECTION;
        }
        else {
            this.detectionType = DETECTIONTYPE_VIRTUAL_DETECTION;
        }

        setColor(Color.red);

        this.minIntensity = minIntensity;
        this.maxIntensity = maxIntensity;
        this.meanIntensity = meanIntensity;

        if (point3DList == null) {
            // use the one from the spot
            if (spot.point3DList != null)
                this.point3DList = new ArrayList<>(spot.point3DList);
            else
                this.point3DList = new ArrayList<>();
        }
        else {
            this.point3DList = new ArrayList<>(point3DList);
        }
    }

    public ArrayList<Point3D> getPoint3DList() {
        return point3DList;
    }

    public void setPoint3DList(final ArrayList<Point3D> point3dList) {
        point3DList = point3dList;
    }
}
