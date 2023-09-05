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

package plugins.nchenouard.particletracking;

import plugins.nchenouard.spot.Detection;
import plugins.nchenouard.spot.Spot;

/**
 * A specialized Detection object that embeds a DetectionSpot object
 * <p>
 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
 *
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com)
 * @version 3.1
 * date 2013-11-13
 * license gpl v3.0
 */

public class DetectionSpotTrack extends Detection {
    public Spot spot;

    public DetectionSpotTrack(final Spot detection, final int t) {
        super(detection.mass_center.x, detection.mass_center.y, detection.mass_center.z, t);
        this.spot = detection;
    }

    public DetectionSpotTrack() {
        super();
    }

    public void setDetection(final Spot detection, final int t) {
        this.spot = detection;
        this.t = t;
    }
}
